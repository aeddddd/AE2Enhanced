package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import com.github.aeddddd.ae2enhanced.client.gui.planview.IPlanViewHost;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GuiScreen 层的计划视图事件转发: updateScreen.
 * <p>供搜索框光标闪烁. 注意: keyTyped 不能在 GuiScreen 层挂钩——
 * GuiContainer 覆写了 keyTyped 且不调用 super, 容器类 GUI 的按键事件
 * 永远到不了 GuiScreen.keyTyped; 按键分发见 {@link MixinGuiContainerPlanHooks}.</p>
 * <p>目标为 Minecraft 原生类, 使用默认 remap=true, 必须 early 注册
 * (Cleanroom 下 late 注册原生类目标会被静默拒绝).</p>
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreenPlanHooks {

    @Inject(method = "updateScreen", at = @At("HEAD"))
    private void ae2enhanced$dispatchPlanUpdateScreen(CallbackInfo ci) {
        if ((Object) this instanceof IPlanViewHost) {
            ((IPlanViewHost) (Object) this).ae2enhanced$planUpdateScreen();
        }
    }
}
