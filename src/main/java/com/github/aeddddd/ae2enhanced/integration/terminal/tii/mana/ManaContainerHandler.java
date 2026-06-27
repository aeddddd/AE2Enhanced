package com.github.aeddddd.ae2enhanced.integration.terminal.tii.mana;

import appeng.api.networking.security.IActionSource;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.api.IContainerHandler;

import java.lang.reflect.Method;

/**
 * TII Botania Mana 容器处理器.
 * <p>
 * 通过反射访问 {@code vazkii.botania.api.mana.IManaItem},避免无条件加载 Botania 类.
 * 若 Botania 未安装,所有方法均返回空/0.
 * </p>
 */
public class ManaContainerHandler implements IContainerHandler {

    private static final Class<?> IMANA_ITEM;
    private static final Method GET_MANA;
    private static final Method GET_MAX_MANA;
    private static final Method ADD_MANA;

    static {
        Class<?> clazz = null;
        Method getMana = null;
        Method getMaxMana = null;
        Method addMana = null;
        try {
            clazz = Class.forName("vazkii.botania.api.mana.IManaItem");
            getMana = clazz.getMethod("getMana", ItemStack.class);
            getMaxMana = clazz.getMethod("getMaxMana", ItemStack.class);
            addMana = clazz.getMethod("addMana", ItemStack.class, int.class);
        } catch (Throwable ignored) {
            // Botania 未安装或接口结构不符,静默回退
        }
        IMANA_ITEM = clazz;
        GET_MANA = getMana;
        GET_MAX_MANA = getMaxMana;
        ADD_MANA = addMana;
    }

    @Override
    public boolean canHandle(ItemStack container) {
        return IMANA_ITEM != null
                && !container.isEmpty()
                && IMANA_ITEM.isInstance(container.getItem());
    }

    @Override
    public long getStoredAmount(ItemStack container) {
        if (GET_MANA == null) {
            return 0;
        }
        try {
            Object result = GET_MANA.invoke(container.getItem(), container);
            return result instanceof Number ? ((Number) result).longValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public long getMaxCapacity(ItemStack container) {
        if (GET_MAX_MANA == null) {
            return 0;
        }
        try {
            Object result = GET_MAX_MANA.invoke(container.getItem(), container);
            return result instanceof Number ? ((Number) result).longValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public long extract(ItemStack container, long amount, IActionSource source) {
        // Botania IManaItem 没有独立的 extract 方法,通过 addMana 传入负值实现
        if (ADD_MANA == null) {
            return 0;
        }
        long stored = getStoredAmount(container);
        long toExtract = Math.min(amount, stored);
        if (toExtract <= 0) {
            return 0;
        }
        try {
            ADD_MANA.invoke(container.getItem(), container, -(int) Math.min(toExtract, Integer.MAX_VALUE));
            return toExtract;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public long inject(ItemStack container, long amount, IActionSource source) {
        if (ADD_MANA == null) {
            return 0;
        }
        long capacity = getMaxCapacity(container);
        long stored = getStoredAmount(container);
        long toInject = Math.min(amount, capacity - stored);
        if (toInject <= 0) {
            return 0;
        }
        try {
            ADD_MANA.invoke(container.getItem(), container, (int) Math.min(toInject, Integer.MAX_VALUE));
            return toInject;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public String getContainerDisplayName(ItemStack container) {
        return container.getDisplayName();
    }

    @Override
    public ItemStack getEmptyContainer() {
        return ItemStack.EMPTY;
    }
}
