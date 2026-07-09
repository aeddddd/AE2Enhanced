package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter.FluidStorageAdapter;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.FluidDescriptor;

/**
 * 流体通道，内部使用 {@link FluidStorageAdapter} 处理 {@link AEFluidKey}。
 */
public class FluidStorageChannel extends AbstractStorageChannel<AEFluidKey, FluidDescriptor> {

    public FluidStorageChannel() {
        super(new FluidStorageAdapter());
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.fluids();
    }
}
