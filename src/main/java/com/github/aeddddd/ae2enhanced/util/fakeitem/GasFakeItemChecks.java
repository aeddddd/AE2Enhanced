package com.github.aeddddd.ae2enhanced.util.fakeitem;

import net.minecraft.item.ItemStack;

/**
 * 气体假物品的安全工具类,不含任何 Mekanism / MekanismEnergistics 硬引用.
 *
 * <p>此类可被无条件加载的类(如终端 Mixin、Part 类)安全导入,
 * 因为所有方法均使用字符串比较与反射,不会在气体相关 mod
 * 缺失时触发 {@link NoClassDefFoundError}.</p>
 */
public class GasFakeItemChecks {

    private static final String GAS_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemGasDrop";

    /**
     * 判断 ItemStack 是否是气体假物品(ItemGasDrop).
     */
    public static boolean isGasFakeItem(ItemStack stack) {
        return !stack.isEmpty() && GAS_DROP_CLASS.equals(stack.getItem().getClass().getName());
    }

    /**
     * 安全获取气体假物品的气体注册名.
     * 直接从 NBT 读取,不加载 ItemGasDrop 类.
     */
    public static String tryGetGasName(ItemStack stack) {
        if (!isGasFakeItem(stack)) return null;
        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.getString("GasName") : null;
    }
}
