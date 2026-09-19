package com.github.aeddddd.ae2enhanced.diag.check;

import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 超维度仓储中枢健康检查.
 *
 * <p>检查项：</p>
 * <ul>
 *   <li>已加载控制器：结构成型 / 安全模式 / 网络激活 / 网络供电</li>
 *   <li>磁盘存档：文件头完整性（Magic + 版本号），旧格式识别</li>
 *   <li>磁盘与内存对照：存档存在但控制器未加载 → INFO（区块未加载属正常）</li>
 * </ul>
 */
public final class StorageCheck implements SystemCheck {

    /** 与 HyperdimensionalStorageFile 文件头格式保持一致（Magic "AE2E" + version; v2 头部含 checkpointRev） */
    private static final int HEADER_MAGIC = 0x41453245; // "AE2E"
    private static final int HEADER_CURRENT_VERSION = 2;
    private static final int HEADER_BYTES = 16;

    private static final String KEY_PREFIX = "chat.ae2enhanced.check.storage.";

    @Override
    public String name() {
        return "storage";
    }

    @Override
    public String displayName() {
        return KEY_PREFIX + "name";
    }

    @Override
    public void run(MinecraftServer server, List<CheckResult> out) {
        try {
            Set<UUID> loadedNexusIds = checkLoadedControllers(server, out);
            checkDiskStorage(server, loadedNexusIds, out);
        } catch (Exception e) {
            out.add(CheckResult.error(KEY_PREFIX + "exception", String.valueOf(e)));
        }
    }

    private Set<UUID> checkLoadedControllers(MinecraftServer server, List<CheckResult> out) {
        Set<UUID> nexusIds = new HashSet<>();
        int total = 0;
        int formed = 0;
        for (WorldServer world : server.worlds) {
            // 与 CommandAE2Enhanced 现有遍历模式一致：扫 loadedTileEntityList
            for (TileEntity te : world.loadedTileEntityList) {
                if (!(te instanceof TileHyperdimensionalController)) {
                    continue;
                }
                TileHyperdimensionalController controller = (TileHyperdimensionalController) te;
                total++;
                UUID nexusId = controller.getNexusId();
                if (nexusId != null) {
                    nexusIds.add(nexusId);
                }
                String where = te.getPos() + " @dim" + world.provider.getDimension();
                if (!controller.isFormed()) {
                    out.add(CheckResult.error(KEY_PREFIX + "controller_unformed", where));
                    continue;
                }
                formed++;
                if (controller.isSafeMode()) {
                    out.add(CheckResult.error(KEY_PREFIX + "controller_safe_mode", where));
                }
                if (!controller.isNetworkActive()) {
                    out.add(CheckResult.warn(KEY_PREFIX + "controller_inactive", where));
                }
                if (!controller.isNetworkPowered()) {
                    out.add(CheckResult.warn(KEY_PREFIX + "controller_unpowered", where));
                }
            }
        }
        out.add(CheckResult.ok(KEY_PREFIX + "controllers_summary", total, formed));
        return nexusIds;
    }

    private void checkDiskStorage(MinecraftServer server, Set<UUID> loadedNexusIds, List<CheckResult> out) {
        WorldServer overworld = server.getWorld(0);
        if (overworld == null) {
            out.add(CheckResult.warn(KEY_PREFIX + "overworld_unavailable"));
            return;
        }
        File storageDir = new File(overworld.getSaveHandler().getWorldDirectory(), "ae2enhanced/storage");
        if (!storageDir.exists()) {
            out.add(CheckResult.ok(KEY_PREFIX + "no_save_dir"));
            return;
        }
        File[] entries = storageDir.listFiles((dir, name) -> {
            if (name.startsWith("smartpattern_")) return false;
            File f = new File(dir, name);
            return f.isDirectory() || name.endsWith(".dat");
        });
        if (entries == null || entries.length == 0) {
            out.add(CheckResult.ok(KEY_PREFIX + "empty_save_dir"));
            return;
        }
        int healthy = 0;
        int unloaded = 0;
        for (File entry : entries) {
            String id = entry.isDirectory()
                    ? entry.getName()
                    : entry.getName().substring(0, entry.getName().length() - 4);
            TextComponentTranslation headerError = validateHeader(entry);
            if (headerError != null) {
                out.add(CheckResult.error(KEY_PREFIX + "header_corrupt", id, headerError));
                continue;
            }
            healthy++;
            boolean loaded;
            try {
                loaded = loadedNexusIds.contains(UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                out.add(CheckResult.warn(KEY_PREFIX + "bad_uuid_name", id));
                continue;
            }
            if (!loaded) {
                unloaded++;
            }
        }
        out.add(CheckResult.ok(KEY_PREFIX + "saves_summary", entries.length, healthy, unloaded));
    }

    /** 校验分区文件头；合法返回 null，否则返回错误描述（本地化组件，作为参数嵌套渲染）。 */
    private static TextComponentTranslation validateHeader(File entry) {
        File target;
        if (entry.isDirectory()) {
            target = new File(entry, "items.bin");
            if (!target.exists()) {
                return null; // 目录存在但无 item 分区（可能仅存流体等）,不判错
            }
        } else {
            target = entry; // 旧格式 <uuid>.dat
        }
        if (target.length() < HEADER_BYTES) {
            return new TextComponentTranslation(KEY_PREFIX + "hdr_short", HEADER_BYTES);
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(target))) {
            int magic = in.readInt();
            if (magic != HEADER_MAGIC) {
                return new TextComponentTranslation(KEY_PREFIX + "hdr_magic",
                        "0x" + Integer.toHexString(magic));
            }
            int version = in.readInt();
            if (version < 0 || version > HEADER_CURRENT_VERSION) {
                return new TextComponentTranslation(KEY_PREFIX + "hdr_version", version);
            }
            return null;
        } catch (Exception e) {
            return new TextComponentTranslation(KEY_PREFIX + "hdr_read", String.valueOf(e));
        }
    }
}
