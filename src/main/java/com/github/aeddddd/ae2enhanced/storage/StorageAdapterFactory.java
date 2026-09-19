package com.github.aeddddd.ae2enhanced.storage;

import com.github.aeddddd.ae2enhanced.integration.botaniaapplie.BotaniaApplieCompat;
import com.github.aeddddd.ae2enhanced.integration.fluxapplied.FluxAppliedCompat;
import com.github.aeddddd.ae2enhanced.storage.external.ExternalStorageAdapter;
import net.minecraftforge.fml.common.Loader;

/**
 * 存储适配器创建工厂（从 TileHyperdimensionalController 拆出，
 * 供 {@link HyperdimensionalStorageManager} 的会话创建使用）.
 *
 * <p>只负责创建与磁盘加载；与 tile 的回调绑定由控制器在 attach 时完成。</p>
 */
public final class StorageAdapterFactory {

    private StorageAdapterFactory() {
    }

    /**
     * 创建能量适配器:优先使用 Flux_Applied 外部通道,否则回退到 AE2E 自有通道.
     */
    @SuppressWarnings("unchecked")
    public static IStorageAdapter createEnergyAdapter(HyperdimensionalStorageFile file) {
        if (FluxAppliedCompat.isFluxStorageChannelAvailable()) {
            ExternalStorageAdapter<EnergyDescriptor> adapter = new ExternalStorageAdapter<>(
                    file,
                    FluxAppliedCompat.getFluxStorageChannelInstance(),
                    "fe",
                    EnergyDescriptor.INSTANCE
            );
            file.loadEnergy((java.util.Map<EnergyDescriptor, HugeCount>) (java.util.Map<?, ?>) adapter.getStorageMap());
            file.registerPostLoadHook(adapter::recalcTotal); // 异步首加载完成后重算总数
            return adapter;
        }
        return new HyperdimensionalEnergyStorageAdapter(file);
    }

    /**
     * 创建 Mana 适配器:优先使用 Botania_Applie 外部通道,否则在 Botania 存在时回退到 AE2E 自有通道.
     */
    @SuppressWarnings("unchecked")
    public static IStorageAdapter createManaAdapter(HyperdimensionalStorageFile file) {
        if (BotaniaApplieCompat.isManaStorageChannelAvailable()) {
            ExternalStorageAdapter<ManaDescriptor> adapter = new ExternalStorageAdapter<>(
                    file,
                    BotaniaApplieCompat.getManaStorageChannelInstance(),
                    "mana",
                    ManaDescriptor.INSTANCE
            );
            file.loadMana((java.util.Map<ManaDescriptor, HugeCount>) (java.util.Map<?, ?>) adapter.getStorageMap());
            file.registerPostLoadHook(adapter::recalcTotal); // 异步首加载完成后重算总数
            return adapter;
        } else if (Loader.isModLoaded("botania")) {
            return new ManaStorageAdapter(file);
        }
        return null;
    }
}
