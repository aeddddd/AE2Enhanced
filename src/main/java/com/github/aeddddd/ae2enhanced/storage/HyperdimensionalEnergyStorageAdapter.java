package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.storage.IStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;

/**
 * 超维度仓储枢纽的 RF 能量存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 {@link HugeCount} 混合精度计数,突破 long 上限.
 */
public class HyperdimensionalEnergyStorageAdapter extends AbstractStorageAdapter<IAEEnergyStack, EnergyDescriptor> {

    public HyperdimensionalEnergyStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = (IStorageChannel<IAEEnergyStack>) EnergyChannelResolver.getChannel();
        file.loadEnergy(storage);
        file.registerPostLoadHook(this::recalcTotal); // 异步首加载完成后重算总数
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
    protected IAEEnergyStack createResult(IAEEnergyStack request, HugeCount amount) {
        return AEEnergyStack.create(amount.toLongSaturated());
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
