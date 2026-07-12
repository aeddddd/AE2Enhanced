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

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.client.gui.GuiConstants;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 装配枢纽控制器成形状态菜单。
 * <p>包含 6 个升级卡槽与玩家背包。</p>
 */
public class AssemblyMenu extends AbstractContainerMenu {

    public static final int UPGRADE_SLOTS = AssemblyControllerBlockEntity.UPGRADE_SLOTS;

    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int UPGRADE_Y = 8;

    private final Inventory playerInventory;
    private final BlockPos controllerPos;
    private final ItemStackHandler itemHandler;

    public AssemblyMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.ASSEMBLY.get(), id);
        this.playerInventory = inv;
        this.controllerPos = pos;

        Level level = inv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AssemblyControllerBlockEntity controller) {
            this.itemHandler = controller.getItemHandler();
        } else {
            this.itemHandler = new ItemStackHandler(UPGRADE_SLOTS);
        }

        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            int row = i / 3;
            int col = i % 3;
            addSlot(new SlotItemHandler(itemHandler, i, 8 + col * 18, UPGRADE_Y + row * 18));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = 9 + row * 9 + col;
                addSlot(new Slot(playerInventory, index, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, HOTBAR_Y));
        }
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
            if (index < UPGRADE_SLOTS) {
                if (!this.moveItemStackTo(stack, UPGRADE_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stack, 0, UPGRADE_SLOTS, false)) {
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
}
