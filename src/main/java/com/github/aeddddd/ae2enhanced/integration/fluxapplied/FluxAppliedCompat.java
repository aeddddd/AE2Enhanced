package com.github.aeddddd.ae2enhanced.integration.fluxapplied;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Flux_Applied 兼容性检测类.
 * 仅通过反射探测 {@code com.flux_applied.ae2.FluxStorageChannel},避免无条件加载类中出现外部类的硬引用.
 */
public final class FluxAppliedCompat {

    private static final boolean LOADED;
    private static final boolean HAS_FLUX_CHANNEL;
    private static Class<?> fluxChannelClass;
    private static Object fluxChannelInstance;

    // Flux 数据包物品反射缓存
    private static Class<?> fluxPacketClass;
    private static Method isFluxPacketMethod;
    private static Method createPacketMethod;
    private static Method createAEMethod;
    private static Method getFEMethod;

    static {
        boolean loaded = false;
        boolean hasChannel = false;
        try {
            fluxChannelClass = Class.forName("com.flux_applied.ae2.FluxStorageChannel");
            loaded = true;
            Object instance = fluxChannelClass.getField("INSTANCE").get(null);
            if (instance instanceof IStorageChannel) {
                fluxChannelInstance = instance;
                hasChannel = true;
            }
        } catch (Throwable ignored) {
            // Flux_Applied 未安装或类结构不符,静默回退
        }
        LOADED = loaded;
        HAS_FLUX_CHANNEL = hasChannel;

        // 单独探测数据包物品,避免通道可用但物品类不存在时影响通道判断
        try {
            fluxPacketClass = Class.forName("com.flux_applied.item.ItemFluxPacket");
            isFluxPacketMethod = fluxPacketClass.getMethod("isFluxPacket", ItemStack.class);
            createPacketMethod = fluxPacketClass.getMethod("create", long.class);
            createAEMethod = fluxPacketClass.getMethod("createAE", long.class);
            getFEMethod = fluxPacketClass.getMethod("getFE", ItemStack.class);
        } catch (Throwable ignored) {
            // ItemFluxPacket 不存在或方法签名不符,静默回退
        }
    }

    private FluxAppliedCompat() {
    }

    /**
     * 判断 Flux_Applied 相关类是否存在.
     */
    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * 判断 Flux_Applied 的 {@code FluxStorageChannel} 是否可用.
     */
    public static boolean isFluxStorageChannelAvailable() {
        return HAS_FLUX_CHANNEL;
    }

    /**
     * 获取 Flux_Applied 的 {@code FluxStorageChannel} 单例实例.
     *
     * @return 外部能量存储通道实例,若不可用则返回 null
     */
    @SuppressWarnings("unchecked")
    public static IStorageChannel<?> getFluxStorageChannelInstance() {
        return (IStorageChannel<?>) fluxChannelInstance;
    }

    /**
     * 获取 Flux_Applied 的 {@code FluxStorageChannel} 类对象.
     *
     * @return 外部通道类,若不存在则返回 null
     */
    public static Class<?> getFluxChannelClass() {
        return fluxChannelClass;
    }

    /**
     * 判断给定 ItemStack 是否为 Flux 数据包.
     */
    public static boolean isFluxPacket(ItemStack stack) {
        if (!LOADED || isFluxPacketMethod == null || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            Object result = isFluxPacketMethod.invoke(null, stack);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 创建指定数量的 Flux 数据包物品.
     *
     * @return Flux 数据包,失败时返回空堆
     */
    public static ItemStack createFluxPacket(long amount) {
        if (!LOADED || createPacketMethod == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object result = createPacketMethod.invoke(null, amount);
            return result instanceof ItemStack ? (ItemStack) result : ItemStack.EMPTY;
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 创建指定数量的 Flux AE 堆叠.
     *
     * @return Flux AE 堆叠,失败时返回 null
     */
    public static IAEItemStack createFluxAE(long amount) {
        if (!LOADED || createAEMethod == null) {
            return null;
        }
        try {
            Object result = createAEMethod.invoke(null, amount);
            return result instanceof IAEItemStack ? (IAEItemStack) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 获取 Flux 数据包中存储的能量数量.
     *
     * @return 能量数量,失败或非 Flux 包时返回 0
     */
    public static long getFluxPacketAmount(ItemStack stack) {
        if (!LOADED || getFEMethod == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        try {
            Object result = getFEMethod.invoke(null, stack);
            return result instanceof Number ? ((Number) result).longValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
