package com.github.aeddddd.ae2enhanced.container;

import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngSlot;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * F1a：无线频道发生器 GUI 的 Container.
 */
public class ContainerWirelessChannelTransmitter extends AEBaseContainer {

    private final TileWirelessChannelTransmitter tile;

    public ContainerWirelessChannelTransmitter(InventoryPlayer ip, TileWirelessChannelTransmitter tile) {
        super(ip, tile, null);
        this.tile = tile;

        this.addSlotToContainer(new AppEngSlot(tile.getInventory(), TileWirelessChannelTransmitter.SLOT_CARD, 81, 48));

        this.bindPlayerInventory(ip, 0, 84);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return Platform.hasPermissions(this.getTileEntity().getWorld(), this.getTileEntity().getPos(), player);
    }
}
