package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraftforge.fml.ModList;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.StorageChannel;

/**
 * 可选存储通道管理器。
 * <p>本类不再通过反射扫描第三方 Mod 主类，而是维护一个显式的 modId -> 通道工厂映射。
 * 第三方 Mod（或本模组的集成模块）在初始化时调用 {@link #registerOptional(String, Supplier)} 注册
 * 对应 AE key type 的 {@link StorageChannel} 工厂；当目标 Mod 已安装时，工厂才会被调用并注册通道。</p>
 *
 * <p>这种方式避免了反射加载不存在类时的静默失败，也使扩展点清晰、类型安全。</p>
 */
public class OptionalStorageManager {

    private static final OptionalStorageManager INSTANCE = new OptionalStorageManager();

    private final Map<String, Supplier<StorageChannel<?>>> factories = new HashMap<>();

    private OptionalStorageManager() {
        // 预留已知第三方 Mod 的扩展点。实际工厂由对应集成模块注册。
        // 当前 1.20.1 分支下这些 Mod 尚未移植，因此列表仅作文档化占位。
        registerOptional("mekanism", () -> null);
        registerOptional("thaumcraft", () -> null);
        registerOptional("botania", () -> null);
        registerOptional("astralsorcery", () -> null);
    }

    public static OptionalStorageManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册可选存储通道工厂。
     * <p>若对应 modId 已加载且工厂返回非 null 通道，则会在超维度存储初始化时自动注册。</p>
     *
     * @param modId   目标 Mod ID
     * @param factory 通道工厂
     */
    public void registerOptional(String modId, Supplier<StorageChannel<?>> factory) {
        factories.put(modId, factory);
    }

    /**
     * 根据已加载的 Mod 注册所有可选通道。
     *
     * @param storage 目标超维度存储容器
     */
    public void registerOptionalChannels(HyperdimensionalStorage storage) {
        for (Map.Entry<String, Supplier<StorageChannel<?>>> entry : factories.entrySet()) {
            String modId = entry.getKey();
            if (!ModList.get().isLoaded(modId)) {
                continue;
            }
            try {
                StorageChannel<?> channel = entry.getValue().get();
                if (channel != null) {
                    storage.registerChannel(channel);
                    AE2Enhanced.LOGGER.info("[AE2E] 已注册可选存储通道: {}", modId);
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] 加载可选存储通道 {} 失败", modId, e);
            }
        }
    }
}
