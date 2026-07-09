package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor;

import appeng.api.stacks.AEKey;
import net.minecraft.nbt.CompoundTag;

/**
 * 超维度仓储中枢的存储描述符统一接口。
 * 所有描述符（物品/流体/能量/可选第三方）均作为 {@link java.util.Map} 的 Key 使用，
 * 必须提供稳定的 {@link #equals(Object)}、{@link #hashCode()} 与 NBT 序列化。
 */
public interface Descriptor {

    /**
     * 将描述符序列化为 NBT，用于持久化与网络传输。
     */
    CompoundTag toNBT();

    /**
     * 获取描述符对应的 AE2 键。
     *
     * @return AEKey 实例
     */
    AEKey getAEKey();
}
