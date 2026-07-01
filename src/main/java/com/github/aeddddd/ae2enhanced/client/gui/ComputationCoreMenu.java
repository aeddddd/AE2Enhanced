package com.github.aeddddd.ae2enhanced.client.gui;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 超因果计算核心菜单容器。
 * <p>无物品槽，仅用于同步并显示结构状态、CPU 池大小、活跃任务数与并行上限。</p>
 */
public class ComputationCoreMenu extends AbstractContainerMenu {

    private static final int DATA_FORMED = 0;
    private static final int DATA_POOL_SIZE = 1;
    private static final int DATA_ACTIVE_JOBS = 2;
    private static final int DATA_MAX_PARALLEL = 3;

    private final Inventory playerInventory;
    private final BlockPos controllerPos;
    private final ContainerData data;

    public ComputationCoreMenu(int id, Inventory inv, BlockPos controllerPos, ContainerData data) {
        super(ModMenus.COMPUTATION_CORE.get(), id);
        this.playerInventory = inv;
        this.controllerPos = controllerPos;
        this.data = data;
        addDataSlots(data);
    }

    public static ComputationCoreMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new ComputationCoreMenu(id, inv, pos, new SimpleContainerData(4));
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Nullable
    public ComputationCoreBlockEntity getController() {
        Level level = playerInventory.player.level();
        BlockEntity be = level.getBlockEntity(controllerPos);
        return be instanceof ComputationCoreBlockEntity core ? core : null;
    }

    public boolean isFormed() {
        return data.get(DATA_FORMED) != 0;
    }

    public int getPoolSize() {
        return data.get(DATA_POOL_SIZE);
    }

    public int getActiveJobs() {
        return data.get(DATA_ACTIVE_JOBS);
    }

    public int getMaxParallel() {
        return data.get(DATA_MAX_PARALLEL);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void broadcastChanges() {
        if (!playerInventory.player.level().isClientSide()) {
            ComputationCoreBlockEntity controller = getController();
            if (controller != null) {
                data.set(DATA_FORMED, controller.isFormed() ? 1 : 0);
                data.set(DATA_POOL_SIZE, controller.getPoolSize());
                data.set(DATA_ACTIVE_JOBS, controller.getActiveJobs());
                data.set(DATA_MAX_PARALLEL, controller.getParallelLimit());
            }
        }
        super.broadcastChanges();
    }
}
