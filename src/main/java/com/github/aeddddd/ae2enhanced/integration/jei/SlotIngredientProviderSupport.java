package com.github.aeddddd.ae2enhanced.integration.jei;

import net.minecraft.item.ItemStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * HEI ≥ 4.34.0 官方 ISlotIngredientProvider API 的注册入口.
 *
 * 替代旧版 MixinGuiContainerWrapper 的功能：当任意 GUI 的 Slot 中放置了
 * AE2Enhanced 的假物品(流体/气体/源质 drop)时,向 HEI 报告其真实成分,
 * 使 R/U 查询、书签、tooltip 作用于实际成分而非占位物品.
 *
 * 编译期依赖为官方 JEI 4.16 API,不含 ISlotIngredientProvider,
 * 因此通过 JDK 动态代理 + 反射注册,不产生任何编译期/常量池硬引用.
 * 仅在 {@link com.github.aeddddd.ae2enhanced.util.compat.HeiCompat#HAS_SLOT_INGREDIENT_PROVIDER}
 * 为 true 时调用.
 */
public final class SlotIngredientProviderSupport {

    private SlotIngredientProviderSupport() {
    }

    /**
     * 以 GuiContainer 基类注册全局提供者,保持与旧 Mixin 相同的全局生效范围.
     * HEI 的查找逻辑先按精确类匹配,再退化为 isInstance 遍历,基类注册可覆盖所有 GUI.
     */
    public static void register(mezz.jei.api.IModRegistry registry) throws ReflectiveOperationException {
        ClassLoader cl = SlotIngredientProviderSupport.class.getClassLoader();
        Class<?> providerInterface = Class.forName("mezz.jei.api.gui.ISlotIngredientProvider", false, cl);
        Method registerMethod = mezz.jei.api.IModRegistry.class.getMethod(
                "addSlotIngredientProvider", Class.class, providerInterface);
        Object provider = Proxy.newProxyInstance(cl, new Class<?>[]{ providerInterface }, new SlotIngredientHandler());
        registerMethod.invoke(registry, net.minecraft.client.gui.inventory.GuiContainer.class, provider);
    }

    /**
     * ISlotIngredientProvider 的动态代理实现.
     * 接口方法签名: Object getSlotIngredient(GuiContainer guiContainer, Slot slot, ItemStack stack)
     * 返回 null 表示使用原始 stack.
     */
    private static final class SlotIngredientHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("getSlotIngredient".equals(method.getName()) && args != null && args.length == 3
                    && args[2] instanceof ItemStack) {
                ItemStack stack = (ItemStack) args[2];
                Object wrapped = JeiIngredientHelper.wrapIngredient(stack);
                // 未转换(非假物品)时返回 null,让 HEI 使用原始 stack
                return wrapped == stack ? null : wrapped;
            }
            // Object 方法(toString/equals/hashCode)兜底
            if ("toString".equals(method.getName())) {
                return "AE2Enhanced$SlotIngredientProvider";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            return null;
        }
    }
}
