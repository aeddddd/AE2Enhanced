package com.github.aeddddd.ae2enhanced.terminal;

import appeng.client.gui.AEBaseGui;
import appeng.client.me.SlotME;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;

/**
 * 资源终端点击上下文.
 */
public class ResourceClickContext {

    public final AEBaseGui gui;
    public final SlotME slot;
    public final int slotId;
    public final int mouseButton;
    public final ClickType clickType;
    public final EntityPlayer player;

    public ResourceClickContext(AEBaseGui gui, SlotME slot, int slotId, int mouseButton, ClickType clickType) {
        this.gui = gui;
        this.slot = slot;
        this.slotId = slotId;
        this.mouseButton = mouseButton;
        this.clickType = clickType;
        this.player = gui.mc.player;
    }
}
