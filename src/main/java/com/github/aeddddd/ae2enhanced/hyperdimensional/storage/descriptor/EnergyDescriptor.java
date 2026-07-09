package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor;

import appeng.api.stacks.AEKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;
import net.minecraft.nbt.CompoundTag;

/**
 * 能量描述符，用于在超维度仓储中枢的存储 Map 中作为 Key。
 * 由于能量只有一种类型，本描述符为无状态单例模式。
 */
public final class EnergyDescriptor implements Descriptor {

    public static final EnergyDescriptor INSTANCE = new EnergyDescriptor();

    private EnergyDescriptor() {
    }

    @Override
    public CompoundTag toNBT() {
        return new CompoundTag();
    }

    @Override
    public AEKey getAEKey() {
        return EnergyKey.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EnergyDescriptor;
    }

    @Override
    public int hashCode() {
        return 0x45E2E2; // "Energy" 的固定哈希
    }
}
