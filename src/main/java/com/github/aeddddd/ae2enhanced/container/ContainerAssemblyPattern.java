package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerAssemblyPattern extends Container {

    private static final int PATTERN_X = 10;
    private static final int PATTERN_Y = 24;
    private static final int INV_X = 89;
    private static final int INV_Y = 152;
    private static final int HOTBAR_Y = 210;

    private final TileAssemblyController tile;
    private final int page;

    public ContainerAssemblyPattern(IInventory playerInv, TileAssemblyController tile, int page) {
        this.tile = tile;
        // 页码边界保护
        int maxPage = tile.getPatternPages() - 1;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;
        this.page = page;
        IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);

        int startSlot = TileAssemblyController.UPGRADE_SLOTS
            + page * TileAssemblyController.PATTERN_SLOTS_PER_PAGE;
        int endSlot = Math.min(startSlot + TileAssemblyController.PATTERN_SLOTS_PER_PAGE,
            TileAssemblyController.UPGRADE_SLOTS + tile.getPatternSlotCount());

        // 样板槽：当前页 16×6=96 槽
        for (int i = startSlot; i < endSlot; i++) {
            int localIndex = i - startSlot;
            int row = localIndex / 16;
            int col = localIndex % 16;
            this.addSlotToContainer(new SlotItemHandler(handler, i,
                PATTERN_X + col * 20, PATTERN_Y + row * 20) {
                @Override
                public int getItemStackLimit(ItemStack stack) {
                    return 1;
                }

                @Override
                public int getSlotStackLimit() {
                    return 1;
                }
            });
        }

        // 玩家背包 3行×9列
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInv, col + row * 9 + 9,
                    INV_X + col * 18, INV_Y + row * 18));
            }
        }

        // 玩家快捷栏
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInv, col,
                INV_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return tile.getWorld().getTileEntity(tile.getPos()) == tile
                && playerIn.getDistanceSq(tile.getPos()) <= 64.0;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();

            int patternEnd = TileAssemblyController.PATTERN_SLOTS_PER_PAGE;
            int playerStart = patternEnd;
            int playerEnd = playerStart + 36;

            if (index < patternEnd) {
                // 从样板槽移到玩家背包
                if (!this.mergeItemStack(itemstack1, playerStart, playerEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 从玩家背包移到样板槽
                if (!this.mergeItemStack(itemstack1, 0, patternEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }
        return itemstack;
    }

    public TileAssemblyController getTile() {
        return tile;
    }

    public int getPage() {
        return page;
    }
}
