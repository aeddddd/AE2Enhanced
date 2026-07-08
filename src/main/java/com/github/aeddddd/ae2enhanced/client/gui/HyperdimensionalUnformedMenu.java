package com.github.aeddddd.ae2enhanced.client.gui;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.client.gui.menu.StructureUnformedMenu;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;

/**
 * 超维度仓储未成形状态菜单。
 */
public class HyperdimensionalUnformedMenu extends StructureUnformedMenu {

    public HyperdimensionalUnformedMenu(int id, Inventory inv, BlockPos controllerPos) {
        super(ModMenus.HYPERDIMENSIONAL_UNFORMED.get(), id, inv, controllerPos);
    }

    public static HyperdimensionalUnformedMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        return new HyperdimensionalUnformedMenu(id, inv, buf.readBlockPos());
    }

    @Override
    public Map<Block, Integer> getMissing() {
        Level level = playerInventory.player.level();
        return HyperdimensionalStructure.getMissingMap(level, controllerPos);
    }

    @Override
    public boolean isTileFormed() {
        Level level = playerInventory.player.level();
        BlockEntity be = level.getBlockEntity(controllerPos);
        return be instanceof HyperdimensionalControllerBlockEntity controller && controller.isFormed();
    }
}
