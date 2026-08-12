package com.github.aeddddd.ae2enhanced.util.compat;

/**
 * HEI (HadEnoughItems) 版本兼容性检测.
 *
 * HEI 4.34.0 (PR #230) 新增了官方 API {@code mezz.jei.api.gui.ISlotIngredientProvider},
 * 同时重构了 GuiContainerWrapper.getIngredientUnderMouse 的内部结构,
 * 导致旧版针对 ClickedIngredient.create 的 MixinExtras WrapOperation 生成非法字节码
 * (VerifyError: Bad type on operand stack).
 *
 * 因此：
 * - HEI ≥ 4.34.0：通过官方 API 注册 slot 成分提供者,不再应用相关 Mixin.
 * - HEI ≤ 4.33.x：回退到 Mixin 方案(计划在未来版本中移除).
 *
 * 使用 getResource 检测而非 Class.forName,避免在 Mixin 配置解析阶段
 * 提前触发 JEI 内部类加载(CleanroomMC ActualClassLoader 会将其标记为 invalid).
 */
public final class HeiCompat {

    /** HEI 是否提供 ISlotIngredientProvider API (HEI ≥ 4.34.0). */
    public static final boolean HAS_SLOT_INGREDIENT_PROVIDER;

    static {
        boolean found = false;
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = HeiCompat.class.getClassLoader();
            }
            found = cl != null && cl.getResource("mezz/jei/api/gui/ISlotIngredientProvider.class") != null;
        } catch (Throwable ignored) {
            // 检测失败时按旧版处理,走 Mixin 回退
        }
        HAS_SLOT_INGREDIENT_PROVIDER = found;
    }

    private HeiCompat() {
    }
}
