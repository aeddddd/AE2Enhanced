package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 将当前可识别数据与原数据合并；已注册内容优先，未知字段原样保留。 */
public final class PreservedNbt {
    private static final Set<String> STACK_KEYS = new HashSet<>(Arrays.asList(
            "id", "Count", "Damage", "tag", "Slot", "ForgeCaps"));

    private PreservedNbt() {
    }

    public static NBTTagCompound mergeRoot(NBTTagCompound fresh, NBTTagCompound original, Set<String> ownedKeys) {
        NBTTagCompound result = original == null ? new NBTTagCompound() : original.copy();
        if (fresh == null) return result;
        for (String key : ownedKeys) {
            if (fresh.hasKey(key)) {
                result.setTag(key, fresh.getTag(key));
            } else {
                result.removeTag(key);
            }
        }
        return result;
    }

    public static NBTTagCompound mergeStack(NBTTagCompound fresh, NBTTagCompound original) {
        NBTTagCompound result = mergeRoot(fresh, original, STACK_KEYS);
        if (original != null && original.hasKey("ForgeCaps", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound capabilities = original.getCompoundTag("ForgeCaps").copy();
            if (fresh != null && fresh.hasKey("ForgeCaps", Constants.NBT.TAG_COMPOUND)) {
                NBTTagCompound current = fresh.getCompoundTag("ForgeCaps");
                for (String key : current.getKeySet()) {
                    capabilities.setTag(key, current.getTag(key));
                }
            }
            result.setTag("ForgeCaps", capabilities);
        }
        return result;
    }
}
