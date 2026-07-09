package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.StorageSection;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.ItemDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.ItemDescriptor;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * 物品存储适配器，继承 {@link AbstractStorageAdapter}。
 * 内部使用 BigInteger 维护数量，突破 long 上限。
 */
public class ItemStorageAdapter extends AbstractStorageAdapter<AEItemKey, ItemDescriptor> {

    private static final byte TYPE_ITEM = 1;

    public ItemStorageAdapter() {
        super(ItemDescriptorCodec.INSTANCE, StorageSection.ITEMS);
    }

    @Override
    public ItemDescriptor createDescriptor(AEItemKey input) {
        return new ItemDescriptor(input);
    }

    @Override
    @Nullable
    public AEItemKey cast(AEKey key) {
        return AEItemKey.is(key) ? (AEItemKey) key : null;
    }

    @Override
    public AEItemKey createResult(AEItemKey request, BigInteger amount) {
        return request;
    }

    @Override
    protected byte getTypeByte() {
        return TYPE_ITEM;
    }
}
