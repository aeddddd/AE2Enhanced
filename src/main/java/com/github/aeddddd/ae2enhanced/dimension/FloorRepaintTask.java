package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 可恢复的地板重铺游标；只保存中性数据，不保存 World 或 Chunk 引用。 */
public final class FloorRepaintTask {
    private final Set<Long> pendingChunks = new LinkedHashSet<>();
    private String taskId = UUID.randomUUID().toString();
    private long revision;
    private Status status = Status.SCANNING;
    private int centerChunkX;
    private int centerChunkZ;
    private int radiusChunks;
    private int nextRegionX;
    private int nextRegionZ;
    private long scanToken;

    public static FloorRepaintTask create(long revision, int centerX, int centerZ, int radius) {
        FloorRepaintTask task = new FloorRepaintTask();
        task.revision = revision;
        task.centerChunkX = centerX;
        task.centerChunkZ = centerZ;
        task.radiusChunks = Math.max(0, radius);
        task.nextRegionX = floorRegion(centerX - task.radiusChunks);
        task.nextRegionZ = floorRegion(centerZ - task.radiusChunks);
        return task;
    }

    public static FloorRepaintTask read(NBTTagCompound tag) {
        FloorRepaintTask task = new FloorRepaintTask();
        if (tag.hasKey("taskId", 8)) {
            task.taskId = tag.getString("taskId");
        }
        task.revision = tag.getLong("revision");
        try {
            task.status = Status.valueOf(tag.getString("status").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            task.status = Status.CANCELLED;
        }
        task.centerChunkX = tag.getInteger("centerChunkX");
        task.centerChunkZ = tag.getInteger("centerChunkZ");
        task.radiusChunks = Math.max(0, tag.getInteger("radiusChunks"));
        task.nextRegionX = tag.getInteger("nextRegionX");
        task.nextRegionZ = tag.getInteger("nextRegionZ");
        task.scanToken = tag.getLong("scanToken");
        NBTTagList pending = tag.getTagList("pending", 10);
        for (int i = 0; i < pending.tagCount(); i++) {
            task.pendingChunks.add(pending.getCompoundTagAt(i).getLong("chunk"));
        }
        return task;
    }

    public static long chunkKey(int x, int z) {
        return ((long) x << 32) | z & 0xffffffffL;
    }

    public static int chunkX(long key) {
        return (int) (key >> 32);
    }

    public static int chunkZ(long key) {
        return (int) key;
    }

    private static int floorRegion(int chunk) {
        return Math.floorDiv(chunk, 32);
    }

    public String getTaskId() {
        return taskId;
    }

    public long getRevision() {
        return revision;
    }

    public Status getStatus() {
        return status;
    }

    public int getCenterChunkX() {
        return centerChunkX;
    }

    public int getCenterChunkZ() {
        return centerChunkZ;
    }

    public int getRadiusChunks() {
        return radiusChunks;
    }

    public int getNextRegionX() {
        return nextRegionX;
    }

    public int getNextRegionZ() {
        return nextRegionZ;
    }

    public long getScanToken() {
        return scanToken;
    }

    public long nextScanToken() {
        return ++scanToken;
    }

    public List<Long> getPendingChunks() {
        return Collections.unmodifiableList(new ArrayList<>(pendingChunks));
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        return pendingChunks.contains(chunkKey(chunkX, chunkZ));
    }

    public boolean removeChunk(int chunkX, int chunkZ) {
        return pendingChunks.remove(chunkKey(chunkX, chunkZ));
    }

    public void addScannedChunks(Collection<Long> chunks) {
        pendingChunks.addAll(chunks);
    }

    public void advanceRegion() {
        int minX = floorRegion(centerChunkX - radiusChunks);
        int maxX = floorRegion(centerChunkX + radiusChunks);
        int minZ = floorRegion(centerChunkZ - radiusChunks);
        int maxZ = floorRegion(centerChunkZ + radiusChunks);
        if (nextRegionX < maxX) {
            nextRegionX++;
        } else if (nextRegionZ < maxZ) {
            nextRegionX = minX;
            nextRegionZ++;
        } else {
            nextRegionX = minX;
            nextRegionZ = maxZ + 1;
            status = Status.RUNNING;
        }
        if (status == Status.RUNNING && pendingChunks.isEmpty() && nextRegionX == minX && nextRegionZ > maxZ) {
            status = Status.COMPLETE;
        }
    }

    public void updateWaitingStatus() {
        if (status == Status.SCANNING || status == Status.COMPLETE || status == Status.CANCELLED) {
            return;
        }
        status = pendingChunks.isEmpty() ? Status.COMPLETE : Status.WAITING_FOR_LOAD;
    }

    public void cancel() {
        status = Status.CANCELLED;
        pendingChunks.clear();
        scanToken++;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("taskId", taskId);
        tag.setLong("revision", revision);
        tag.setString("status", status.name());
        tag.setInteger("centerChunkX", centerChunkX);
        tag.setInteger("centerChunkZ", centerChunkZ);
        tag.setInteger("radiusChunks", radiusChunks);
        tag.setInteger("nextRegionX", nextRegionX);
        tag.setInteger("nextRegionZ", nextRegionZ);
        tag.setLong("scanToken", scanToken);
        NBTTagList pending = new NBTTagList();
        for (long chunk : pendingChunks) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("chunk", chunk);
            pending.appendTag(entry);
        }
        tag.setTag("pending", pending);
        return tag;
    }

    public enum Status {
        SCANNING,
        RUNNING,
        WAITING_FOR_LOAD,
        COMPLETE,
        CANCELLED
    }
}
