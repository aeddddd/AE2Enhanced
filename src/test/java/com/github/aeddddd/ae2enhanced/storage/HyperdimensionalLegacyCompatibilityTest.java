package com.github.aeddddd.ae2enhanced.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.CompatibilityFixtures;

/** 来自静态旧文件而非当前写法的 Hyperdimensional v1/v2 兼容验收。 */
class HyperdimensionalLegacyCompatibilityTest {

    private static final UUID ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");

    @TempDir
    Path tempDir;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static long revision(HyperdimensionalStorageFile file, String field) {
        try {
            Field sectionField = HyperdimensionalStorageFile.class.getDeclaredField("itemSection");
            sectionField.setAccessible(true);
            Object section = sectionField.get(file);
            Field value = section.getClass().getDeclaredField(field);
            value.setAccessible(true);
            return value.getLong(section);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static long countOf(ItemStorageAdapter adapter, int meta) {
        IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IAEItemStack probe = channel.createStack(new ItemStack(Items.DYE, 1, meta));
        probe.setStackSize(Long.MAX_VALUE);
        IAEItemStack result = adapter.extractItems(probe, Actionable.SIMULATE, null);
        return result == null ? 0L : result.getStackSize();
    }

    @Test
    void staticV1BaseLoadsWithoutBeingGeneratedByCurrentWriter() {
        copyBase("hyperdimensional/items-v1.bin");
        LoadedStorage loaded = load();

        assertThat(countOf(loaded.adapter, 4)).isEqualTo(777L);
        assertThat(revision(loaded.file, "checkpointRev")).isZero();
        assertThat(loaded.file.isSectionFailed(StorageSection.ITEM)).isFalse();
        loaded.file.close();
    }

    @Test
    void staticV2BaseAndWalHonorCheckpointRevisionAndAbsoluteReplay() {
        copyBase("hyperdimensional/items-v2.bin");
        CompatibilityFixtures.copy("hyperdimensional/items-v2.wal", sectionDir().resolve("items.wal"));
        LoadedStorage loaded = load();

        assertThat(revision(loaded.file, "checkpointRev")).isEqualTo(7L);
        assertThat(revision(loaded.file, "mutationRev")).isEqualTo(9L);
        assertThat(countOf(loaded.adapter, 4)).as("rev=7 的旧帧不得覆盖 base，rev=8 应用绝对值").isEqualTo(125L);
        assertThat(countOf(loaded.adapter, 1)).as("rev=9 的零值回放为删除").isZero();
        assertThat(countOf(loaded.adapter, 7)).isEqualTo(42L);
        assertThat(loaded.file.isSectionFailed(StorageSection.ITEM)).isFalse();
        loaded.file.close();
    }

    private Path sectionDir() {
        return tempDir.resolve("ae2enhanced/storage").resolve(ID.toString());
    }

    private void copyBase(String resource) {
        CompatibilityFixtures.copy(resource, sectionDir().resolve("items.bin"));
    }

    private LoadedStorage load() {
        HyperdimensionalStorageFile file = new HyperdimensionalStorageFile(tempDir.toFile(), ID);
        ItemStorageAdapter adapter = new ItemStorageAdapter(file);
        file.setStorageRef(adapter.getStorageMap());
        file.beginLoad();
        file.awaitLoaded();
        return new LoadedStorage(file, adapter);
    }

    private static final class LoadedStorage {
        final HyperdimensionalStorageFile file;
        final ItemStorageAdapter adapter;

        LoadedStorage(HyperdimensionalStorageFile file, ItemStorageAdapter adapter) {
            this.file = file;
            this.adapter = adapter;
        }
    }
}
