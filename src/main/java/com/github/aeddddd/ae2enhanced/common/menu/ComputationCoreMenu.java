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

import com.github.aeddddd.ae2enhanced.client.gui.GuiConstants;
import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 超因果计算核心成形状态菜单.
 * <p>包含玩家背包与快捷栏,与 2.png 纹理布局匹配（同 Nexus 面板风格）.</p>
 */
public class ComputationCoreMenu extends AbstractContainerMenu {

    private static final int INV_X = 8;
    private static final int INV_Y = 108;
    private static final int HOTBAR_Y = 166;

    private final Inventory playerInventory;
    private final BlockPos controllerPos;

    public ComputationCoreMenu(int id, Inventory inv, BlockPos controllerPos) {
        super(ModMenus.COMPUTATION_CORE.get(), id);
        this.playerInventory = inv;
        this.controllerPos = controllerPos;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;
                addSlot(new Slot(playerInventory, index, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public static ComputationCoreMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new ComputationCoreMenu(id, inv, pos);
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Nullable
    public ComputationCoreBlockEntity getController() {
        Level level = playerInventory.player.level();
        if (level == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(controllerPos);
        return be instanceof ComputationCoreBlockEntity core ? core : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5) <= GuiConstants.CONTAINER_MAX_DISTANCE_SQR;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 无机器槽位,仅在玩家背包与快捷栏之间移动
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (index < 27) {
            if (!this.moveItemStackTo(stack, 27, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }
}
