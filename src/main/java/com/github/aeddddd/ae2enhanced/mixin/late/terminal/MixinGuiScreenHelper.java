package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import com.github.aeddddd.ae2enhanced.integration.jei.JeiIngredientHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.input.IClickedIngredient;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * E2a：JEI 成分识别适配 —— GuiScreenHelper.
 * 处理通过 IAdvancedGuiHandler / IGlobalGuiHandler 获取的 ingredient,
 * 同样将假物品转换为 JEI 可识别的实际成分.
 *
 * 使用 targets 字符串代替类字面量,避免 Mixin 配置解析阶段提前触发
 * GuiScreenHelper 类加载(HEI 尚未初始化时会导致 ActualClassLoader 标记 invalid).
 */
@Mixin(targets = "mezz.jei.gui.GuiScreenHelper", remap = false)
public class MixinGuiScreenHelper {

    @WrapOperation(
        method = "getPluginsIngredientUnderMouse",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/gui/GuiScreenHelper;createClickedIngredient(Ljava/lang/Object;Lnet/minecraft/client/gui/inventory/GuiContainer;)Lmezz/jei/input/IClickedIngredient;"
        )
    )
    private <T> IClickedIngredient<T> ae2enhanced$wrapClickedIngredient(Object instance, T ingredient, GuiContainer guiContainer, Operation<IClickedIngredient<T>> original) {
        return original.call(instance, JeiIngredientHelper.wrapIngredient(ingredient), guiContainer);
    }
}
