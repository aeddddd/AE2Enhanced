package com.github.aeddddd.ae2enhanced.util.fakeitem;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 假物品注册表.
 * 每种假物品类型(FluidDrop/GasDrop)注册一个 FakeItemHandler,
 * 统一负责 ItemStack 与实际存储对象的互相转换.
 *
 * 设计参考 ae2fc 的 FakeItemRegister,但独立实现以避免与 ae2fc 冲突.
 */
public final class FakeItemRegister {

    private static final Map<Class<? extends Item>, FakeItemHandler<?, ?>> HANDLERS = new HashMap<>();

    public static void registerHandler(Class<? extends Item> host, FakeItemHandler<?, ?> handler) {
        if (host == null) {
            throw new IllegalArgumentException("Null fake item class");
        }
        if (HANDLERS.containsKey(host)) {
            throw new IllegalArgumentException("Duplicate fake item handler for " + host.getName());
        }
        HANDLERS.put(host, handler);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FakeItemHandler handler = checkItem(stack.getItem());
        return handler == null ? null : (T) handler.getStack(stack);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getStack(IAEItemStack stack) {
        if (stack == null) return null;
        FakeItemHandler handler = checkItem(stack.getItem());
        return handler == null ? null : (T) handler.getStack(stack);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getAEStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FakeItemHandler handler = checkItem(stack.getItem());
        return handler == null ? null : (T) handler.getAEStack(stack);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getAEStack(IAEItemStack stack) {
        if (stack == null) return null;
        FakeItemHandler handler = checkItem(stack.getItem());
        return handler == null ? null : (T) handler.getAEStack(stack);
    }

    @SuppressWarnings("unchecked")
    public static <T> ItemStack packStack(T target, Item host) {
        FakeItemHandler handler = checkItem(host);
        return handler == null ? null : handler.packStack(target);
    }

    @SuppressWarnings("unchecked")
    public static <T> IAEItemStack packAEStack(T target, Item host) {
        FakeItemHandler handler = checkItem(host);
        return handler == null ? null : handler.packAEStack(target);
    }

    @SuppressWarnings("unchecked")
    public static <A> IAEItemStack packAEStackLong(A target, Item host) {
        FakeItemHandler handler = checkItem(host);
        return handler == null ? null : handler.packAEStackLong(target);
    }

    public static boolean isFakeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return HANDLERS.containsKey(stack.getItem().getClass());
    }

    private static FakeItemHandler<?, ?> checkItem(Item host) {
        Class<? extends Item> clazz = host.getClass();
        if (HANDLERS.containsKey(clazz)) {
            return HANDLERS.get(clazz);
        }
        return null;
    }
}
