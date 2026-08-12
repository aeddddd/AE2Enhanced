package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import com.github.aeddddd.ae2enhanced.integration.jei.JeiIngredientHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.input.GuiContainerWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.awt.Rectangle;

/**
 * E2a：JEI 成分识别适配 —— GuiContainerWrapper.
 * 当鼠标悬停在 AE2 终端的流体/气体/源质假物品上时,将假物品 ItemStack
 * 转换为对应的 FluidStack / GasStack / crystal essence ItemStack,
 * 使 JEI 的 R/U 查询能够正确识别.
 *
 * 对齐 ae2fc 实现：使用 @Mixin(value) 类字面量 + 实例方法 handler.
 *
 * @deprecated 旧版 HEI (≤4.33.x) 兼容回退.HEI 4.34.0 重构了目标方法
 *             (新增 GuiScreenHelper.getSlotIngredient 调用),本 Mixin 在新版上
 *             会生成非法字节码(VerifyError),因此由 LateMixinPlugin 在新版 HEI 下
 *             跳过应用,改由官方 ISlotIngredientProvider API 实现
 *             (见 SlotIngredientProviderSupport).将在未来版本中移除.
 */
@Mixin(value = GuiContainerWrapper.class, remap = false)
public class MixinGuiContainerWrapper {

    @WrapOperation(
        method = "getIngredientUnderMouse",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/input/ClickedIngredient;create(Ljava/lang/Object;Ljava/awt/Rectangle;)Lmezz/jei/input/ClickedIngredient;"
        )
    )
    private <V> ClickedIngredient<V> ae2enhanced$wrapClickedIngredient(V ingredient, Rectangle area, Operation<ClickedIngredient<V>> original) {
        return original.call(JeiIngredientHelper.wrapIngredient(ingredient), area);
    }
}
