package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.client.gui.implementations.GuiMEMonitorable;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为所有 AE2 标准终端(GuiMEMonitorable 及其子类,不含覆盖 keyTyped 的 GuiOmniTerm)
 * 添加 F 键 JEI 搜索支持.
 *
 * <p>当用户在终端 GUI 中按下 F 键,且搜索栏未聚焦时,将 JEI 悬停物品(物品列表
 * 或收藏栏)的名称填入终端搜索栏并立即刷新.</p>
 */
@Mixin(value = GuiMEMonitorable.class, remap = false)
public class MixinGuiMEMonitorableKeyHandler {

    @Inject(
            method = "func_73869_a",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ae2enhanced$onKeyTyped(char character, int key, CallbackInfo ci) {
        if (key == Keyboard.KEY_F) {
            GuiMEMonitorable gui = (GuiMEMonitorable) (Object) this;
            int mouseX = 0, mouseY = 0;
            try {
                java.lang.reflect.Field fmx = GuiMEMonitorable.class.getDeclaredField("currentMouseX");
                fmx.setAccessible(true);
                mouseX = fmx.getInt(gui);
                java.lang.reflect.Field fmy = GuiMEMonitorable.class.getDeclaredField("currentMouseY");
                fmy.setAccessible(true);
                mouseY = fmy.getInt(gui);
            } catch (Exception ignored) {
            }
            if (JEISearchKeyHandler.performSearch(gui, mouseX, mouseY)) {
                ci.cancel();
            }
        }
    }
}
