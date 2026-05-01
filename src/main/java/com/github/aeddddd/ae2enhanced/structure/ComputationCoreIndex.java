package com.github.aeddddd.ae2enhanced.structure;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 超因果计算核心位置索引。
 * 用于追踪世界中所有已放置的计算核心控制器位置。
 */
public class ComputationCoreIndex extends WorldSavedData {

    private static final String DATA_NAME = AE2Enhanced.MOD_ID + "_computation_core_index";
    private final Set<BlockPos> cores = new HashSet<>();

    public ComputationCoreIndex() {
        super(DATA_NAME);
    }

    public ComputationCoreIndex(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        cores.clear();
        NBTTagList list = nbt.getTagList("cores", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound posTag = list.getCompoundTagAt(i);
            cores.add(new BlockPos(posTag.getInteger("x"), posTag.getInteger("y"), posTag.getInteger("z")));
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

    public void add(BlockPos pos) {
        if (cores.add(pos)) {
            markDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (cores.remove(pos)) {
            markDirty();
        }
    }

    public Set<BlockPos> getAll() {
        return Collections.unmodifiableSet(cores);
    }
}
