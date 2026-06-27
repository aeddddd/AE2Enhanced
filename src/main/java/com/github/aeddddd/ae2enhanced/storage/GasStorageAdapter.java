package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;

import java.math.BigInteger;

/**
 * 气体存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 BigInteger 维护数量,突破 long 上限.
 */
public class GasStorageAdapter extends AbstractStorageAdapter<com.mekeng.github.common.me.data.IAEGasStack, GasDescriptor> {

    public GasStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
        file.loadGases(storage);
        recalcTotal(); // 从文件加载后必须重新计算总数
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
    protected com.mekeng.github.common.me.data.IAEGasStack createResult(com.mekeng.github.common.me.data.IAEGasStack request, BigInteger amount) {
        com.mekeng.github.common.me.data.IAEGasStack result = request.copy();
        if (amount.compareTo(StorageConstants.LONG_MAX) > 0) {
            result.setStackSize(Long.MAX_VALUE);
        } else {
            result.setStackSize(amount.longValueExact());
        }
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
