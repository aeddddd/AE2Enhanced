package com.github.aeddddd.ae2enhanced.client.gui;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import appeng.api.crafting.PatternDetailsHelper;

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.AssemblyPagePacket;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 装配枢纽控制器菜单容器。
 * <p>包含 6 个升级卡槽与 17×6 的样板槽分页视图。</p>
 */
public class AssemblyMenu extends AbstractContainerMenu {

    public static final int PATTERN_COLS = 17;
    public static final int PATTERN_ROWS = 6;
    public static final int PAGE_SIZE = PATTERN_COLS * PATTERN_ROWS;
    public static final int UPGRADE_SLOTS = AssemblyControllerBlockEntity.UPGRADE_SLOTS;

    private static final int PLAYER_INV_X = 88;
    private static final int PLAYER_INV_Y = 162;
    private static final int UPGRADE_Y = 18;
    private static final int PATTERN_START_Y = 44;

    private final Inventory playerInventory;
    private final BlockPos controllerPos;
    private final ItemStackHandler itemHandler;
    private final int[] pageData = new int[1];
    private final DataSlot pageSlot;

    public AssemblyMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.ASSEMBLY.get(), id);
        this.playerInventory = inv;
        this.controllerPos = pos;

        Level level = inv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AssemblyControllerBlockEntity controller) {
            this.itemHandler = controller.getItemHandler();
        } else {
            // 客户端未加载或方块实体不可见时提供临时背包，避免崩溃
            this.itemHandler = new ItemStackHandler(UPGRADE_SLOTS);
        }

        // 升级卡槽
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            addSlot(new SlotItemHandler(itemHandler, i, 8 + i * 18, UPGRADE_Y));
        }

        // 样板槽（按当前页动态映射到实际物品索引）
        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int col = 0; col < PATTERN_COLS; col++) {
                int localIndex = row * PATTERN_COLS + col;
                int x = 8 + col * 18;
                int y = PATTERN_START_Y + row * 18;
                addSlot(new AssemblyPatternSlot(itemHandler, localIndex, x, y, this));
            }
        }

        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = 9 + row * 9 + col;
                addSlot(new Slot(playerInventory, index, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }

        // 页码同步数据槽
        this.pageSlot = this.addDataSlot(DataSlot.shared(this.pageData, 0));
    }

    public static AssemblyMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        return new AssemblyMenu(id, inv, buf.readBlockPos());
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

    public int getPageIndex() {
        return this.pageData[0];
    }

    public int getTotalPages() {
        AssemblyControllerBlockEntity controller = getController();
        if (controller == null) {
            return 1;
        }
        return Math.max(1, controller.getPatternPages());
    }

    public void setPageIndex(int page) {
        int totalPages = getTotalPages();
        int newPage = Math.max(0, Math.min(page, totalPages - 1));
        if (newPage != this.pageData[0]) {
            this.pageData[0] = newPage;
            this.pageSlot.set(newPage);
            // 立即刷新槽位显示
            broadcastChanges();
        }
    }

    public void pageUp() {
        if (playerInventory.player.level().isClientSide()) {
            ModNetwork.CHANNEL.sendToServer(new AssemblyPagePacket(controllerPos, getPageIndex() + 1));
        } else {
            setPageIndex(getPageIndex() + 1);
        }
    }

    public void pageDown() {
        if (playerInventory.player.level().isClientSide()) {
            ModNetwork.CHANNEL.sendToServer(new AssemblyPagePacket(controllerPos, getPageIndex() - 1));
        } else {
            setPageIndex(getPageIndex() - 1);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            int ourSlotCount = UPGRADE_SLOTS + PAGE_SIZE;
            if (index < ourSlotCount) {
                if (!this.moveItemStackTo(stack, ourSlotCount, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stack, 0, ourSlotCount, false)) {
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

    /**
     * 装配枢纽样板槽：根据当前页码动态映射到物品处理器的实际索引。
     */
    private static class AssemblyPatternSlot extends SlotItemHandler {
        private final AssemblyMenu menu;
        private final int localIndex;
        private final ItemStackHandler handler;

        AssemblyPatternSlot(ItemStackHandler handler, int localIndex, int x, int y, AssemblyMenu menu) {
            super(handler, localIndex, x, y);
            this.handler = handler;
            this.menu = menu;
            this.localIndex = localIndex;
        }

        private int getAbsoluteIndex() {
            return UPGRADE_SLOTS + menu.getPageIndex() * PAGE_SIZE + localIndex;
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
            if (level == null || PatternDetailsHelper.decodePattern(stack, level) == null) {
                return false;
            }
            return handler.isItemValid(absolute, stack);
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
