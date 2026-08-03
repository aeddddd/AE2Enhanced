package com.github.aeddddd.ae2enhanced.memorycard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 通用内存卡配置复制粘贴的 Handler 接口.
 * 本期实现 AE2 部件/方块设备 Handler,后续新增设备类型只需实现此接口并注册
 * (第三方 Mod Handler 通过 MemoryCardHandlerRegistry 按 modid 条件反射加载).
 */
public interface IMemoryCardHandler {

    /**
     * 此 Handler 是否能处理该目标.
     * @param target BlockEntity 或 IPart
     */
    boolean canHandle(Object target);

    /**
     * 复制目标设备的完整配置(含升级槽).
     * @return 配置 NBT,返回 null 表示无法复制
     */
    CompoundTag copy(Object target);

    /**
     * 将配置粘贴到目标设备.
     * @return 粘贴结果
     */
    PasteResult paste(Object target, CompoundTag data, Player player);

    /**
     * 获取目标设备的显示名称(用于内存卡 tooltip / GUI).
     */
    String getDisplayName(Object target);
}
