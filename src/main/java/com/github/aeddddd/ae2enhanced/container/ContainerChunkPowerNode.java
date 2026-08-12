package com.github.aeddddd.ae2enhanced.container;

import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChunkPowerNodeSync;
import com.github.aeddddd.ae2enhanced.tile.TileChunkPowerNode;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 区块供电节点 GUI 的服务端 Container.
 *
 * <p>不包含可交互槽位（仅玩家物品栏）。每 10 tick 向正在查看的玩家
 * 同步一次供电状态与目标列表.</p>
 */
public class ContainerChunkPowerNode extends Container {

    /** GUI 数据同步间隔（tick） */
    private static final int SYNC_INTERVAL = 10;

    private final TileChunkPowerNode tile;
    private int syncCooldown = 0;

    public ContainerChunkPowerNode(InventoryPlayer playerInventory, TileChunkPowerNode tile) {
        this.tile = tile;

        // 玩家物品栏（GUI 加高，整体下移）
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlotToContainer(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 140 + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            addSlotToContainer(new Slot(playerInventory, i, 8 + i * 18, 198));
        }
    }

    public TileChunkPowerNode getTile() {
        return tile;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (tile.getWorld() == null || tile.getWorld().isRemote) return;
        if (--syncCooldown > 0) return;
        syncCooldown = SYNC_INTERVAL;

        PacketChunkPowerNodeSync packet = tile.buildSyncPacket();
        for (IContainerListener listener : this.listeners) {
            if (listener instanceof EntityPlayerMP) {
                AE2Enhanced.network.sendTo(packet, (EntityPlayerMP) listener);
            }
        }
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return tile.getWorld().getTileEntity(tile.getPos()) == tile
                && playerIn.getDistanceSq(tile.getPos().getX() + 0.5, tile.getPos().getY() + 0.5,
                        tile.getPos().getZ() + 0.5) <= 64.0
                && Platform.hasPermissions(tile.getWorld(), tile.getPos(), playerIn);
    }

    @Override
    @Nonnull
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer playerIn, int index) {
        return ItemStack.EMPTY;
    }
}
