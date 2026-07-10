package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor;

import java.util.Objects;

import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEKey;

/**
 * 通用 AEKey 描述符。
 * 适用于任意已注册的 AEKeyType（物品、流体以及第三方模组注册的类型），
 * 通过 AEKey 自身的 NBT 序列化实现持久化，无需为每种类型单独定义描述符。
 */
public class GenericKeyDescriptor implements Descriptor {

    private final AEKey key;

    public GenericKeyDescriptor(AEKey key) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
    }

    @Override
    public AEKey getAEKey() {
        return key;
    }

    @Override
    public CompoundTag toNBT() {
        // toTagGeneric 会写入类型信息，使得 fromTagGeneric 可以恢复任意 AEKeyType
        return key.toTagGeneric();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GenericKeyDescriptor that)) {
            return false;
        }
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key);
    }

    @Override
    public String toString() {
        return "GenericKeyDescriptor{" + key + "}";
    }
}
