package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.client.gui.AEBaseGui;
import com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm;
import com.github.aeddddd.ae2enhanced.mixin.bridge.TerminalClickBridge;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：在标准 AE2 物品终端中拦截流体/气体/源质假物品的点击.
 * 实际逻辑委托给 {@link TerminalClickBridge}.
 */
@Mixin(value = AEBaseGui.class, remap = false, priority = 1099)
public class MixinGuiMEMonitorableClick {

    @Inject(method = "func_184098_a", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onHandleMouseClick(Slot slot, int slotId, int mouseButton, ClickType clickType, CallbackInfo ci) {
        // ae2fc 已加载时，标准 AE2 终端交给 ae2fc 处理；但 Omni Terminal 仍需走 AE2E 自己的桥接。
        if (Ae2fcCompat.AE2FC_LOADED && !((Object) this instanceof GuiOmniTerm)) {
            return;
        }

        if (TerminalClickBridge.onHandleMouseClick((AEBaseGui) (Object) this, slot, slotId, mouseButton, clickType)) {
            ci.cancel();
        }
    }
}
