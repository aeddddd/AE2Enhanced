package com.github.aeddddd.ae2enhanced.structure;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 记录世界中所有超因果计算核心控制器位置的持久化索引。
 * <p>供 {@link com.github.aeddddd.ae2enhanced.event.StructureEventHandler} 快速定位受结构变化影响的控制器。</p>
 */
public class ComputationCoreIndex extends SavedData {

    private static final String DATA_NAME = AE2Enhanced.MOD_ID + "_computation_core_index";
    private final Set<BlockPos> cores = new HashSet<>();

    public ComputationCoreIndex() {
    }

    public ComputationCoreIndex(CompoundTag tag) {
        load(tag);
    }

    public void load(CompoundTag tag) {
        cores.clear();
        ListTag list = tag.getList("cores", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag posTag = list.getCompound(i);
            cores.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockPos pos : cores) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            list.add(posTag);
        }
        tag.put("cores", list);
        return tag;
    }

    public static ComputationCoreIndex get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(ComputationCoreIndex::new, ComputationCoreIndex::new, DATA_NAME);
    }

    public void add(BlockPos pos) {
        if (cores.add(pos.immutable())) {
            setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (cores.remove(pos.immutable())) {
            setDirty();
        }
    }

    public Set<BlockPos> getAll() {
        return Collections.unmodifiableSet(cores);
    }
}
