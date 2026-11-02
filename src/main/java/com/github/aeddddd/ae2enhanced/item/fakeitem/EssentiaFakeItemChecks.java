package com.github.aeddddd.ae2enhanced.item.fakeitem;

import net.minecraft.item.ItemStack;

/**
 * 源质假物品的安全判断工具类.
 *
 * 本类不 import 任何第三方 mod 类(Thaumcraft / ThaumicEnergistics),
 * 仅使用字符串比较和反射,确保在缺少对应 mod 时不会触发 NoClassDefFoundError.
 *
 * 无条件配置中的 Mixin 和 Part 类应使用本类进行源质假物品判断,
 * 避免直接引用 {@link FakeEssentias}(其常量池包含 ThaumicEnergistics 类引用).
 */
public final class EssentiaFakeItemChecks {

    private static final String ESSENTIA_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop";

    private EssentiaFakeItemChecks() {}

    /**
     * 判断 ItemStack 是否是本 mod 的源质假物品.
     * 使用字符串比较而非直接引用 ItemEssentiaDrop 类.
     */
    public static boolean isEssentiaFakeItem(ItemStack stack) {
        return !stack.isEmpty() && ESSENTIA_DROP_CLASS.equals(stack.getItem().getClass().getName());
    }

    /**
     * 安全获取源质假物品的 aspect 标签.
     * 使用反射调用 ItemEssentiaDrop.getAspectTag,仅在确认是源质假物品后调用.
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
     * 获取源质容器(安瓿/源质罐)的容量.
     * 仅通过注册名字符串判断,不加载任何 Thaumcraft 类.
     * 硬编码容量:thaumcraft:phial=10,jar_normal/jar_void=250(与 ThE 默认一致).
     *
     * @return 容量;不支持的物品返回 0
     */
    public static int getEssentiaContainerCapacity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        net.minecraft.util.ResourceLocation rl = stack.getItem().getRegistryName();
        if (rl == null) return 0;
        String name = rl.toString();
        switch (name) {
            case "thaumcraft:phial":
                return 10;
            case "thaumcraft:jar_normal":
            case "thaumcraft:jar_void":
                return 250;
            default:
                return 0;
        }
    }

    /**
     * 反射方法：读取源质容器(IEssentiaContainerItem)中当前存储的 aspect 标签.
     *
     * @return 容器内第一个 aspect 的标签;容器为空或不支持时返回 null
     */
    public static String tryGetContainerAspectTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            Class<?> containerItemClass = Class.forName("thaumcraft.api.aspects.IEssentiaContainerItem");
            if (!containerItemClass.isInstance(stack.getItem())) return null;
            Object aspectList = containerItemClass.getMethod("getAspects", ItemStack.class).invoke(stack.getItem(), stack);
            if (aspectList == null) return null;
            Object[] aspects = (Object[]) aspectList.getClass().getMethod("getAspects").invoke(aspectList);
            if (aspects == null || aspects.length == 0) return null;
            return (String) aspects[0].getClass().getMethod("getTag").invoke(aspects[0]);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 反射方法：从源质容器(IEssentiaContainerItem)转换为 ItemEssentiaDrop.
     * 本方法不依赖 ThaumicEnergistics 类存在于常量池,全部通过反射访问.
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
