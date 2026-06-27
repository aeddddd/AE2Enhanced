package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.mana.AEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IAEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.ManaChannelResolver;

import java.math.BigInteger;

/**
 * 超维度仓储枢纽的 Botania Mana 存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 BigInteger 维护数量,突破 long 上限.
 */
public class ManaStorageAdapter extends AbstractStorageAdapter<IAEManaStack, ManaDescriptor> {

    public ManaStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = (IStorageChannel<IAEManaStack>) ManaChannelResolver.getChannel();
        file.loadMana(storage);
        recalcTotal();
    }

    @Override
    protected StorageSection getStorageSection() {
        return StorageSection.MANA;
    }

    @Override
    protected ManaDescriptor createDescriptor(IAEManaStack input) {
        return ManaDescriptor.INSTANCE;
    }

    @Override
    protected IAEManaStack createResult(IAEManaStack request, BigInteger amount) {
        if (amount.compareTo(StorageConstants.LONG_MAX) > 0) {
            return AEManaStack.create(Long.MAX_VALUE);
        }
        return AEManaStack.create(amount.longValueExact());
    }

    @Override
    protected IAEManaStack getAETemplate(ManaDescriptor descriptor) {
        return descriptor.getAETemplate();
    }

    @Override
    public IStorageChannel<IAEManaStack> getChannel() {
        return (IStorageChannel<IAEManaStack>) channel;
    }
}
