package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.EnergyDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.EnergyDescriptor;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * 超维度仓储枢纽的能量存储适配器，继承 {@link AbstractStorageAdapter}。
 * 内部使用 BigInteger 维护数量，突破 long 上限。
 */
public class HyperdimensionalEnergyStorageAdapter extends AbstractStorageAdapter<EnergyKey, EnergyDescriptor> {

    private static final byte TYPE_ENERGY = 3;

    public HyperdimensionalEnergyStorageAdapter() {
        super(EnergyDescriptorCodec.INSTANCE, EnergyKey.ENERGY_KEY_TYPE);
    }

    @Override
    public EnergyDescriptor createDescriptor(EnergyKey input) {
        return EnergyDescriptor.INSTANCE;
    }

    @Override
    @Nullable
    public EnergyKey cast(AEKey key) {
        return key instanceof EnergyKey ? (EnergyKey) key : null;
    }

    @Override
    public EnergyKey createResult(EnergyKey request, BigInteger amount) {
        return request;
    }

    @Override
    protected byte getTypeByte() {
        return TYPE_ENERGY;
    }
}
