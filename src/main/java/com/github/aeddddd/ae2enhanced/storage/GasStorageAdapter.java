package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;

/**
 * 气体存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 {@link HugeCount} 混合精度计数,突破 long 上限.
 */
public class GasStorageAdapter extends AbstractStorageAdapter<com.mekeng.github.common.me.data.IAEGasStack, GasDescriptor> {

    public GasStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
        file.loadGases(storage);
        file.registerPostLoadHook(this::recalcTotal); // 异步首加载完成后重算总数
    }

    @Override
    protected StorageSection getStorageSection() {
        return StorageSection.GAS;
    }

    @Override
    protected GasDescriptor createDescriptor(com.mekeng.github.common.me.data.IAEGasStack input) {
        return new GasDescriptor(input);
    }

    @Override
    protected com.mekeng.github.common.me.data.IAEGasStack createResult(com.mekeng.github.common.me.data.IAEGasStack request, HugeCount amount) {
        com.mekeng.github.common.me.data.IAEGasStack result = request.copy();
        result.setStackSize(amount.toLongSaturated());
        return result;
    }

    @Override
    protected com.mekeng.github.common.me.data.IAEGasStack getAETemplate(GasDescriptor descriptor) {
        return descriptor.getAETemplate();
    }

    @Override
    public IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> getChannel() {
        return (IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack>) channel;
    }
}
