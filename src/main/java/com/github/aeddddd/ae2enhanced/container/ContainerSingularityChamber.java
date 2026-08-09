package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.chamber.LongItemStore;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import com.github.aeddddd.ae2enhanced.item.ItemVirtualParallelCard;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChamberCatalog;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChamberSync;
import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 奇点处理仓 Container.
 * 真实槽位仅卡片（1 并行 + 4 加速）与玩家物品栏；
 * 原料缓存在 Tile 内部（long 数量）,shift-click 玩家物品直接倒入缓存,
 * 缓存内容与状态通过 {@link PacketChamberSync} 每 10 tick 同步,
 * 配方目录在打开时通过 {@link PacketChamberCatalog} 一次性下发.
 */
public class ContainerSingularityChamber extends Container {

    public static final int SLOT_PARALLEL = 0;
    public static final int SLOT_SPEED_START = 1;
    public static final int PLAYER_INV_START = 5;

    private final TileSingularityChamber tile;
    private int syncCounter = 0;

    public ContainerSingularityChamber(InventoryPlayer playerInv, TileSingularityChamber tile) {
        this.tile = tile;

        // 并行卡槽
        addSlotToContainer(new SlotItemHandler(tile.getCardSlots(), TileSingularityChamber.SLOT_PARALLEL, 26, 108) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack.getItem() instanceof ItemVirtualParallelCard;
            }
        });
        // 加速卡槽 ×4
        for (int i = 0; i < 4; i++) {
            addSlotToContainer(new SlotItemHandler(tile.getCardSlots(), 1 + i, 44 + i * 18, 108) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return stack.getItem() instanceof ItemUpgradeCard
                            && stack.getMetadata() == ItemUpgradeCard.META_SPEED;
                }
            });
        }

        // 玩家物品栏
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 26 + col * 18, 134 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 26 + col * 18, 192));
        }

        // 打开时下发配方目录（服务端创建容器时玩家已知）
        if (playerInv.player instanceof EntityPlayerMP) {
            AE2Enhanced.network.sendTo(PacketChamberCatalog.build(), (EntityPlayerMP) playerInv.player);
        }
    }

    public TileSingularityChamber getTile() {
        return tile;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }
        if (++syncCounter < 10) {
            return;
        }
        syncCounter = 0;

        PacketChamberSync packet = new PacketChamberSync(
                tile.getPos(), tile.getEnergy(),
                tile.getParallelChannels(), tile.getUsedChannels(), tile.getActiveJobCount(),
                tile.getRedstoneMode().ordinal(), tile.getDisabledRecipes());
        for (LongItemStore.Entry entry : tile.getInputStore().getEntries()) {
            packet.addInput(entry.getTemplate(), entry.getCount());
        }
        for (LongItemStore.Entry entry : tile.getOutputStore().getEntries()) {
            packet.addOutput(entry.getTemplate(), entry.getCount());
        }
        for (IContainerListener listener : listeners) {
            if (listener instanceof EntityPlayerMP) {
                AE2Enhanced.network.sendTo(packet, (EntityPlayerMP) listener);
            }
        }
    }

    /**
     * shift-click：卡片进入卡槽,其余玩家物品全部倒入原料缓存.
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        if (index >= PLAYER_INV_START) {
            boolean isCard = stack.getItem() instanceof ItemVirtualParallelCard
                    || (stack.getItem() instanceof ItemUpgradeCard
                    && stack.getMetadata() == ItemUpgradeCard.META_SPEED);
            if (isCard) {
                if (!mergeItemStack(stack, SLOT_PARALLEL, SLOT_SPEED_START + 4, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 倒入原料缓存
                long remaining = tile.insertInput(stack, stack.getCount());
                int inserted = stack.getCount() - (int) remaining;
                if (inserted <= 0) {
                    return ItemStack.EMPTY;
                }
                stack.shrink(inserted);
            }
        } else {
            if (!mergeItemStack(stack, PLAYER_INV_START, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return original;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.getWorld() != null
                && tile.getWorld().getTileEntity(tile.getPos()) == tile
                && player.getDistanceSq(tile.getPos().add(0.5, 0.5, 0.5)) <= 64.0;
    }
}
