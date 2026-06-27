package com.github.aeddddd.ae2enhanced.platform.energy;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.platform.energy.adapter.ForgeEnergyAdapter;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.List;

/**
 * 能量适配器反射隔离注册表.
 *
 * <p>无条件加载,内部通过 {@link Class#forName(String)} 懒加载具体适配器类.
 * 未安装的 mod 对应的适配器类永远不会被触碰,避免 {@link NoClassDefFoundError}.</p>
 *
 * <p>注册顺序：通用 fallback ({@link ForgeEnergyAdapter}) 最后匹配,
 * mod-specific adapters 先注册.{@link #findAdapter(String)} 按注册顺序匹配
 * {@link IEnergyAdapter#canHandle(String)}.</p>
 */
public class EnergyAdapterRegistry {

    private static final List<IEnergyAdapter> ADAPTERS = new ArrayList<>();
    private static final IEnergyAdapter FALLBACK = new ForgeEnergyAdapter();
    private static boolean initialized = false;

    /**
     * 初始化注册表.线程安全,重复调用无效果.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // Mod-specific adapters(反射隔离加载)
        tryLoad("enderio", "com.github.aeddddd.ae2enhanced.platform.energy.adapter.EIOEnergyAdapter");
        tryLoad("thermalexpansion", "com.github.aeddddd.ae2enhanced.platform.energy.adapter.TEEnergyAdapter");
        tryLoad("draconicevolution", "com.github.aeddddd.ae2enhanced.platform.energy.adapter.DEEnergyAdapter");
        tryLoad("mekanism", "com.github.aeddddd.ae2enhanced.platform.energy.adapter.MekanismEnergyAdapter");
        tryLoad("techreborn", "com.github.aeddddd.ae2enhanced.platform.energy.adapter.TechRebornEnergyAdapter");
        tryLoad("rftools", "com.github.aeddddd.ae2enhanced.platform.energy.adapter.McJtyEnergyAdapter");
        tryLoad("modularmachinery", "com.github.aeddddd.ae2enhanced.platform.energy.adapter.MMCEEnergyAdapter");
    }

    private static void tryLoad(String modId, String className) {
        if (!Loader.isModLoaded(modId)) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className);
            IEnergyAdapter adapter = (IEnergyAdapter) clazz.newInstance();
            ADAPTERS.add(adapter);
            AE2Enhanced.LOGGER.info("[AE2E] EnergyAdapterRegistry loaded adapter for mod: {}", modId);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] EnergyAdapterRegistry failed to load adapter for mod: {}", modId, e);
        }
    }

    /**
     * 根据方块 ID 查找匹配的适配器.
     *
     * @param blockId 方块注册 ID(如 "enderio:block_alloy_smelter")
     * @return 匹配的适配器；若无可匹配者,返回默认 fallback
     */
    public static IEnergyAdapter findAdapter(String blockId) {
        init();
        for (IEnergyAdapter adapter : ADAPTERS) {
            if (adapter.canHandle(blockId)) {
                return adapter;
            }
        }
        return FALLBACK;
    }

    /**
     * 获取所有已注册的适配器副本(用于调试或列表展示).
     */
    public static List<IEnergyAdapter> getAdapters() {
        init();
        return new ArrayList<>(ADAPTERS);
    }
}
