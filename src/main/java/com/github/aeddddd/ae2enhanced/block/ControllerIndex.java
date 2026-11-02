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

public class ControllerIndex extends WorldSavedData {

    private static final String DATA_NAME = AE2Enhanced.MOD_ID + "_controller_index";
    private final Set<BlockPos> controllers = new HashSet<>();
    private final java.util.Map<Long, Set<BlockPos>> controllersByChunk = new java.util.HashMap<>();

    public ControllerIndex() {
        super(DATA_NAME);
    }

    public ControllerIndex(String name) {
        super(name);
    }

    public static ControllerIndex get(World world) {
        if (world.isRemote) return null;
        MapStorage storage = world.getPerWorldStorage();
        ControllerIndex index = (ControllerIndex) storage.getOrLoadData(ControllerIndex.class, DATA_NAME);
        if (index == null) {
            index = new ControllerIndex();
            storage.setData(DATA_NAME, index);
        }
        return index;
    }

    private static long chunkKey(int x, int z) {
        return (long) x & 0xffffffffL | ((long) z & 0xffffffffL) << 32;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        controllers.clear();
        controllersByChunk.clear();
        NBTTagList list = nbt.getTagList("controllers", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound posTag = list.getCompoundTagAt(i);
            addToView(new BlockPos(posTag.getInteger("x"), posTag.getInteger("y"), posTag.getInteger("z")));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (BlockPos pos : controllers) {
            NBTTagCompound posTag = new NBTTagCompound();
            posTag.setInteger("x", pos.getX());
            posTag.setInteger("y", pos.getY());
            posTag.setInteger("z", pos.getZ());
            list.appendTag(posTag);
        }
        compound.setTag("controllers", list);
        return compound;
    }

    public void add(BlockPos pos) {
        if (controllers.add(pos)) {
            addToView(pos);
            markDirty();
        }
    }

    private void addToView(BlockPos pos) {
        controllers.add(pos);
        controllersByChunk.computeIfAbsent(chunkKey(pos.getX() >> 4, pos.getZ() >> 4),
                key -> new HashSet<>()).add(pos);
    }

    public void remove(BlockPos pos) {
        if (controllers.remove(pos)) {
            Set<BlockPos> bucket = controllersByChunk.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
            if (bucket != null) {
                bucket.remove(pos);
                if (bucket.isEmpty()) controllersByChunk.remove(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
            }
            markDirty();
        }
    }

    public Set<BlockPos> getInChunk(World world, ChunkPos chunk, Block controllerBlock) {
        Set<BlockPos> bucket = controllersByChunk.get(chunkKey(chunk.x, chunk.z));
        if (bucket == null || bucket.isEmpty()) return Collections.emptySet();
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : new HashSet<>(bucket)) {
            if (!world.isBlockLoaded(pos)) continue;
            Block actual = world.getBlockState(pos).getBlock();
            if (actual == controllerBlock) {
                result.add(pos);
            } else if (actual != BlockRegistry.ASSEMBLY_CONTROLLER
                    && actual != BlockRegistry.HYPERDIMENSIONAL_CONTROLLER) {
                remove(pos);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<BlockPos> getAll() {
        return Collections.unmodifiableSet(controllers);
    }
}
