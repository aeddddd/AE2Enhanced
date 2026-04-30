package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 超维度仓储中枢的外部文件持久化层。
 * 每个结构对应一个独立文件，数据不写入 NBT/WorldSavedData。
 *
 * 文件格式（压缩 NBT）：
 * {
 *   version: 1 (int)
 *   nexusId: UUID (long[])
 *   items: NBTTagList {
 *     { id: "modid:item", Damage: 0s, Count: "12345678901234567890", tag?: NBTTagCompound }
 *   }
 * }
 */
public class HyperdimensionalStorageFile {

    public static final int CURRENT_VERSION = 1;

    private static final ScheduledExecutorService FLUSH_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AE2E-Storage-Flush");
            t.setDaemon(true);
            return t;
        });

    private final File file;
    private final UUID nexusId;
    private final ScheduledFuture<?> flushTask;
    private volatile boolean dirty = false;
    private volatile boolean closed = false;
    private Map<ItemDescriptor, BigInteger> storageRef = null;

    public HyperdimensionalStorageFile(World world, UUID nexusId) {
        this.nexusId = nexusId;
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File storageDir = new File(worldDir, "ae2enhanced/storage");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        this.file = new File(storageDir, nexusId.toString() + ".dat");
        this.flushTask = FLUSH_EXECUTOR.scheduleWithFixedDelay(this::flush, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * 从文件加载数据到目标 Map。若文件不存在则不做任何事（空存储）。
     */
    public void load(Map<ItemDescriptor, BigInteger> target) {
        if (!file.exists()) return;
        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            if (root == null) return;
            int version = root.getInteger("version");
            if (version > CURRENT_VERSION) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error(
                    "[AE2E] Storage file version {} > current {}. " +
                    "This file was created by a newer version of AE2Enhanced. " +
                    "Refusing to load to prevent data corruption.", version, CURRENT_VERSION);
                return;
            }
            NBTTagList items = root.getTagList("items", 10);
            for (int i = 0; i < items.tagCount(); i++) {
                NBTTagCompound tag = items.getCompoundTagAt(i);
                ItemDescriptor descriptor = ItemDescriptor.fromNBT(tag);
                if (descriptor == null) continue;
                String countStr = tag.getString("Count");
                BigInteger count;
                try {
                    count = new BigInteger(countStr);
                } catch (NumberFormatException e) {
                    continue;
                }
                target.put(descriptor, count);
            }
        } catch (IOException e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error(
                "[AE2E] Failed to load storage file: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 将当前内存数据保存到文件。
     * @return true 表示保存成功，false 表示失败
     */
    public boolean save(Map<ItemDescriptor, BigInteger> source) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("version", CURRENT_VERSION);
        root.setUniqueId("nexusId", nexusId);
        NBTTagList items = new NBTTagList();
        for (Map.Entry<ItemDescriptor, BigInteger> entry : source.entrySet()) {
            NBTTagCompound tag = entry.getKey().toNBT();
            tag.setString("Count", entry.getValue().toString());
            items.appendTag(tag);
        }
        root.setTag("items", items);

        File tmpFile = new File(file.getAbsolutePath() + ".tmp");
        try {
            CompressedStreamTools.write(root, tmpFile);
            if (file.exists() && !file.delete()) {
                throw new IOException("Failed to delete old storage file");
            }
            Files.move(tmpFile.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error(
                "[AE2E] Failed to save storage file: {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void setStorageRef(Map<ItemDescriptor, BigInteger> ref) {
        this.storageRef = ref;
    }

    private void flush() {
        if (!dirty || closed) return;
        if (storageRef != null && save(storageRef)) {
            dirty = false;
        }
        // save 失败时 dirty 保持 true，下次继续尝试
    }

    /**
     * 关闭文件句柄，强制刷盘一次。
     */
    public void close(Map<ItemDescriptor, BigInteger> finalSnapshot) {
        if (closed) return;
        closed = true;
        if (flushTask != null) {
            flushTask.cancel(false);
        }
        if (finalSnapshot != null) {
            save(finalSnapshot);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public UUID getNexusId() {
        return nexusId;
    }
}
