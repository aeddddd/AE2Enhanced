package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import net.minecraftforge.fml.common.Loader;

import java.math.BigInteger;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 可选存储管理器：条件加载 Mekanism 气体、Thaumcraft 源质等第三方存储适配器。
 * 同时提供扩展注册接口，允许其他 Mod 通过 API 注册自定义存储适配器。
 *
 * 设计原则：本类中直接引用可选 Mod 的类，但通过 {@link Loader#isModLoaded} 条件保护，
 * 确保未安装对应 Mod 时不会触发类加载。
 */
public class OptionalStorageManager {

    private GasStorageAdapter gasAdapter;
    private EssentiaStorageAdapter essentiaAdapter;

    /**
     * 外部扩展注册表。其他 Mod 可通过 {@link #registerExternalAdapter} 注册自定义 IMEMonitor。
     * 元素类型为 Object，避免编译期对未知类型的强依赖。
     */
    private final List<Object> externalAdapters = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 反射方法缓存，避免每次 getHandlers() 都重复 getMethod()。
     * Key: 外部适配器类；Value: [getChannel 方法, getHandler 方法]
     */
    private final Map<Class<?>, Method[]> methodCache = new ConcurrentHashMap<>();

    private Method[] getCachedMethods(Class<?> clazz) {
        return methodCache.computeIfAbsent(clazz, k -> {
            try {
                return new Method[]{
                    k.getMethod("getChannel"),
                    k.getMethod("getHandler")
                };
            } catch (NoSuchMethodException e) {
                return null;
            }
        });
    }

    public void init(HyperdimensionalStorageFile file) {
        if (Loader.isModLoaded("mekeng")) {
            try {
                gasAdapter = new GasStorageAdapter(file);
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                    "[AE2E] Failed to initialize gas storage adapter", e);
            }
        }
        if (Loader.isModLoaded("thaumicenergistics")) {
            try {
                essentiaAdapter = new EssentiaStorageAdapter(file);
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                    "[AE2E] Failed to initialize essentia storage adapter", e);
            }
        }
    }

    /**
     * 设置回调。由于 callback 签名的泛型差异，使用 Object 类型并通过 instanceof 分发。
     */
    @SuppressWarnings("unchecked")
    public void setCallbacks(Runnable changeCallback,
                             BiConsumer<?, IActionSource> itemPostChange,
                             BiConsumer<?, IActionSource> fluidPostChange) {
        if (gasAdapter != null) {
            gasAdapter.setOnChangeCallback(changeCallback);
            if (itemPostChange instanceof BiConsumer) {
                gasAdapter.setPostChangeCallback((BiConsumer) itemPostChange);
            }
        }
        if (essentiaAdapter != null) {
            essentiaAdapter.setOnChangeCallback(changeCallback);
            if (itemPostChange instanceof BiConsumer) {
                essentiaAdapter.setPostChangeCallback((BiConsumer) itemPostChange);
            }
        }
    }

    /**
     * 根据 IStorageChannel 返回对应的 IMEInventoryHandler。
     */
    @SuppressWarnings("unchecked")
    public List<IMEInventoryHandler> getHandlers(IStorageChannel<?> channel) {
        try {
            Class<?> gasChannelClass = Class.forName("com.mekeng.github.common.me.storage.IGasStorageChannel");
            if (gasAdapter != null && gasChannelClass.isInstance(channel)) {
                return Collections.singletonList(gasAdapter);
            }
        } catch (ClassNotFoundException ignored) {}

        try {
            Class<?> essentiaChannelClass = Class.forName("thaumicenergistics.api.storage.IEssentiaStorageChannel");
            if (essentiaAdapter != null && essentiaChannelClass.isInstance(channel)) {
                return Collections.singletonList(essentiaAdapter);
            }
        } catch (ClassNotFoundException ignored) {}
        // 外部扩展适配器（通过反射匹配 channel 类型，方法已缓存）
        for (Object ext : externalAdapters) {
            Method[] methods = getCachedMethods(ext.getClass());
            if (methods == null) continue;
            try {
                Object extChannel = methods[0].invoke(ext);
                if (extChannel == channel) {
                    Object handler = methods[1].invoke(ext);
                    if (handler instanceof IMEInventoryHandler) {
                        return Collections.singletonList((IMEInventoryHandler) handler);
                    }
                }
            } catch (Exception ignored) {}
        }
        return Collections.emptyList();
    }

    public int getTotalTypeCount() {
        int count = 0;
        if (gasAdapter != null) count += gasAdapter.getStorageMap().size();
        if (essentiaAdapter != null) count += essentiaAdapter.getStorageMap().size();
        return count;
    }

    public BigInteger getTotalCount() {
        BigInteger sum = BigInteger.ZERO;
        if (gasAdapter != null) sum = sum.add(gasAdapter.getTotalCount());
        if (essentiaAdapter != null) sum = sum.add(essentiaAdapter.getTotalCount());
        return sum;
    }

    public boolean isSafeMode() {
        return (gasAdapter != null && gasAdapter.isSafeMode())
            || (essentiaAdapter != null && essentiaAdapter.isSafeMode());
    }

    /**
     * 刷新所有可选 monitor（反射强制 NetworkMonitor 更新）。
     */
    public void refreshMonitors(java.util.function.Consumer<Object> refresher) {
        if (gasAdapter != null) refresher.accept(gasAdapter);
        if (essentiaAdapter != null) refresher.accept(essentiaAdapter);
    }

    /**
     * 注册外部存储适配器。其他 Mod 可通过 API 调用此方法注册自定义存储通道。
     *
     * @param adapter 必须实现 getChannel()、getHandler()、getTypeCount()、getTotalCount()、isSafeMode() 方法
     */
    public void registerExternalAdapter(Object adapter) {
        if (adapter != null) {
            externalAdapters.add(adapter);
        }
    }

    public GasStorageAdapter getGasAdapter() {
        return gasAdapter;
    }

    public EssentiaStorageAdapter getEssentiaAdapter() {
        return essentiaAdapter;
    }

    public void close() {
        // Adapter 本身不持有需要关闭的资源，文件层由 HyperdimensionalStorageFile 统一管理
        gasAdapter = null;
        essentiaAdapter = null;
        externalAdapters.clear();
        methodCache.clear();
    }
}
