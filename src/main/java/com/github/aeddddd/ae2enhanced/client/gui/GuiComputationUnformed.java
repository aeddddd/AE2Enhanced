package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerComputationUnformed;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.block.Block;
import net.minecraft.entity.player.InventoryPlayer;

import java.util.Map;

/**
 * Supercausal Computation Core 未成型状态 GUI.
 */
public class GuiComputationUnformed extends GuiStructureUnformed {

    public GuiComputationUnformed(InventoryPlayer playerInv, TileComputationCore tile) {
        super(playerInv, tile, new ContainerComputationUnformed(playerInv, tile), 260,
                150, 140, 134, 170, 76, 54, 70, 46, 58, 70);
    }

    @Override
    protected Map<Block, Integer> getMissingMap() {
        SupercausalStructure.ValidationResult result = SupercausalStructure.validate(mc.world, tile.getPos());
        return result.missing;
    }

    @Override
    protected String getTitleKey() {
        return "gui.ae2enhanced.computation.unformed.title";
    }

    @Override
    protected String getSubtitleKey() {
        return "tile.ae2enhanced.computation_core.name";
    }

    @Override
    protected boolean isTileFormed() {
        return ((TileComputationCore) tile).isFormed();
    }
}
