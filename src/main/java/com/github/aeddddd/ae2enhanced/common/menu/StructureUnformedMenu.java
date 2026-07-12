package com.github.aeddddd.ae2enhanced.common.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Map;

import com.github.aeddddd.ae2enhanced.client.gui.GuiConstants;

/**
 * 多方块结构未成形状态菜单抽象基类。
 */
public abstract class StructureUnformedMenu extends AbstractContainerMenu {

    protected final Inventory playerInventory;
    protected final BlockPos controllerPos;

    public StructureUnformedMenu(net.minecraft.world.inventory.MenuType<?> type, int id, Inventory inv, BlockPos controllerPos) {
        super(type, id);
        this.playerInventory = inv;
        this.controllerPos = controllerPos;
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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
