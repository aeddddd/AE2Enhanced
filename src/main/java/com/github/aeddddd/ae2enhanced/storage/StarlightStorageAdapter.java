package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.starlight.AEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IAEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel;

import java.math.BigInteger;

/**
 * 超维度仓储枢纽的 Astral Sorcery Starlight 存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 BigInteger 维护数量,突破 long 上限.
 */
public class StarlightStorageAdapter extends AbstractStorageAdapter<IAEStarlightStack, StarlightDescriptor> {

    public StarlightStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = AEApi.instance().storage().getStorageChannel(IStarlightStorageChannel.class);
        file.loadStarlight(storage);
        recalcTotal();
    }

    @Override
    protected StorageSection getStorageSection() {
        return StorageSection.STARLIGHT;
    }

    @Override
    protected StarlightDescriptor createDescriptor(IAEStarlightStack input) {
        return StarlightDescriptor.INSTANCE;
    }

    @Override
    protected IAEStarlightStack createResult(IAEStarlightStack request, BigInteger amount) {
        if (amount.compareTo(StorageConstants.LONG_MAX) > 0) {
            return AEStarlightStack.create(Long.MAX_VALUE);
        }
        return AEStarlightStack.create(amount.longValueExact());
    }

    @Override
    protected IAEStarlightStack getAETemplate(StarlightDescriptor descriptor) {
        return descriptor.getAETemplate();
    }

    @Override
    public IStorageChannel<IAEStarlightStack> getChannel() {
        return (IStorageChannel<IAEStarlightStack>) channel;
    }
}
