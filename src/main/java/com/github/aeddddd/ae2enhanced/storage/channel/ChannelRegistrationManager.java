package com.github.aeddddd.ae2enhanced.storage.channel;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import com.github.aeddddd.ae2enhanced.integration.botaniaapplie.BotaniaApplieCompat;
import com.github.aeddddd.ae2enhanced.integration.fluxapplied.FluxAppliedCompat;
import com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.mana.ManaStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.starlight.StarlightStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import net.minecraftforge.fml.common.Loader;

/**
 * 存储通道注册管理器.
 * <p>
 * 根据外部兼容层 ({@link FluxAppliedCompat} / {@link BotaniaApplieCompat}) 以及 Forge 的 mod 加载状态,
 * 决定是否为 RF/Mana/Starlight 注册 AE2E 自有存储通道,避免与外部通道重复注册.
 * </p>
 */
public final class ChannelRegistrationManager {

    private static IStorageChannel<?> registeredEnergyChannel;
    private static IStorageChannel<?> registeredManaChannel;
    private static IStorageChannel<?> registeredStarlightChannel;

    private ChannelRegistrationManager() {
    }

    /**
     * 注册所有 AE2E 负责的存储通道.
     * 外部通道已存在时,本方法不再重复注册对应类型.
     */
    public static void registerChannels() {
        // RF: 若 Flux_Applied 已提供能量通道,则交由外部管理
        if (!FluxAppliedCompat.isFluxStorageChannelAvailable()) {
            EnergyStorageChannel channel = new EnergyStorageChannel();
            AEApi.instance().storage().registerStorageChannel(IEnergyStorageChannel.class, channel);
            registeredEnergyChannel = channel;
        }

        // Mana: 若 Botania_Applie 已提供 Mana 通道,则交由外部管理;否则仅在 Botania 存在时注册 AE2E 通道
        if (BotaniaApplieCompat.isManaStorageChannelAvailable()) {
            registeredManaChannel = null;
        } else if (Loader.isModLoaded("botania")) {
            ManaStorageChannel channel = new ManaStorageChannel();
            AEApi.instance().storage().registerStorageChannel(IManaStorageChannel.class, channel);
            registeredManaChannel = channel;
        }

        // Starlight: 当前无外部专用通道,仅根据 Astral Sorcery 是否存在进行注册
        if (Loader.isModLoaded("astralsorcery")) {
            StarlightStorageChannel channel = new StarlightStorageChannel();
            AEApi.instance().storage().registerStorageChannel(IStarlightStorageChannel.class, channel);
            registeredStarlightChannel = channel;
        }
    }

    /**
     * 判断给定通道是否为当前生效的能量通道(外部或 AE2E 自有).
     */
    public static boolean isEnergyChannel(IStorageChannel<?> channel) {
        if (channel == null) {
            return false;
        }
        if (FluxAppliedCompat.isFluxStorageChannelAvailable()) {
            return channel == FluxAppliedCompat.getFluxStorageChannelInstance();
        }
        return channel == registeredEnergyChannel;
    }

    /**
     * 判断给定通道是否为当前生效的 Mana 通道(外部或 AE2E 自有).
     */
    public static boolean isManaChannel(IStorageChannel<?> channel) {
        if (channel == null) {
            return false;
        }
        if (BotaniaApplieCompat.isManaStorageChannelAvailable()) {
            return channel == BotaniaApplieCompat.getManaStorageChannelInstance();
        }
        return channel == registeredManaChannel;
    }

    /**
     * 判断给定通道是否为当前生效的 Starlight 通道.
     */
    public static boolean isStarlightChannel(IStorageChannel<?> channel) {
        if (channel == null) {
            return false;
        }
        return channel == registeredStarlightChannel;
    }
}
