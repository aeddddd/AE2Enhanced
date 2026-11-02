package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 智能样板配方数据的文件系统持久化层.
 * 复用超维度仓储中枢的 ae2enhanced/storage/ 目录结构.
 *
 * 文件格式(压缩 NBT)：
 * {
 *   version: 2 (int; v1 可读)
 *   revision: long (v1 缺失时为 0)
 *   patternDataId: UUID (long[])
 *   targetBlockId: String
 *   createdAt: long
 *   recipes: NBTTagList { ... }
 *   conflictMask: String (Base64)
 *   disabledMask: String (Base64)
 * }
 */
public class SmartPatternStorageFile {

    public static final int CURRENT_VERSION = 2;
    static final String FILE_PREFIX = "smartpattern_";

    /** v1 缺少 revision 时按 0 读取；主文件是唯一权威，.tmp 永不参与恢复。 */
    @Nullable
    public static SmartPatternData load(@Nullable World world, @Nonnull UUID patternDataId) {
        Stored stored = adapter(world).load(patternDataId);
        return stored == null ? null : stored.data();
    }

    /** 仅供显式管理操作使用；正常回收不得删除仍可能被副本引用的数据。 */
    public static boolean delete(@Nullable World world, @Nonnull UUID patternDataId) {
        if (world == null) return false;
        File file = getFile(world, patternDataId);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    @Nonnull
    public static Adapter adapter(@Nullable World world) {
        return new FileAdapter(getStorageDir(world).toPath());
    }

    public interface Adapter {
        @Nullable
        Stored load(@Nonnull UUID id);

        boolean save(@Nonnull UUID id, @Nonnull Stored stored);
    }

    public static final class Stored {
        private final SmartPatternData data;
        private final long revision;

        public Stored(@Nonnull SmartPatternData data, long revision) {
            this.data = data.copy();
            this.revision = revision;
        }

        @Nonnull
        public SmartPatternData data() {
            return data.copy();
        }

        public long revision() {
            return revision;
        }
    }

    private static final class FileAdapter implements Adapter {
        private final java.nio.file.Path directory;

        private FileAdapter(java.nio.file.Path directory) {
            this.directory = directory;
        }

        @Override
        @Nullable
        public Stored load(@Nonnull UUID id) {
            File file = directory.resolve(FILE_PREFIX + id + ".dat").toFile();
            if (!file.isFile()) return null;
            try {
                NBTTagCompound root = CompressedStreamTools.read(file);
                if (root == null) return null;
                int version = root.getInteger("version");
                if (version > CURRENT_VERSION) {
                    AE2Enhanced.LOGGER.error(
                            "[AE2E] SmartPattern file version {} > current {}. Refusing to load.",
                            version, CURRENT_VERSION);
                    return null;
                }
                SmartPatternData data = SmartPatternData.fromNBT(root);
                if (data == null || !id.equals(data.getPatternDataId())) return null;
                return new Stored(data, root.getLong("revision"));
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to read SmartPattern file: {}.",
                        file.getAbsolutePath(), e);
                return null;
            }
        }

        @Override
        public boolean save(@Nonnull UUID id, @Nonnull Stored stored) {
            File file = directory.resolve(FILE_PREFIX + id + ".dat").toFile();
            NBTTagCompound root = stored.data().toNBT();
            root.setInteger("version", CURRENT_VERSION);
            root.setLong("revision", stored.revision());
            File tmpFile = new File(file.getAbsolutePath() + ".tmp");
            try {
                Files.createDirectories(directory);
                CompressedStreamTools.write(root, tmpFile);
                Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (IOException e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to save SmartPattern file: {}.",
                        file.getAbsolutePath(), e);
                return false;
            }
        }
    }

    @Nonnull
    private static File getFile(@Nullable World world, @Nonnull UUID patternDataId) {
        File storageDir = getStorageDir(world);
        return new File(storageDir, FILE_PREFIX + patternDataId.toString() + ".dat");
    }

    @Nonnull
    static File getStorageDir(@Nullable World world) {
        if (world == null) {
            // Fallback: use the server overworld when world is not available (e.g. during TileEntity.readFromNBT)
            File fallback = getStorageDir();
            if (fallback != null) return fallback;
            throw new IllegalStateException("Cannot determine storage directory: world is null and server is not available");
        }
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File storageDir = new File(worldDir, "ae2enhanced/storage");
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            AE2Enhanced.LOGGER.warn("Failed to create storage directory: {}", storageDir.getAbsolutePath());
        }
        return storageDir;
    }

    @Nullable
    static File getStorageDir() {
        if (FMLCommonHandler.instance().getMinecraftServerInstance() == null) {
            return null;
        }
        WorldServer overworld = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        if (overworld == null) {
            return null;
        }
        return getStorageDir(overworld);
    }
}
