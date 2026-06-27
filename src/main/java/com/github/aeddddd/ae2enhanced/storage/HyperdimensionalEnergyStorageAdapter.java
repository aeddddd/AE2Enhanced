package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;

import java.math.BigInteger;

/**
 * 超维度仓储枢纽的 RF 能量存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 BigInteger 维护数量,突破 long 上限.
 */
public class HyperdimensionalEnergyStorageAdapter extends AbstractStorageAdapter<IAEEnergyStack, EnergyDescriptor> {

    public HyperdimensionalEnergyStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = (IStorageChannel<IAEEnergyStack>) EnergyChannelResolver.getChannel();
        file.loadEnergy(storage);
        recalcTotal();
    }

    @Override
    protected StorageSection getStorageSection() {
        return StorageSection.ENERGY;
    }

    @Override
    protected EnergyDescriptor createDescriptor(IAEEnergyStack input) {
        return EnergyDescriptor.INSTANCE;
    }

    @Override
    protected IAEEnergyStack createResult(IAEEnergyStack request, BigInteger amount) {
        if (amount.compareTo(StorageConstants.LONG_MAX) > 0) {
            return AEEnergyStack.create(Long.MAX_VALUE);
        }
        return AEEnergyStack.create(amount.longValueExact());
    }

    @Override
    protected IAEEnergyStack getAETemplate(EnergyDescriptor descriptor) {
        return descriptor.getAETemplate();
    }

    @Override
    public IStorageChannel<IAEEnergyStack> getChannel() {
        return (IStorageChannel<IAEEnergyStack>) channel;
    }
}
