package com.github.aeddddd.ae2enhanced.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.NotNull;

/**
 * 将装配枢纽的样板数据存储在维度级别的 SavedData 中，而非方块实体 NBT。
 * <p>避免 100 页（10200 槽）的样板数据直接写入区块 NBT，降低单区块 NBT 过大导致
 * 保存/加载异常的风险。</p>
 */
public class AssemblyPatternSavedData extends SavedData {

    private static final String DATA_ID = "ae2enhanced_assembly_patterns";

    private final CompoundTag data = new CompoundTag();

    public AssemblyPatternSavedData() {
    }

    public static AssemblyPatternSavedData get(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getDataStorage().computeIfAbsent(
                AssemblyPatternSavedData::load,
                AssemblyPatternSavedData::new,
                DATA_ID);
    }

    public static AssemblyPatternSavedData load(CompoundTag nbt) {
        AssemblyPatternSavedData savedData = new AssemblyPatternSavedData();
        if (nbt.contains("patterns", CompoundTag.TAG_COMPOUND)) {
            savedData.data.merge(nbt.getCompound("patterns"));
        }
        return savedData;
    }

    @Override
    @NotNull
    public CompoundTag save(CompoundTag compound) {
        compound.put("patterns", data.copy());
        return compound;
    }

    private String key(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /**
     * 读取指定控制器位置的样板数据。
     */
    public CompoundTag getPatterns(BlockPos pos) {
        String key = key(pos);
        return data.contains(key, CompoundTag.TAG_COMPOUND)
                ? data.getCompound(key).copy()
                : new CompoundTag();
    }

    /**
     * 保存指定控制器位置的样板数据。
     */
    public void setPatterns(BlockPos pos, CompoundTag patterns) {
        data.put(key(pos), patterns.copy());
        setDirty();
    }

    /**
     * 移除指定控制器位置的样板数据。方块被破坏时调用。
     */
    public void removePatterns(BlockPos pos) {
        data.remove(key(pos));
        setDirty();
    }
}
