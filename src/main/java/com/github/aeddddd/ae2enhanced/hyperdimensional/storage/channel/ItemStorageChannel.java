package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * 物品通道，使用 {@link AEItemKey} 作为键。
 */
public class ItemStorageChannel extends AbstractStorageChannel<AEItemKey> {

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    @Override
    @Nullable
    protected AEItemKey cast(AEKey key) {
        return AEItemKey.is(key) ? (AEItemKey) key : null;
    }

    @Override
    protected CompoundTag writeKey(AEItemKey key) {
        return key.toTag();
    }

    @Override
    @Nullable
    protected AEItemKey readKey(CompoundTag tag) {
        return AEItemKey.fromTag(tag);
    }
}
