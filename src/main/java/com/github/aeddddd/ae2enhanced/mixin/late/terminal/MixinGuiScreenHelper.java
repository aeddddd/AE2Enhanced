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
    private <T> IClickedIngredient<T> ae2enhanced$wrapClickedIngredient(
            mezz.jei.gui.GuiScreenHelper instance, T ingredient, GuiContainer guiContainer,
            Operation<IClickedIngredient<T>> original) {
        // 第一个参数必须是目标方法的"接收者类型"（MixinExtras 会校验，类型写宽了直接注入失败：
        // 历史 bug 写成 Object → InvalidInjectionException "unexpected argument type java.lang.Object
        // at index 0, expected mezz.jei.gui.GuiScreenHelper"）。
        // 这里用全限定名而不是 import，语义等价：@Mixin(targets=...) 只是避免配置解析阶段提前解析该类，
        // 方法描述符里的引用在 mixin 真正应用（= GuiScreenHelper 正在被加载）时才解析。
        return original.call(instance, JeiIngredientHelper.wrapIngredient(ingredient), guiContainer);
    }
}
