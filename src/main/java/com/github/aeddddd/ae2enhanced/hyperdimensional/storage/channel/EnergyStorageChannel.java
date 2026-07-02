package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * 能量通道，内部存储 FE/RF 等能量。
 * <p>当前仅做内部存储，不直接向 AE2 网络提供 getAvailableStacks。</p>
 */
public class EnergyStorageChannel extends AbstractStorageChannel<EnergyKey> {

    @Override
    public AEKeyType getKeyType() {
        return EnergyKey.ENERGY_KEY_TYPE;
    }

    @Override
    @Nullable
    protected EnergyKey cast(AEKey key) {
        return key instanceof EnergyKey ? (EnergyKey) key : null;
    }

    @Override
    protected CompoundTag writeKey(EnergyKey key) {
        return key.toTag();
    }

    @Override
    @Nullable
    protected EnergyKey readKey(CompoundTag tag) {
        return EnergyKey.INSTANCE;
    }

    @Override
    public void getAvailableStacks(appeng.api.stacks.KeyCounter out) {
        // 能量不暴露给 AE2 网络，避免自定义 key type 引发兼容问题
    }
}
