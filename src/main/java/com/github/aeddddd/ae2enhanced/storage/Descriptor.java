package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 超维度仓储中枢的存储描述符统一接口.
 * 所有描述符(物品/流体/气体/源质)均作为 {@link java.util.Map} 的 Key 使用,
 * 必须提供稳定的 {@link #equals(Object)}、{@link #hashCode()} 和 NBT 序列化.
 */
public interface Descriptor {

    /**
     * 将描述符序列化为 NBT,用于 {@link HyperdimensionalStorageFile} 的持久化存储.
     */
    NBTTagCompound toNBT();
}
