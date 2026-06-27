package com.github.aeddddd.ae2enhanced.storage;

import com.github.aeddddd.ae2enhanced.storage.starlight.AEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IAEStarlightStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Astral Sorcery Starlight 描述符,用于在超维度仓储中枢的存储 Map 中作为 Key.
 * 由于 Starlight 只有一种类型,本描述符为无状态单例模式.
 */
public class StarlightDescriptor implements Descriptor {

    public static final StarlightDescriptor INSTANCE = new StarlightDescriptor();

    private transient volatile IAEStarlightStack aeTemplate;

    private StarlightDescriptor() {
    }

    public static StarlightDescriptor fromNBT(NBTTagCompound tag) {
        return INSTANCE;
    }

    @Override
    public NBTTagCompound toNBT() {
        return new NBTTagCompound();
    }

    /**
     * 获取缓存的 IAEStarlightStack 模板(stackSize=1).
     */
    public IAEStarlightStack getAETemplate() {
        IAEStarlightStack result = aeTemplate;
        if (result == null) {
            synchronized (this) {
                result = aeTemplate;
                if (result == null) {
                    result = aeTemplate = AEStarlightStack.create(1);
                }
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StarlightDescriptor;
    }

    @Override
    public int hashCode() {
        return 0x53544152; // "STAR" 的 ASCII 固定哈希
    }
}
