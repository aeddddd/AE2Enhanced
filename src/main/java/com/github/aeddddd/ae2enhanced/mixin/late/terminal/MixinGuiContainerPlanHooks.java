package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import com.github.aeddddd.ae2enhanced.client.gui.planview.IPlanViewHost;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

/**
 * GuiContainer 层的计划视图事件转发: keyTyped.
 * <p>必须挂在 GuiContainer 而非 GuiScreen: GuiContainer 覆写了 keyTyped
 * (ESC/背包键关闭界面)且不调用 super, 容器类 GUI(如 GuiCraftingCPU/
 * GuiCraftingStatus)的按键事件不会到达 GuiScreen.keyTyped.
 * 自行覆写 keyTyped 的 GUI(如 GuiCraftConfirm)已在自身 mixin 中先行消费,
 * 到达此处时接口实现会按焦点状态自行忽略, 不会重复处理.</p>
 * <p>目标为 Minecraft 原生类, 使用默认 remap=true, 必须 early 注册
 * (Cleanroom 下 late 注册原生类目标会被静默拒绝).</p>
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainerPlanHooks {

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$dispatchPlanKeyTyped(char typedChar, int keyCode, CallbackInfo ci)
            throws IOException {
        if ((Object) this instanceof IPlanViewHost
                && ((IPlanViewHost) (Object) this).ae2enhanced$planKeyTyped(typedChar, keyCode)) {
            ci.cancel();
        }
    }
}
