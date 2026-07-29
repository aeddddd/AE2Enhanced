package com.github.aeddddd.ae2enhanced.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Map;

import com.github.aeddddd.ae2enhanced.client.gui.GuiConstants;

/**
 * 多方块结构未成形状态菜单抽象基类.
 * <p>包含玩家背包与快捷栏,布局与 2.png 纹理一致(同 HyperdimensionalNexusMenu).</p>
 */
public abstract class StructureUnformedMenu extends AbstractContainerMenu {

    private static final int INV_X = 8;
    private static final int INV_Y = 108;
    private static final int HOTBAR_Y = 166;

    protected final Inventory playerInventory;
    protected final BlockPos controllerPos;

    public StructureUnformedMenu(net.minecraft.world.inventory.MenuType<?> type, int id, Inventory inv, BlockPos controllerPos) {
        super(type, id);
        this.playerInventory = inv;
        this.controllerPos = controllerPos;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public abstract Map<Block, Integer> getMissing();

    public abstract boolean isTileFormed();

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5) <= GuiConstants.CONTAINER_MAX_DISTANCE_SQR;
    }

    /**
     * 无容器槽位,shift-click 仅在背包与快捷栏之间移动.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
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
            if (stack.getCount() == moved.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return moved;
    }
}
