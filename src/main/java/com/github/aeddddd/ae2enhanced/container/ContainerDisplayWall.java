package com.github.aeddddd.ae2enhanced.container;

import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotFake;
import appeng.helpers.InventoryAction;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.tile.TileDisplayPanel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * 趋势显示幕墙配置 GUI 的 Container.
 *
 * <p>8 个假物品槽位(支持普通物品与流体容器→FluidDrop 转换),
 * 所有配置操作通过 PacketDisplayAction 直接作用于 master TE.</p>
 */
public class ContainerDisplayWall extends AEBaseContainer {

    private final TileDisplayPanel tile;

    public ContainerDisplayWall(InventoryPlayer ip, TileDisplayPanel tile) {
        super(ip, tile, null);
        this.tile = tile;
        for (int i = 0; i < TileDisplayPanel.MAX_TRACKED; i++) {
            this.addSlotToContainer(new SlotFake(tile.getConfigInv(), i, 9, 19 + i * 18));
        }
        this.bindPlayerInventory(ip, 0, 174);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isFormed() && tile.isMasterRole()
                && Platform.hasPermissions(tile.getWorld(), tile.getPos(), player);
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        // 流体容器 → FluidDrop 假物品转换
        if (slot >= 0 && slot < this.inventorySlots.size()) {
            Slot s = this.inventorySlots.get(slot);
            if (s instanceof SlotFake) {
                ItemStack held = player.inventory.getItemStack();
                if (!held.isEmpty() && (action == InventoryAction.PICKUP_OR_SET_DOWN
                        || action == InventoryAction.SPLIT_OR_PLACE_SINGLE)) {
                    ItemStack fake = tryConvertFluidToFake(held);
                    if (fake != null && !fake.isEmpty()) {
                        s.putStack(fake);
                        return;
                    }
                }
            }
        }
        super.doAction(player, action, slot, id);
    }

    private static ItemStack tryConvertFluidToFake(ItemStack held) {
        if (held.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            IFluidHandlerItem fh = held.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (fh != null) {
                FluidStack drained = fh.drain(Integer.MAX_VALUE, false);
                if (drained != null && drained.amount > 0) {
                    return ItemFluidDrop.createStack(drained);
                }
            }
        }
        return null;
    }

    public TileDisplayPanel getTile() {
        return tile;
    }
}
