package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.ItemDescriptor;

/**
 * 物品通道，内部使用 {@link ItemStorageAdapter} 处理 {@link AEItemKey}。
 */
public class ItemStorageChannel extends AbstractStorageChannel<AEItemKey, ItemDescriptor> {

    public ItemStorageChannel() {
        super(new ItemStorageAdapter());
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }
}
