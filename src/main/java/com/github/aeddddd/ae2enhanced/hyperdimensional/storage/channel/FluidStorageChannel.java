package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * 流体通道，使用 {@link AEFluidKey} 作为键。
 */
public class FluidStorageChannel extends AbstractStorageChannel<AEFluidKey> {

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.fluids();
    }

    @Override
    @Nullable
    protected AEFluidKey cast(AEKey key) {
        return AEFluidKey.is(key) ? (AEFluidKey) key : null;
    }

    @Override
    protected CompoundTag writeKey(AEFluidKey key) {
        return key.toTag();
    }

    @Override
    @Nullable
    protected AEFluidKey readKey(CompoundTag tag) {
        return AEFluidKey.fromTag(tag);
    }
}
