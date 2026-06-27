package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * RF 能量描述符,用于在超维度仓储中枢的存储 Map 中作为 Key.
 * 由于 RF 能量只有一种类型,本描述符为无状态单例模式.
 */
public class EnergyDescriptor implements Descriptor {

    public static final EnergyDescriptor INSTANCE = new EnergyDescriptor();

    private transient volatile IAEEnergyStack aeTemplate;

    private EnergyDescriptor() {
    }

    public static EnergyDescriptor fromNBT(NBTTagCompound tag) {
        return INSTANCE;
    }

    @Override
    public NBTTagCompound toNBT() {
        return new NBTTagCompound();
    }

    /**
     * 获取缓存的 IAEEnergyStack 模板(stackSize=1).
     */
    public IAEEnergyStack getAETemplate() {
        IAEEnergyStack result = aeTemplate;
        if (result == null) {
            synchronized (this) {
                result = aeTemplate;
                if (result == null) {
                    result = aeTemplate = AEEnergyStack.create(1);
                }
            }
        }
        return result;
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
