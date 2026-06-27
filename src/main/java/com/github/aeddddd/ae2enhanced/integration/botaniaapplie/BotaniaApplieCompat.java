package com.github.aeddddd.ae2enhanced.integration.botaniaapplie;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Botania_Applie 兼容性检测类.
 * 仅通过反射探测 {@code nyonio.ae2.ManaStorageChannel},避免无条件加载类中出现外部类的硬引用.
 */
public final class BotaniaApplieCompat {

    private static final boolean LOADED;
    private static final boolean HAS_MANA_CHANNEL;
    private static Class<?> manaChannelClass;
    private static Object manaChannelInstance;

    // Mana 数据包物品反射缓存
    private static Class<?> manaPacketClass;
    private static Method isManaPacketMethod;
    private static Method createPacketMethod;
    private static Method createAEMethod;
    private static Method getManaMethod;

    static {
        boolean loaded = false;
        boolean hasChannel = false;
        try {
            manaChannelClass = Class.forName("nyonio.ae2.ManaStorageChannel");
            loaded = true;
            Object instance = manaChannelClass.getField("INSTANCE").get(null);
            if (instance instanceof IStorageChannel) {
                manaChannelInstance = instance;
                hasChannel = true;
            }
        } catch (Throwable ignored) {
            // Botania_Applie 未安装或类结构不符,静默回退
        }
        LOADED = loaded;
        HAS_MANA_CHANNEL = hasChannel;

        // 单独探测数据包物品
        try {
            manaPacketClass = Class.forName("nyonio.item.ItemManaPacket");
            isManaPacketMethod = manaPacketClass.getMethod("isManaPacket", ItemStack.class);
            createPacketMethod = manaPacketClass.getMethod("create", long.class);
            createAEMethod = manaPacketClass.getMethod("createAE", long.class);
            getManaMethod = manaPacketClass.getMethod("getMana", ItemStack.class);
        } catch (Throwable ignored) {
            // ItemManaPacket 不存在或方法签名不符,静默回退
        }
    }

    private BotaniaApplieCompat() {
    }

    /**
     * 判断 Botania_Applie 相关类是否存在.
     */
    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * 判断 Botania_Applie 的 {@code ManaStorageChannel} 是否可用.
     */
    public static boolean isManaStorageChannelAvailable() {
        return HAS_MANA_CHANNEL;
    }

    /**
     * 获取 Botania_Applie 的 {@code ManaStorageChannel} 单例实例.
     *
     * @return 外部 Mana 存储通道实例,若不可用则返回 null
     */
    @SuppressWarnings("unchecked")
    public static IStorageChannel<?> getManaStorageChannelInstance() {
        return (IStorageChannel<?>) manaChannelInstance;
    }

    /**
     * 获取 Botania_Applie 的 {@code ManaStorageChannel} 类对象.
     *
     * @return 外部通道类,若不存在则返回 null
     */
    public static Class<?> getManaChannelClass() {
        return manaChannelClass;
    }

    /**
     * 判断给定 ItemStack 是否为 Mana 数据包.
     */
    public static boolean isManaPacket(ItemStack stack) {
        if (!LOADED || isManaPacketMethod == null || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            Object result = isManaPacketMethod.invoke(null, stack);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 创建指定数量的 Mana 数据包物品.
     *
     * @return Mana 数据包,失败时返回空堆
     */
    public static ItemStack createManaPacket(long amount) {
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
     * 创建指定数量的 Mana AE 堆叠.
     *
     * @return Mana AE 堆叠,失败时返回 null
     */
    public static IAEItemStack createManaAE(long amount) {
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
     * 获取 Mana 数据包中存储的 Mana 数量.
     *
     * @return Mana 数量,失败或非 Mana 包时返回 0
     */
    public static long getManaPacketAmount(ItemStack stack) {
        if (!LOADED || getManaMethod == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        try {
            Object result = getManaMethod.invoke(null, stack);
            return result instanceof Number ? ((Number) result).longValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
