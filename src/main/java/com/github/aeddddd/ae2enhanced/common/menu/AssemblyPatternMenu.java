package com.github.aeddddd.ae2enhanced.common.menu;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import appeng.api.crafting.PatternDetailsHelper;

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.client.gui.GuiConstants;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 装配枢纽样板存储分页菜单。
 */
public class AssemblyPatternMenu extends AbstractContainerMenu {

    public static final int PATTERN_COLS = 17;
    public static final int PATTERN_ROWS = 6;
    public static final int PAGE_SIZE = PATTERN_COLS * PATTERN_ROWS;
    public static final int UPGRADE_SLOTS = AssemblyControllerBlockEntity.UPGRADE_SLOTS;

    private static final int PATTERN_X = 8;
    private static final int PATTERN_Y = 25;
    private static final int INV_X = 80;
    private static final int INV_Y = 145;
    private static final int HOTBAR_Y = 203;

    private final Inventory playerInventory;
    private final BlockPos controllerPos;
    private final ItemStackHandler itemHandler;
    private final int page;
    private final int patternSlotCount;

    public AssemblyPatternMenu(int id, Inventory inv, BlockPos pos, int page) {
        super(ModMenus.ASSEMBLY_PATTERN.get(), id);
        this.playerInventory = inv;
        this.controllerPos = pos;
        this.page = page;

        Level level = inv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AssemblyControllerBlockEntity controller) {
            this.itemHandler = controller.getItemHandler();
        } else {
            this.itemHandler = new ItemStackHandler(UPGRADE_SLOTS);
        }

        int startSlot = UPGRADE_SLOTS + page * PAGE_SIZE;
        int expectedTotalSlots = UPGRADE_SLOTS + getTotalPages() * PAGE_SIZE;
        int endSlot = Math.min(startSlot + PAGE_SIZE, expectedTotalSlots);
        this.patternSlotCount = Math.max(0, endSlot - startSlot);

        for (int i = 0; i < PAGE_SIZE; i++) {
            int row = i / PATTERN_COLS;
            int col = i % PATTERN_COLS;
            addSlot(new PatternSlot(itemHandler, i, startSlot, PATTERN_X + col * 18, PATTERN_Y + row * 18, this));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = 9 + row * 9 + col;
                addSlot(new Slot(playerInventory, index, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public static AssemblyPatternMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int page = buf.readVarInt();
        return new AssemblyPatternMenu(id, inv, pos, page);
    }

    public static void encodeExtra(FriendlyByteBuf buf, BlockPos pos, int page) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(page);
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Nullable
    public AssemblyControllerBlockEntity getController() {
        Level level = playerInventory.player.level();
        if (level == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(controllerPos);
        return be instanceof AssemblyControllerBlockEntity controller ? controller : null;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        AssemblyControllerBlockEntity controller = getController();
        if (controller == null) {
            return 1;
        }
        return Math.max(1, controller.getPatternPages());
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5) <= GuiConstants.CONTAINER_MAX_DISTANCE_SQR;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < PAGE_SIZE) {
                if (!this.moveItemStackTo(stack, PAGE_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stack, 0, PAGE_SIZE, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == moved.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return moved;
    }

    private static class PatternSlot extends SlotItemHandler {
        private final ItemStackHandler handler;
        private final int localIndex;
        private final int startSlot;
        private final AssemblyPatternMenu menu;

        PatternSlot(ItemStackHandler handler, int localIndex, int startSlot, int x, int y, AssemblyPatternMenu menu) {
            super(handler, localIndex, x, y);
            this.handler = handler;
            this.localIndex = localIndex;
            this.startSlot = startSlot;
            this.menu = menu;
        }

        private int getAbsoluteIndex() {
            return startSlot + localIndex;
        }

        @Override
        public boolean isActive() {
            return localIndex < menu.patternSlotCount;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            int absolute = getAbsoluteIndex();
            if (absolute < 0 || absolute >= handler.getSlots()) {
                return false;
            }
            if (stack.isEmpty()) {
                return true;
            }
            Level level = menu.playerInventory.player.level();
            return level != null && PatternDetailsHelper.decodePattern(stack, level) != null && handler.isItemValid(absolute, stack);
        }

        @Override
        public ItemStack getItem() {
            int absolute = getAbsoluteIndex();
            if (absolute < 0 || absolute >= handler.getSlots()) {
                return ItemStack.EMPTY;
            }
            return handler.getStackInSlot(absolute);
        }

        @Override
        public void set(ItemStack stack) {
            int absolute = getAbsoluteIndex();
            if (absolute < 0 || absolute >= handler.getSlots()) {
                return;
            }
            handler.setStackInSlot(absolute, stack);
            setChanged();
        }

        @Override
        public boolean mayPickup(Player player) {
            int absolute = getAbsoluteIndex();
            return absolute >= 0 && absolute < handler.getSlots();
        }

        @Override
        public int getMaxStackSize() {
            int absolute = getAbsoluteIndex();
            if (absolute < 0 || absolute >= handler.getSlots()) {
                return 0;
            }
            return handler.getSlotLimit(absolute);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(this.getMaxStackSize(), stack.getMaxStackSize());
        }
    }
}
