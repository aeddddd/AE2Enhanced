package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspaceEditor;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.container.OmniTerminalLayoutNbt;

/**
 * 阶段 4a：物品 NBT（权威）+ 本地文件（兜底）的复合持久化。
 *
 * <p>覆盖三条关键语义：读优先物品 NBT、NBT 没有布局时回落文件、NBT 布局损坏时<strong>标记损坏
 * 且不覆盖</strong>（由 {@code DockManager} 据此进入"禁止回写"状态）。</p>
 */
class TerminalNbtDockLayoutStoreTest {

    private static Path tempDir;

    @BeforeAll
    static void boot() throws IOException {
        net.minecraft.init.Bootstrap.register();
        tempDir = Files.createTempDirectory("ae2e-layout-store");
    }

    @AfterAll
    static void cleanup() {
        // 手工清理：不用 JUnit 的 @TempDir，避免 Windows 上删除被占用文件导致的假失败
        try {
            if (tempDir != null) {
                Files.walk(tempDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    /** 记录上传内容的假通道。 */
    private static final class RecordingUploader implements TerminalNbtDockLayoutStore.LayoutUploader {
        private final List<String> uploads = new ArrayList<String>();

        @Override
        public void upload(String layoutJson) {
            uploads.add(layoutJson);
        }
    }

    private static Path backupPath(String name) {
        return tempDir.resolve(name + "/layout.json");
    }

    private static DockRect rootBounds(DockWorkspace workspace, String rootId) {
        for (FloatingRoot root : workspace.roots()) {
            if (root.rootId().equals(rootId)) {
                return root.bounds();
            }
        }
        return null;
    }

    private static DockWorkspace shiftedWorkspace(DockLayoutCodec codec) {
        DockWorkspace base = WorkspacePersistenceFixtures.workspace();
        return new DockWorkspaceEditor(WorkspacePersistenceFixtures.catalog())
                .setRootBounds(base, "root-a", new DockRect(300, 300, 100, 80));
    }

    private static String nbtJson(DockLayoutCodec codec, boolean shifted) {
        return codec.encode(shifted ? shiftedWorkspace(codec) : WorkspacePersistenceFixtures.workspace());
    }

    @Test
    void saveUploadsTheLayoutAndWritesTheBackupFile() throws IOException {
        DockLayoutCodec codec = new DockLayoutCodec(WorkspacePersistenceFixtures.catalog(),
                WorkspacePersistenceFixtures.migrationContext());
        RecordingUploader uploader = new RecordingUploader();
        Path path = backupPath("save");
        TerminalNbtDockLayoutStore store = new TerminalNbtDockLayoutStore(codec,
                () -> new ItemStack(Items.STICK), uploader,
                new AtomicFileDockLayoutStore(path, codec));

        store.save(WorkspacePersistenceFixtures.workspace());

        assertEquals(1, uploader.uploads.size(), "save 必须把布局发出去");
        assertTrue(Files.isRegularFile(path), "save 也必须落一份本地兜底");
        assertEquals(WorkspacePersistenceFixtures.workspace().roots().size(),
                codec.decode(uploader.uploads.get(0)).workspace().roots().size());
        assertEquals(codec.encode(WorkspacePersistenceFixtures.workspace()),
                new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8),
                "文件内容与上传内容同源");
    }

    @Test
    void saveWithoutBackupStillUploads() throws IOException {
        DockLayoutCodec codec = new DockLayoutCodec(WorkspacePersistenceFixtures.catalog(),
                WorkspacePersistenceFixtures.migrationContext());
        RecordingUploader uploader = new RecordingUploader();
        TerminalNbtDockLayoutStore store = new TerminalNbtDockLayoutStore(codec,
                () -> new ItemStack(Items.STICK), uploader, null);

        store.save(WorkspacePersistenceFixtures.workspace());

        assertEquals(1, uploader.uploads.size());
    }

    @Test
    void loadPrefersTheTerminalItemNbt() throws IOException {
        DockLayoutCodec codec = new DockLayoutCodec(WorkspacePersistenceFixtures.catalog(),
                WorkspacePersistenceFixtures.migrationContext());
        ItemStack stack = new ItemStack(Items.STICK);
        assertTrue(OmniTerminalLayoutNbt.write(stack, nbtJson(codec, false)));

        Path path = backupPath("prefer-nbt");
        AtomicFileDockLayoutStore backup = new AtomicFileDockLayoutStore(path, codec);
        backup.saveEncoded(nbtJson(codec, true));

        TerminalNbtDockLayoutStore store = new TerminalNbtDockLayoutStore(codec, () -> stack,
                json -> { }, backup);

        Optional<DockLayoutCodec.DecodedLayout> loaded = store.load();
        assertTrue(loaded.isPresent());
        assertEquals(new DockRect(10, 20, 100, 80), rootBounds(loaded.get().workspace(), "root-a"),
                "物品 NBT 优先于本地文件");
        assertFalse(store.lastLoadWasCorrupt());
    }

    @Test
    void loadFallsBackToTheBackupFileWhenTheItemHasNoLayout() throws IOException {
        DockLayoutCodec codec = new DockLayoutCodec(WorkspacePersistenceFixtures.catalog(),
                WorkspacePersistenceFixtures.migrationContext());
        Path path = backupPath("fallback-file");
        AtomicFileDockLayoutStore backup = new AtomicFileDockLayoutStore(path, codec);
        backup.saveEncoded(nbtJson(codec, true));

        TerminalNbtDockLayoutStore store = new TerminalNbtDockLayoutStore(codec,
                () -> new ItemStack(Items.STICK), json -> { }, backup);

        Optional<DockLayoutCodec.DecodedLayout> loaded = store.load();
        assertTrue(loaded.isPresent(), "物品上没有布局时用本地兜底");
        assertEquals(new DockRect(300, 300, 100, 80), rootBounds(loaded.get().workspace(), "root-a"));
    }

    @Test
    void corruptItemLayoutIsFlaggedAndNeverOverwritten() throws IOException {
        DockLayoutCodec codec = new DockLayoutCodec(WorkspacePersistenceFixtures.catalog(),
                WorkspacePersistenceFixtures.migrationContext());
        ItemStack stack = new ItemStack(Items.STICK);
        String corrupt = "{ this is not a valid layout ";
        assertTrue(OmniTerminalLayoutNbt.write(stack, corrupt));

        TerminalNbtDockLayoutStore store = new TerminalNbtDockLayoutStore(codec, () -> stack,
                json -> { }, null);

        Optional<DockLayoutCodec.DecodedLayout> loaded = store.load();
        assertFalse(loaded.isPresent(), "损坏 → 退回默认布局");
        assertTrue(store.lastLoadWasCorrupt(), "必须标记损坏，DockManager 据此禁止回写");
        assertEquals(corrupt, OmniTerminalLayoutNbt.read(stack), "绝不就地覆盖原 NBT");
    }

    @Test
    void missingItemAndMissingFileYieldEmpty() throws IOException {
        DockLayoutCodec codec = new DockLayoutCodec(WorkspacePersistenceFixtures.catalog(),
                WorkspacePersistenceFixtures.migrationContext());
        TerminalNbtDockLayoutStore store = new TerminalNbtDockLayoutStore(codec,
                () -> ItemStack.EMPTY, json -> { }, null);

        assertFalse(store.load().isPresent());
        assertFalse(store.lastLoadWasCorrupt(), "新物品不算损坏");
        assertNotNull(store.lastQuarantinedPath());
        assertFalse(store.lastQuarantinedPath().isPresent());
    }
}
