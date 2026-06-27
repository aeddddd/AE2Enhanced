package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
 *   version: 1 (int)
 *   patternDataId: UUID (long[])
 *   targetBlockId: String
 *   createdAt: long
 *   recipes: NBTTagList { ... }
 *   conflictMask: String (Base64)
 *   disabledMask: String (Base64)
 * }
 */
public class SmartPatternStorageFile {

    public static final int CURRENT_VERSION = 1;
    static final String FILE_PREFIX = "smartpattern_";

    /**
     * 加载指定 UUID 的智能样板数据.
     *
     * @return 若文件不存在或损坏,返回 null
     */
    @Nullable
    public static SmartPatternData load(@Nullable World world, @Nonnull UUID patternDataId) {
        if (world == null) return null;
        File file = getFile(world, patternDataId);
        if (!file.exists()) {
            return null;
        }
        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            if (root == null) {
                return null;
            }
            int version = root.getInteger("version");
            if (version > CURRENT_VERSION) {
                AE2Enhanced.LOGGER.error(
                    "[AE2E] SmartPattern file version {} > current {}. Refusing to load to prevent data corruption.",
                    version, CURRENT_VERSION);
                return null;
            }
            // 更新访问时间,用于垃圾回收判断
            try {
                file.setLastModified(System.currentTimeMillis());
            } catch (Exception ignored) {
            }
            return SmartPatternData.fromNBT(root);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error(
                "[AE2E] Failed to read SmartPattern file: {}.", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * 保存智能样板数据到文件.
     *
     * @return 是否保存成功
     */
    public static boolean save(@Nullable World world, @Nonnull SmartPatternData data) {
        if (world == null) return false;
        File file = getFile(world, data.getPatternDataId());
        NBTTagCompound root = data.toNBT();
        root.setInteger("version", CURRENT_VERSION);

        File tmpFile = new File(file.getAbsolutePath() + ".tmp");
        try {
            CompressedStreamTools.write(root, tmpFile);
            Files.move(tmpFile.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            AE2Enhanced.LOGGER.error(
                "[AE2E] Failed to save SmartPattern file: {}.", file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * 删除指定 UUID 的智能样板文件.
     */
    public static boolean delete(@Nullable World world, @Nonnull UUID patternDataId) {
        if (world == null) return false;
        File file = getFile(world, patternDataId);
        if (file.exists()) {
            return file.delete();
        }
        return true;
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
