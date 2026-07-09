package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 物品描述符，用于在内存中作为存储 Map 的 Key。
 * 基于物品注册项与 NBT 内容做 equals/hashCode，不依赖 CompoundTag 引用相等。
 */
public final class ItemDescriptor implements Descriptor {

    private final AEItemKey key;
    @Nullable
    private final CompoundTag nbt;
    private final int hash;

    public ItemDescriptor(AEItemKey key) {
        this.key = key;
        this.nbt = key.hasTag() ? key.getTag().copy() : null;
        this.hash = computeHash();
    }

    private ItemDescriptor(AEItemKey key, @Nullable CompoundTag nbt) {
        this.key = key;
        this.nbt = nbt != null ? nbt.copy() : null;
        this.hash = computeHash();
    }

    private int computeHash() {
        return Objects.hash(key.getItem(), nbt);
    }

    /**
     * 供自定义二进制 Codec 使用的工厂方法。
     */
    public static ItemDescriptor fromRaw(AEItemKey key, @Nullable CompoundTag nbt) {
        return new ItemDescriptor(key, nbt);
    }

    @Override
    public CompoundTag toNBT() {
        return key.toTag();
    }

    @Override
    public AEKey getAEKey() {
        return key;
    }

    public AEItemKey getAEItemKey() {
        return key;
    }

    @Nullable
    public CompoundTag getNbt() {
        return nbt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemDescriptor)) {
            return false;
        }
        ItemDescriptor other = (ItemDescriptor) o;
        return key.getItem() == other.key.getItem() && Objects.equals(nbt, other.nbt);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
