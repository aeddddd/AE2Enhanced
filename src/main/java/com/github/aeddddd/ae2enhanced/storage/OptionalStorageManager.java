package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 可选存储管理器：条件加载 Mekanism 气体、Thaumcraft 源质等第三方存储适配器.
 * 同时提供扩展注册接口,允许其他 Mod 通过 API 注册自定义存储适配器.
 *
 * 设计原则：本类<strong>不</strong>直接引用可选 Mod 的类,所有适配器实例通过
 * {@link Class#forName} 延迟加载,字段类型为 {@link Object},方法调用通过反射完成.
 */
public class OptionalStorageManager {

    /**
     * 封装一个可选适配器的实例、对应 channel 类型,以及反射方法缓存.
     */
    private static class OptionalAdapterWrapper {
        final Object adapter;
        final Class<?> channelClass;
        private final Method getStorageMapMethod;
        private final Method getTotalCountMethod;
        private final Method isSafeModeMethod;
        private final Method setOnChangeCallbackMethod;
        private final Method setPostChangeCallbackMethod;

        OptionalAdapterWrapper(Object adapter, Class<?> channelClass) throws NoSuchMethodException {
            this.adapter = adapter;
            this.channelClass = channelClass;
            Class<?> clazz = adapter.getClass();
            this.getStorageMapMethod = clazz.getMethod("getStorageMap");
            this.getTotalCountMethod = clazz.getMethod("getTotalCount");
            this.isSafeModeMethod = clazz.getMethod("isSafeMode");
            this.setOnChangeCallbackMethod = clazz.getMethod("setOnChangeCallback", Runnable.class);
            this.setPostChangeCallbackMethod = clazz.getMethod("setPostChangeCallback", BiConsumer.class);
        }

        boolean handlesChannel(Object channel) {
            return channelClass.isInstance(channel);
        }

        @SuppressWarnings("unchecked")
        Map<Object, BigInteger> getStorageMap() {
            try {
                return (Map<Object, BigInteger>) getStorageMapMethod.invoke(adapter);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to get storage map", e);
                return null;
            }
        }

        BigInteger getTotalCount() {
            try {
                Object total = getTotalCountMethod.invoke(adapter);
                return total instanceof BigInteger ? (BigInteger) total : BigInteger.ZERO;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to get total count", e);
                return BigInteger.ZERO;
            }
        }

        boolean isSafeMode() {
            try {
                Object result = isSafeModeMethod.invoke(adapter);
                return result instanceof Boolean && (Boolean) result;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to get safe mode", e);
                return false;
            }
        }

        void setCallbacks(Runnable changeCallback, BiConsumer<?, IActionSource> postChange) {
            try {
                setOnChangeCallbackMethod.invoke(adapter, changeCallback);
                setPostChangeCallbackMethod.invoke(adapter, postChange);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to set adapter callbacks", e);
            }
        }
    }

    private final List<OptionalAdapterWrapper> optionalAdapters = new CopyOnWriteArrayList<>();

    /**
     * 外部扩展注册表.其他 Mod 可通过 {@link #registerExternalAdapter} 注册自定义 IMEMonitor.
     */
    private final List<Object> externalAdapters = new CopyOnWriteArrayList<>();

    /**
     * 反射方法缓存,避免每次 getHandlers() 都重复 getMethod().
     */
    private final java.util.concurrent.ConcurrentHashMap<Class<?>, Method[]> methodCache =
            new java.util.concurrent.ConcurrentHashMap<>();

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
        initOptionalAdapter("mekeng",
                "com.github.aeddddd.ae2enhanced.storage.GasStorageAdapter",
                "com.mekeng.github.common.me.storage.IGasStorageChannel",
                file);
        initOptionalAdapter("thaumicenergistics",
                "com.github.aeddddd.ae2enhanced.storage.EssentiaStorageAdapter",
                "thaumicenergistics.api.storage.IEssentiaStorageChannel",
                file);
    }

    private void initOptionalAdapter(String modId, String adapterClassName,
                                      String channelClassName, HyperdimensionalStorageFile file) {
        if (!Loader.isModLoaded(modId)) return;
        try {
            Class<?> adapterClass = Class.forName(adapterClassName);
            Object adapter = adapterClass.getConstructor(HyperdimensionalStorageFile.class).newInstance(file);
            Class<?> channelClass = Class.forName(channelClassName);
            optionalAdapters.add(new OptionalAdapterWrapper(adapter, channelClass));
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize optional adapter for {}", modId, e);
        }
    }

    /**
     * 设置回调.通过反射调用适配器的 setOnChangeCallback / setPostChangeCallback.
     */
    @SuppressWarnings("unchecked")
    public void setCallbacks(Runnable changeCallback,
                             BiConsumer<?, IActionSource> postChange) {
        for (OptionalAdapterWrapper wrapper : optionalAdapters) {
            wrapper.setCallbacks(changeCallback, postChange);
        }
    }

    /**
     * 根据 IStorageChannel 返回对应的 IMEInventoryHandler.
     */
    @SuppressWarnings("unchecked")
    public List<IMEInventoryHandler> getHandlers(IStorageChannel<?> channel) {
        for (OptionalAdapterWrapper wrapper : optionalAdapters) {
            if (wrapper.handlesChannel(channel) && wrapper.adapter instanceof IMEInventoryHandler) {
                return Collections.singletonList((IMEInventoryHandler) wrapper.adapter);
            }
        }

        // 外部扩展适配器
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
        for (OptionalAdapterWrapper wrapper : optionalAdapters) {
            Map<?, ?> map = wrapper.getStorageMap();
            if (map != null) count += map.size();
        }
        return count;
    }

    public BigInteger getTotalCount() {
        BigInteger sum = BigInteger.ZERO;
        for (OptionalAdapterWrapper wrapper : optionalAdapters) {
            sum = sum.add(wrapper.getTotalCount());
        }
        return sum;
    }

    public boolean isSafeMode() {
        for (OptionalAdapterWrapper wrapper : optionalAdapters) {
            if (wrapper.isSafeMode()) return true;
        }
        return false;
    }

    /**
     * 刷新所有可选 monitor(反射强制 NetworkMonitor 更新).
     */
    public void refreshMonitors(java.util.function.Consumer<Object> refresher) {
        for (OptionalAdapterWrapper wrapper : optionalAdapters) {
            refresher.accept(wrapper.adapter);
        }
    }

    /**
     * 注册外部存储适配器.其他 Mod 可通过 API 调用此方法注册自定义存储通道.
     */
    public void registerExternalAdapter(Object adapter) {
        if (adapter != null) {
            externalAdapters.add(adapter);
        }
    }

    public Object getGasAdapter() {
        for (OptionalAdapterWrapper wrapper : optionalAdapters) {
            if (wrapper.channelClass.getName().contains("IGasStorageChannel")) {
                return wrapper.adapter;
            }
        }
        return null;
    }

    public Object getEssentiaAdapter() {
        for (OptionalAdapterWrapper wrapper : optionalAdapters) {
            if (wrapper.channelClass.getName().contains("IEssentiaStorageChannel")) {
                return wrapper.adapter;
            }
        }
        return null;
    }

    public void close() {
        optionalAdapters.clear();
        externalAdapters.clear();
        methodCache.clear();
    }
}
