package com.github.aeddddd.ae2enhanced.mixin;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;

import java.lang.reflect.Method;

/**
 * Mixin 层反射调用统一封装.
 * NetworkMonitor / GridStorageCache 的私有方法通过反射调用,集中在此类缓存 Method 对象,
 * 避免每个 mixin 中重复写 try-catch + setAccessible.
 */
public final class MixinReflectionHelper {

    private static Method NOTIFY_LISTENERS_METHOD;
    private static Method POST_CHANGE_METHOD;

    static {
        try {
            NOTIFY_LISTENERS_METHOD = NetworkMonitor.class.getDeclaredMethod(
                    "notifyListenersOfChange", Iterable.class, IActionSource.class);
            NOTIFY_LISTENERS_METHOD.setAccessible(true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to cache NetworkMonitor.notifyListenersOfChange", e);
        }
        try {
            POST_CHANGE_METHOD = NetworkMonitor.class.getDeclaredMethod(
                    "postChange", boolean.class, Iterable.class, IActionSource.class);
            POST_CHANGE_METHOD.setAccessible(true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to cache NetworkMonitor.postChange", e);
        }
    }

    private MixinReflectionHelper() {}

    /**
     * 调用 NetworkMonitor.notifyListenersOfChange(Iterable, IActionSource).
     */
    @SuppressWarnings("unchecked")
    public static void notifyListenersOfChange(NetworkMonitor monitor, Iterable<IAEItemStack> diff, IActionSource source) {
        if (NOTIFY_LISTENERS_METHOD == null) return;
        try {
            NOTIFY_LISTENERS_METHOD.invoke(monitor, diff, source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] notifyListenersOfChange reflection failed", e);
        }
    }

    /**
     * 调用 NetworkMonitor.postChange(boolean, Iterable, IActionSource).
     */
    @SuppressWarnings("unchecked")
    public static void postChange(NetworkMonitor monitor, boolean add, Iterable<IAEItemStack> changes, IActionSource source) {
        if (POST_CHANGE_METHOD == null) return;
        try {
            POST_CHANGE_METHOD.invoke(monitor, add, changes, source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] postChange reflection failed", e);
        }
    }
}
