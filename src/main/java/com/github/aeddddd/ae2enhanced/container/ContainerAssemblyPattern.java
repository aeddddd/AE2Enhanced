package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;

public class ContainerAssemblyPattern extends Container {

    private static final int PATTERN_X = 10;
    private static final int PATTERN_Y = 24;
    private static final int INV_X = 89;
    private static final int INV_Y = 152;
    private static final int HOTBAR_Y = 210;

    private final TileAssemblyController tile;
    private final int page;
    private final int patternSlotCount;

    public ContainerAssemblyPattern(IInventory playerInv, TileAssemblyController tile, int page, int patternPages) {
        this.tile = tile;
        this.page = page;
        IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);

        int startSlot = TileAssemblyController.UPGRADE_SLOTS
            + page * TileAssemblyController.PATTERN_SLOTS_PER_PAGE;
        int expectedTotalSlots = TileAssemblyController.UPGRADE_SLOTS
            + patternPages * TileAssemblyController.PATTERN_SLOTS_PER_PAGE;
        int endSlot = Math.min(startSlot + TileAssemblyController.PATTERN_SLOTS_PER_PAGE,
            expectedTotalSlots);

        this.patternSlotCount = endSlot - startSlot;

        // 使用 PatternInventory 代理 IItemHandler，让原版 Slot 正常工作
        PatternInventory patternInv = new PatternInventory(handler, startSlot, this.patternSlotCount);
        for (int i = 0; i < this.patternSlotCount; i++) {
            int row = i / 16;
            int col = i % 16;
            this.addSlotToContainer(new Slot(patternInv, i,
                PATTERN_X + col * 20, PATTERN_Y + row * 20));
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

            int patternEnd = this.patternSlotCount;
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

    /**
     * 将 IItemHandler 的指定槽位区间代理为 IInventory，供原版 Slot 使用。
     * 避免使用 null inventory 导致 Slot 默认方法（decrStackSize 等）NPE。
     * setInventorySlotContents 使用 setStackInSlot 实现替换语义，避免 insertItem 的合并副作用。
     */
    private static class PatternInventory implements IInventory {

        @Override
        @Nonnull
        public String getName() {
            return "pattern_inventory";
        }

        @Override
        public boolean hasCustomName() {
            return false;
        }

        @Override
        @Nonnull
        public net.minecraft.util.text.ITextComponent getDisplayName() {
            return new net.minecraft.util.text.TextComponentString(getName());
        }
        private final IItemHandler handler;
        private final int startSlot;
        private final int size;

        PatternInventory(IItemHandler handler, int startSlot, int size) {
            this.handler = handler;
            this.startSlot = startSlot;
            this.size = size;
        }

        private int toHandlerIndex(int index) {
            return startSlot + index;
        }

        @Override
        public int getSizeInventory() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < size; i++) {
                if (!getStackInSlot(i).isEmpty()) return false;
            }
            return true;
        }

        @Override
        @Nonnull
        public ItemStack getStackInSlot(int index) {
            if (index < 0 || index >= size) return ItemStack.EMPTY;
            return handler.getStackInSlot(toHandlerIndex(index));
        }

        @Override
        @Nonnull
        public ItemStack decrStackSize(int index, int count) {
            if (index < 0 || index >= size) return ItemStack.EMPTY;
            return handler.extractItem(toHandlerIndex(index), count, false);
        }

        @Override
        @Nonnull
        public ItemStack removeStackFromSlot(int index) {
            if (index < 0 || index >= size) return ItemStack.EMPTY;
            return handler.extractItem(toHandlerIndex(index), Integer.MAX_VALUE, false);
        }

        @Override
        public void setInventorySlotContents(int index, @Nonnull ItemStack stack) {
            if (index < 0 || index >= size) return;
            int handlerIndex = toHandlerIndex(index);
            if (handler instanceof IItemHandlerModifiable) {
                ((IItemHandlerModifiable) handler).setStackInSlot(handlerIndex,
                    stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            } else {
                handler.extractItem(handlerIndex, Integer.MAX_VALUE, false);
                if (!stack.isEmpty()) {
                    handler.insertItem(handlerIndex, stack.copy(), false);
                }
            }
        }

        @Override
        public int getInventoryStackLimit() {
            return 1;
        }

        @Override
        public void markDirty() {
            // ItemStackHandler 内部已有 onContentsChanged 机制，此处无需额外操作
        }

        @Override
        public boolean isUsableByPlayer(@Nonnull EntityPlayer player) {
            return true;
        }

        @Override
        public void openInventory(@Nonnull EntityPlayer player) {
        }

        @Override
        public void closeInventory(@Nonnull EntityPlayer player) {
        }

        @Override
        public boolean isItemValidForSlot(int index, @Nonnull ItemStack stack) {
            if (index < 0 || index >= size) return false;
            return handler.isItemValid(toHandlerIndex(index), stack);
        }

        @Override
        public int getField(int id) {
            return 0;
        }

        @Override
        public void setField(int id, int value) {
        }

        @Override
        public int getFieldCount() {
            return 0;
        }

        @Override
        public void clear() {
            for (int i = 0; i < size; i++) {
                setInventorySlotContents(i, ItemStack.EMPTY);
            }
        }
    }
}
