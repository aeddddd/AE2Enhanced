package com.github.aeddddd.ae2enhanced.util.fakeitem;

import net.minecraft.item.ItemStack;

/**
 * 源质假物品的安全工具类,不含任何 Thaumic Energistics 硬引用.
 *
 * <p>此类可被无条件加载的类(如终端 Mixin、Part 类)安全导入,
 * 因为所有方法均使用字符串比较与反射,不会在 Thaumic Energistics
 * 缺失时触发 {@link NoClassDefFoundError}.</p>
 */
public class FakeEssentiaSafe {

    private static final String ESSENTIA_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop";

    /**
     * 判断 ItemStack 是否是源质假物品(ItemEssentiaDrop).
     */
    public static boolean isEssentiaFakeItem(ItemStack stack) {
        return !stack.isEmpty() && ESSENTIA_DROP_CLASS.equals(stack.getItem().getClass().getName());
    }

    /**
     * 安全获取源质假物品的 aspect 标签.
     */
    public static String tryGetAspectTag(ItemStack stack) {
        if (!isEssentiaFakeItem(stack)) return null;
        try {
            Class<?> clazz = Class.forName(ESSENTIA_DROP_CLASS);
            return (String) clazz.getMethod("getAspectTag", ItemStack.class).invoke(null, stack);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 反射方法：从源质容器(IEssentiaContainerItem)转换为 ItemEssentiaDrop.
     * 供 Container / GhostIngredientTarget 调用,避免硬引用 Thaumcraft API.
     */
    public static ItemStack tryConvertContainerToFake(ItemStack held) {
        if (held == null || held.isEmpty()) return null;
        try {
            Class<?> containerItemClass = Class.forName("thaumcraft.api.aspects.IEssentiaContainerItem");
            if (!containerItemClass.isInstance(held.getItem())) return null;
            Object containerItem = held.getItem();
            Object aspectList = containerItemClass.getMethod("getAspects", ItemStack.class).invoke(containerItem, held);
            if (aspectList == null) return null;
            Object[] aspects = (Object[]) aspectList.getClass().getMethod("getAspects").invoke(aspectList);
            if (aspects == null || aspects.length == 0) return null;
            Object aspect = aspects[0];
            String aspectTag = (String) aspect.getClass().getMethod("getTag").invoke(aspect);
            Class<?> essentiaDropClass = Class.forName(ESSENTIA_DROP_CLASS);
            return (ItemStack) essentiaDropClass.getMethod("createStack", String.class, int.class)
                    .invoke(null, aspectTag, 1);
        } catch (Exception e) {
            return null;
        }
    }
}
