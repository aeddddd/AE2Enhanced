package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigInteger;

/**
 * 流体存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 BigInteger 维护数量,突破 long 上限.
 */
public class FluidStorageAdapter extends AbstractStorageAdapter<IAEFluidStack, FluidDescriptor> {

    public FluidStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        file.loadFluids(storage);
        recalcTotal(); // 从文件加载后必须重新计算总数
    }

    @Override
    protected StorageSection getStorageSection() {
        return StorageSection.FLUID;
    }

    @Override
    protected FluidDescriptor createDescriptor(IAEFluidStack input) {
        FluidStack fs = input.getFluidStack();
        if (fs == null) return null;
        return new FluidDescriptor(fs);
    }

    @Override
    protected IAEFluidStack createResult(IAEFluidStack request, BigInteger amount) {
        FluidStack fs = request.getFluidStack();
        if (fs == null) return null;
        IAEFluidStack result = ((IFluidStorageChannel) channel).createStack(fs);
        if (result == null) return null;
        if (amount.compareTo(StorageConstants.LONG_MAX) > 0) {
            result.setStackSize(Long.MAX_VALUE);
        } else {
            result.setStackSize(amount.longValueExact());
        }
        return result;
    }

    @Override
    protected IAEFluidStack getAETemplate(FluidDescriptor descriptor) {
        return descriptor.getAETemplate((IFluidStorageChannel) channel);
    }

    @Override
    public IStorageChannel<IAEFluidStack> getChannel() {
        return (IStorageChannel<IAEFluidStack>) channel;
    }
}
