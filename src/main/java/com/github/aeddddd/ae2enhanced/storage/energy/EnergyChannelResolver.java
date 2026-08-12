package com.github.aeddddd.ae2enhanced.storage.energy;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
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

    /**
     * 判断当前生效的能量通道是否为 Flux_Applied 外部通道.
     */
    public static boolean isFluxChannelActive() {
        return FluxAppliedCompat.isFluxStorageChannelAvailable();
    }

    /**
     * 按当前生效通道创建能量堆叠.
     * <p>
     * Flux 通道生效时返回 FluxStack(经 {@link FluxAppliedCompat#createFluxStack(long)}),
     * 否则返回 AE2E 自有 {@link AEEnergyStack}.返回 raw IAEStack,
     * 调用方不得将其强转为 IAEEnergyStack(Flux 通道下会 ClassCastException).
     * </p>
     *
     * @param amount 能量数量(RF),允许 0(作为空堆叠使用)
     * @return 通道对应的能量堆叠,创建失败时返回 null
     */
    public static IAEStack createStack(long amount) {
        if (FluxAppliedCompat.isFluxStorageChannelAvailable()) {
            return FluxAppliedCompat.createFluxStack(amount);
        }
        return AEEnergyStack.create(amount);
    }
}
