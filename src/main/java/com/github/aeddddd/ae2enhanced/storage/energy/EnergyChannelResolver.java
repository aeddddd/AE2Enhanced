package com.github.aeddddd.ae2enhanced.storage.energy;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import com.github.aeddddd.ae2enhanced.integration.fluxapplied.FluxAppliedCompat;

/**
 * RF 能量通道解析器.
 * <p>
 * 动态返回当前实际生效的能量存储通道：若 Flux_Applied 已注册外部通道则优先返回外部通道,
 * 否则返回 AE2E 自有的 {@link IEnergyStorageChannel}.
 * </p>
 */
public final class EnergyChannelResolver {

    private EnergyChannelResolver() {
    }

    /**
     * 获取当前生效的能量存储通道.
     *
     * @return 外部 Flux 通道或 AE2E 自有能量通道
     */
    public static IStorageChannel<?> getChannel() {
        if (FluxAppliedCompat.isFluxStorageChannelAvailable()) {
            return FluxAppliedCompat.getFluxStorageChannelInstance();
        }
        return AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class);
    }
}
