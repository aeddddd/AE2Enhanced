package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import com.github.aeddddd.ae2enhanced.client.gui.terminal.TerminalTooltipBridge;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiContainer.class, priority = 1099)
public abstract class MixinGuiContainerTooltip {
    @Inject(method = "func_191948_b", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onRenderHoveredToolTip(int mouseX, int mouseY, CallbackInfo ci) {
        GuiContainer screen = (GuiContainer) (Object) this;
        if (TerminalTooltipBridge.onRenderSlotTooltip(screen, screen.getSlotUnderMouse(), mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
