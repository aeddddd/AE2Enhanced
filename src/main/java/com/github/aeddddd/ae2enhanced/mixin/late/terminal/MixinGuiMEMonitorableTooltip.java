package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.client.gui.implementations.GuiMEMonitorable;
import com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm;
import com.github.aeddddd.ae2enhanced.mixin.bridge.TerminalTooltipBridge;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：在标准 AE2 物品终端中渲染流体/气体/源质假物品的 tooltip.
 * 实际逻辑委托给 {@link TerminalTooltipBridge}.
 */
@Mixin(value = GuiMEMonitorable.class, remap = false, priority = 1099)
public abstract class MixinGuiMEMonitorableTooltip {

    @Inject(method = "func_191948_b", at = @At("HEAD"), cancellable = true)
    public void ae2enhanced$onRenderHoveredToolTip(int mouseX, int mouseY, CallbackInfo ci) {
        // ae2fc 已加载时，标准 AE2 终端交给 ae2fc 处理；但 Omni Terminal 仍需走 AE2E 自己的桥接。
        if (Ae2fcCompat.AE2FC_LOADED && !((Object) this instanceof GuiOmniTerm)) {
            return;
        }

        GuiContainer screen = (GuiContainer) (Object) this;
        Slot slot = screen.getSlotUnderMouse();
        if (TerminalTooltipBridge.onRenderHoveredToolTip(screen, slot, mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
