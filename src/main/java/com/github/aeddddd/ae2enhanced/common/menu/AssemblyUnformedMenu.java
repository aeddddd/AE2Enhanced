package com.github.aeddddd.ae2enhanced.common.menu;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;

/**
 * 装配枢纽未成形状态菜单。
 */
public class AssemblyUnformedMenu extends StructureUnformedMenu {

    public AssemblyUnformedMenu(int id, Inventory inv, BlockPos controllerPos) {
        super(ModMenus.ASSEMBLY_UNFORMED.get(), id, inv, controllerPos);
    }

    public static AssemblyUnformedMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        return new AssemblyUnformedMenu(id, inv, buf.readBlockPos());
    }

    @Override
    public Map<Block, Integer> getMissing() {
        Level level = playerInventory.player.level();
        return AssemblyStructure.getMissingMap(level, controllerPos);
    }

    @Override
    public boolean isTileFormed() {
        Level level = playerInventory.player.level();
        BlockEntity be = level.getBlockEntity(controllerPos);
        return be instanceof AssemblyControllerBlockEntity controller && controller.isFormed();
    }
}
