package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 流体描述符，用于在内存中作为存储 Map 的 Key。
 * 基于流体注册项与 NBT 内容做 equals/hashCode。
 */
public final class FluidDescriptor implements Descriptor {

    private final AEFluidKey key;
    @Nullable
    private final CompoundTag nbt;
    private final int hash;

    public FluidDescriptor(AEFluidKey key) {
        this.key = key;
        this.nbt = key.hasTag() ? key.getTag().copy() : null;
        this.hash = computeHash();
    }

    private FluidDescriptor(AEFluidKey key, @Nullable CompoundTag nbt) {
        this.key = key;
        this.nbt = nbt != null ? nbt.copy() : null;
        this.hash = computeHash();
    }

    private int computeHash() {
        return Objects.hash(key.getFluid(), nbt);
    }

    /**
     * 供自定义二进制 Codec 使用的工厂方法。
     */
    public static FluidDescriptor fromRaw(AEFluidKey key, @Nullable CompoundTag nbt) {
        return new FluidDescriptor(key, nbt);
    }

    @Override
    public CompoundTag toNBT() {
        return key.toTag();
    }

    @Override
    public AEKey getAEKey() {
        return key;
    }

    public AEFluidKey getAEFluidKey() {
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
        if (!(o instanceof FluidDescriptor)) {
            return false;
        }
        FluidDescriptor other = (FluidDescriptor) o;
        return key.getFluid() == other.key.getFluid() && Objects.equals(nbt, other.nbt);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
