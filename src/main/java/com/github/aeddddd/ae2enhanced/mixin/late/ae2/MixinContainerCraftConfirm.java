package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.core.sync.GuiBridge;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 合成确认 GUI 提交任务后,自动返回原终端.
 * 当原终端是 Omni Terminal 时,拦截返回标准无线终端的逻辑,改为打开 Omni Terminal.
 */
@Mixin(value = ContainerCraftConfirm.class, remap = false)
public class MixinContainerCraftConfirm {

    @Redirect(
        method = "startJob",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/Platform;openGUI(Lnet/minecraft/entity/player/EntityPlayer;ILappeng/core/sync/GuiBridge;Z)V"
        )
    )
    private void ae2enhanced$redirectOpenGUI(EntityPlayer player, int slot, GuiBridge gui, boolean isBauble) {
        Object target = ((AEBaseContainer) (Object) this).getTarget();
        if (target instanceof WirelessTerminalGuiObject && gui == GuiBridge.GUI_WIRELESS_TERM) {
            ItemStack stack = ((WirelessTerminalGuiObject) target).getItemStack();
            if (stack.getItem() instanceof ItemOmniWirelessTerminal) {
                player.openGui(AE2Enhanced.instance, GuiHandler.GUI_OMNI_TERMINAL, player.world, slot, isBauble ? 1 : 0, 0);
                return;
            }
        }
        Platform.openGUI(player, slot, gui, isBauble);
    }
}
