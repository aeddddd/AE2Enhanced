package com.github.aeddddd.ae2enhanced.common.menu;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;
import com.github.aeddddd.ae2enhanced.structure.ValidationResult;

/**
 * 超因果计算核心未成形状态菜单。
 */
public class ComputationUnformedMenu extends StructureUnformedMenu {

    public ComputationUnformedMenu(int id, Inventory inv, BlockPos controllerPos) {
        super(ModMenus.COMPUTATION_UNFORMED.get(), id, inv, controllerPos);
    }

    public static ComputationUnformedMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        return new ComputationUnformedMenu(id, inv, buf.readBlockPos());
    }

    @Override
    public Map<Block, Integer> getMissing() {
        Level level = playerInventory.player.level();
        ValidationResult result = SupercausalStructure.validate(level, controllerPos);
        return result.missing;
    }

    @Override
    public boolean isTileFormed() {
        Level level = playerInventory.player.level();
        BlockEntity be = level.getBlockEntity(controllerPos);
        return be instanceof ComputationCoreBlockEntity controller && controller.isFormed();
    }
}
