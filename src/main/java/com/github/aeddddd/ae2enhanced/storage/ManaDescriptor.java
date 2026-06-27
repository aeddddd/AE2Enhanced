package com.github.aeddddd.ae2enhanced.storage;

import com.github.aeddddd.ae2enhanced.storage.mana.AEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IAEManaStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Botania Mana 描述符,用于在超维度仓储中枢的存储 Map 中作为 Key.
 * 由于 Mana 只有一种类型,本描述符为无状态单例模式.
 */
public class ManaDescriptor implements Descriptor {

    public static final ManaDescriptor INSTANCE = new ManaDescriptor();

    private transient volatile IAEManaStack aeTemplate;

    private ManaDescriptor() {
    }

    public static ManaDescriptor fromNBT(NBTTagCompound tag) {
        return INSTANCE;
    }

    @Override
    public NBTTagCompound toNBT() {
        return new NBTTagCompound();
    }

    /**
     * 获取缓存的 IAEManaStack 模板(stackSize=1).
     */
    public IAEManaStack getAETemplate() {
        IAEManaStack result = aeTemplate;
        if (result == null) {
            synchronized (this) {
                result = aeTemplate;
                if (result == null) {
                    result = aeTemplate = AEManaStack.create(1);
                }
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ManaDescriptor;
    }

    @Override
    public int hashCode() {
        return 0x4D414E41; // "MANA" 的 ASCII 固定哈希
    }
}
