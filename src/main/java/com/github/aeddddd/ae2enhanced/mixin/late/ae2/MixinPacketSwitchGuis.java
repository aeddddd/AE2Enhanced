package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.container.AEBaseContainer;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦截 GUI 切换包(PacketSwitchGuis).
 * 当玩家从合成界面点击返回按钮时,如果原终端是 Omni Terminal,
 * 则拦截返回标准无线终端的逻辑,改为打开 Omni Terminal.
 */
@Mixin(value = PacketSwitchGuis.class, remap = false)
public class MixinPacketSwitchGuis {

    @Redirect(
        method = "serverPacketData",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/Platform;openGUI(Lnet/minecraft/entity/player/EntityPlayer;ILappeng/core/sync/GuiBridge;Z)V"
        )
    )
    private void ae2enhanced$redirectOpenGUI(EntityPlayer player, int slot, GuiBridge gui, boolean isBauble) {
        Container c = player.openContainer;
        if (c instanceof AEBaseContainer && gui == GuiBridge.GUI_WIRELESS_TERM) {
            Object target = ((AEBaseContainer) c).getTarget();
            if (target instanceof WirelessTerminalGuiObject) {
                ItemStack stack = ((WirelessTerminalGuiObject) target).getItemStack();
                if (stack.getItem() instanceof ItemOmniWirelessTerminal) {
                    player.openGui(AE2Enhanced.instance, GuiHandler.GUI_OMNI_TERMINAL, player.world, slot, isBauble ? 1 : 0, 0);
                    return;
                }
            }
        }
        Platform.openGUI(player, slot, gui, isBauble);
    }
}
