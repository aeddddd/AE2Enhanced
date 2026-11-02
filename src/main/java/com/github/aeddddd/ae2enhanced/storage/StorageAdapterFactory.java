package com.github.aeddddd.ae2enhanced.storage;

import com.github.aeddddd.ae2enhanced.config.BootConfig;
import com.github.aeddddd.ae2enhanced.storage.external.ExternalStorageAdapter;
import net.minecraftforge.fml.common.Loader;

/** 存储适配器创建工厂；通道与提供方在启动期冻结，禁用的通道不创建适配器。 */
public final class StorageAdapterFactory {

    private StorageAdapterFactory() {
    }

    @SuppressWarnings("unchecked")
    public static IStorageAdapter createEnergyAdapter(HyperdimensionalStorageFile file) {
        BootConfig boot = BootConfig.current();
        if (!boot.channelEnabled("energy")) {
            return null;
        }
        boolean available = ExternalStorageChannels.isAvailable(ExternalStorageChannels.Kind.ENERGY);
        if (boot.externalChannelRequired("energy") && !available) {
            throw new IllegalStateException("Energy channel is forced EXTERNAL but Flux Applied is unavailable");
        }
        if (boot.externalChannelAllowed("energy") && available) {
            ExternalStorageAdapter<EnergyDescriptor> adapter = new ExternalStorageAdapter<>(
                    file,
                    ExternalStorageChannels.channel(ExternalStorageChannels.Kind.ENERGY),
                    "fe",
                    EnergyDescriptor.INSTANCE
            );
            file.loadEnergy((java.util.Map<EnergyDescriptor, HugeCount>) (java.util.Map<?, ?>) adapter.getStorageMap());
            file.registerPostLoadHook(adapter::recalcTotal);
            return adapter;
        }
        if (!boot.internalChannelAllowed("energy")) {
            throw new IllegalStateException("Energy channel is forced EXTERNAL but no external provider is available");
        }
        return new HyperdimensionalEnergyStorageAdapter(file);
    }

    @SuppressWarnings("unchecked")
    public static IStorageAdapter createManaAdapter(HyperdimensionalStorageFile file) {
        BootConfig boot = BootConfig.current();
        if (!boot.channelEnabled("mana")) {
            return null;
        }
        boolean available = ExternalStorageChannels.isAvailable(ExternalStorageChannels.Kind.MANA);
        if (boot.externalChannelRequired("mana") && !available) {
            throw new IllegalStateException("Mana channel is forced EXTERNAL but Botania Applie is unavailable");
        }
        if (boot.externalChannelAllowed("mana") && available) {
            ExternalStorageAdapter<ManaDescriptor> adapter = new ExternalStorageAdapter<>(
                    file,
                    ExternalStorageChannels.channel(ExternalStorageChannels.Kind.MANA),
                    "mana",
                    ManaDescriptor.INSTANCE
            );
            file.loadMana((java.util.Map<ManaDescriptor, HugeCount>) (java.util.Map<?, ?>) adapter.getStorageMap());
            file.registerPostLoadHook(adapter::recalcTotal);
            return adapter;
        }
        if (!boot.internalChannelAllowed("mana")) {
            throw new IllegalStateException("Mana channel is forced EXTERNAL but no external provider is available");
        }
        if (Loader.isModLoaded("botania") && boot.integrationEnabled("botania")) {
            return new ManaStorageAdapter(file);
        }
        return null;
    }
}
