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

import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 超因果计算核心成形状态菜单。
 * <p>纯展示面板，无物品槽，避免服务端下发背包数据导致客户端物品栏错位。</p>
 */
public class ComputationCoreMenu extends AbstractContainerMenu {

    private final Inventory playerInventory;
    private final BlockPos controllerPos;

    public ComputationCoreMenu(int id, Inventory inv, BlockPos controllerPos) {
        super(ModMenus.COMPUTATION_CORE.get(), id);
        this.playerInventory = inv;
        this.controllerPos = controllerPos;
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
        return player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
