package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.stacks.AEKeyType;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter.HyperdimensionalEnergyStorageAdapter;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.EnergyDescriptor;

/**
 * 能量通道，内部使用 {@link HyperdimensionalEnergyStorageAdapter} 处理 {@link EnergyKey}。
 * <p>当前仅做内部存储，不直接向 AE2 网络提供 getAvailableStacks。</p>
 */
public class EnergyStorageChannel extends AbstractStorageChannel<EnergyKey, EnergyDescriptor> {

    public EnergyStorageChannel() {
        super(new HyperdimensionalEnergyStorageAdapter());
    }

    @Override
    public AEKeyType getKeyType() {
        return EnergyKey.ENERGY_KEY_TYPE;
    }

    @Override
    public void getAvailableStacks(appeng.api.stacks.KeyCounter out) {
        // 能量不暴露给 AE2 网络，避免自定义 key type 引发兼容问题
    }
}
