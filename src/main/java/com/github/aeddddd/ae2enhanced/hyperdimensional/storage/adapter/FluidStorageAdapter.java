package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.FluidDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.FluidDescriptor;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * 流体存储适配器，继承 {@link AbstractStorageAdapter}。
 * 内部使用 BigInteger 维护数量，突破 long 上限。
 */
public class FluidStorageAdapter extends AbstractStorageAdapter<AEFluidKey, FluidDescriptor> {

    private static final byte TYPE_FLUID = 2;

    public FluidStorageAdapter() {
        super(FluidDescriptorCodec.INSTANCE, AEKeyType.fluids());
    }

    @Override
    public FluidDescriptor createDescriptor(AEFluidKey input) {
        return new FluidDescriptor(input);
    }

    @Override
    @Nullable
    public AEFluidKey cast(AEKey key) {
        return AEFluidKey.is(key) ? (AEFluidKey) key : null;
    }

    @Override
    public AEFluidKey createResult(AEFluidKey request, BigInteger amount) {
        return request;
    }

    @Override
    protected byte getTypeByte() {
        return TYPE_FLUID;
    }
}
