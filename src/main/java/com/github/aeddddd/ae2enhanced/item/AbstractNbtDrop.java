package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.creativetab.CreativeTabs;

/**
 * NBT 编码假物品的抽象基类.
 * 封装通用构造、安全类型判断和默认 getSubItems 行为.
 *
 * 子类(ItemFluidDrop / ItemGasDrop)只需实现具体的 createStack / getStack 逻辑.
 */
public abstract class AbstractNbtDrop extends Item {

    protected AbstractNbtDrop(String name) {
        setRegistryName(AE2Enhanced.MOD_ID, name);
        setTranslationKey(AE2Enhanced.MOD_ID + "." + name);
        setCreativeTab(null);
    }

    /**
     * 安全判断 ItemStack 是否是指定类型的假物品.
     * 使用字符串类名比较,避免直接引用条件类(如 ItemGasDrop)导致 NoClassDefFoundError.
     *
     * @param stack            待判断的 ItemStack
     * @param expectedClassName 预期的完整类名(如 "com.github.aeddddd.ae2enhanced.item.ItemGasDrop")
     */
    public static boolean isDrop(ItemStack stack, String expectedClassName) {
        return !stack.isEmpty() && expectedClassName.equals(stack.getItem().getClass().getName());
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        // 默认不返回任何子类型,避免创造模式物品栏 / JEI 索引
    }
}
