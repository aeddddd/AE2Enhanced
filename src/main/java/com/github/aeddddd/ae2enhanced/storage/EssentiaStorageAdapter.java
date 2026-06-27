package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;

import java.math.BigInteger;

/**
 * 源质存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 BigInteger 维护数量,突破 long 上限.
 */
public class EssentiaStorageAdapter extends AbstractStorageAdapter<thaumicenergistics.api.storage.IAEEssentiaStack, EssentiaDescriptor> {

    public EssentiaStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
        file.loadEssentias(storage);
        recalcTotal(); // 从文件加载后必须重新计算总数
    }

    @Override
    protected StorageSection getStorageSection() {
        return StorageSection.ESSENTIA;
    }

    @Override
    protected EssentiaDescriptor createDescriptor(thaumicenergistics.api.storage.IAEEssentiaStack input) {
        return new EssentiaDescriptor(input);
    }

    @Override
    protected thaumicenergistics.api.storage.IAEEssentiaStack createResult(thaumicenergistics.api.storage.IAEEssentiaStack request, BigInteger amount) {
        thaumicenergistics.api.storage.IAEEssentiaStack result = request.copy();
        if (amount.compareTo(StorageConstants.LONG_MAX) > 0) {
            result.setStackSize(Long.MAX_VALUE);
        } else {
            result.setStackSize(amount.longValueExact());
        }
        return result;
    }

    @Override
    protected thaumicenergistics.api.storage.IAEEssentiaStack getAETemplate(EssentiaDescriptor descriptor) {
        return descriptor.getAETemplate();
    }

    @Override
    public IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> getChannel() {
        return (IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack>) channel;
    }
}
