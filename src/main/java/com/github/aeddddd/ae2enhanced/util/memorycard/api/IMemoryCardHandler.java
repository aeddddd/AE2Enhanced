package com.github.aeddddd.ae2enhanced.util.memorycard.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 通用内存卡配置复制粘贴的 Handler 接口.
 * 第一期实现 AE2PartHandler / AE2TileHandler,后续新增设备类型只需实现此接口并注册.
 */
public interface IMemoryCardHandler {

    /**
     * 此 Handler 是否能处理该目标.
     * @param target TileEntity 或 IPart
     */
    boolean canHandle(Object target);

    /**
     * 复制目标设备的完整配置(含升级槽).
     * @return 配置 NBT,返回 null 表示无法复制
     */
    NBTTagCompound copy(Object target);

    /**
     * 将配置粘贴到目标设备.
     * @return 粘贴结果
     */
    PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player);

    /**
     * 获取目标设备的显示名称(用于内存卡 tooltip / GUI).
     */
    String getDisplayName(Object target);

}
