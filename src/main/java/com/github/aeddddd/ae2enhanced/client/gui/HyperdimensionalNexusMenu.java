package com.github.aeddddd.ae2enhanced.client.gui;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 超维度仓储 Nexus 成形状态的菜单容器。
 */
public class HyperdimensionalNexusMenu extends AbstractContainerMenu {

    private final Inventory playerInventory;
    private final BlockPos controllerPos;

    public HyperdimensionalNexusMenu(int id, Inventory inv, BlockPos controllerPos) {
        super(ModMenus.HYPERDIMENSIONAL_NEXUS.get(), id);
        this.playerInventory = inv;
        this.controllerPos = controllerPos;
    }

    public static HyperdimensionalNexusMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new HyperdimensionalNexusMenu(id, inv, pos);
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Nullable
    public HyperdimensionalControllerBlockEntity getController() {
        Level level = playerInventory.player.level();
        if (level == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(controllerPos);
        if (be instanceof HyperdimensionalControllerBlockEntity controller) {
            return controller;
        }
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
