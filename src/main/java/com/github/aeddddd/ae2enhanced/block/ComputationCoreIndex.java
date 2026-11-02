package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 超因果计算核心位置索引.
 * 用于追踪世界中所有已放置的计算核心控制器位置.
 */
public class ComputationCoreIndex extends WorldSavedData {

    private static final String DATA_NAME = AE2Enhanced.MOD_ID + "_computation_core_index";
    private final Set<BlockPos> cores = new HashSet<>();
    private final java.util.Map<Long, Set<BlockPos>> coresByChunk = new java.util.HashMap<>();

    public ComputationCoreIndex() {
        super(DATA_NAME);
    }

    public ComputationCoreIndex(String name) {
        super(name);
    }

    public static ComputationCoreIndex get(World world) {
        if (world.isRemote) return null;
        MapStorage storage = world.getPerWorldStorage();
        ComputationCoreIndex index = (ComputationCoreIndex) storage.getOrLoadData(ComputationCoreIndex.class, DATA_NAME);
        if (index == null) {
            index = new ComputationCoreIndex();
            storage.setData(DATA_NAME, index);
        }
        return index;
    }

    private static long chunkKey(int x, int z) {
        return (long) x & 0xffffffffL | ((long) z & 0xffffffffL) << 32;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        cores.clear();
        coresByChunk.clear();
        NBTTagList list = nbt.getTagList("cores", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound posTag = list.getCompoundTagAt(i);
            addToView(new BlockPos(posTag.getInteger("x"), posTag.getInteger("y"), posTag.getInteger("z")));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (BlockPos pos : cores) {
            NBTTagCompound posTag = new NBTTagCompound();
            posTag.setInteger("x", pos.getX());
            posTag.setInteger("y", pos.getY());
            posTag.setInteger("z", pos.getZ());
            list.appendTag(posTag);
        }
        compound.setTag("cores", list);
        return compound;
    }

    public void add(BlockPos pos) {
        if (cores.add(pos)) {
            addToView(pos);
            markDirty();
        }
    }

    private void addToView(BlockPos pos) {
        cores.add(pos);
        coresByChunk.computeIfAbsent(chunkKey(pos.getX() >> 4, pos.getZ() >> 4),
                key -> new HashSet<>()).add(pos);
    }

    public void remove(BlockPos pos) {
        if (cores.remove(pos)) {
            Set<BlockPos> bucket = coresByChunk.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
            if (bucket != null) {
                bucket.remove(pos);
                if (bucket.isEmpty()) coresByChunk.remove(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
            }
            markDirty();
        }
    }

    public Set<BlockPos> getInChunk(World world, ChunkPos chunk, Block controllerBlock) {
        Set<BlockPos> bucket = coresByChunk.get(chunkKey(chunk.x, chunk.z));
        if (bucket == null || bucket.isEmpty()) return Collections.emptySet();
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : new HashSet<>(bucket)) {
            if (!world.isBlockLoaded(pos)) continue;
            if (world.getBlockState(pos).getBlock() == controllerBlock) {
                result.add(pos);
            } else {
                remove(pos);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<BlockPos> getAll() {
        return Collections.unmodifiableSet(cores);
    }
}
