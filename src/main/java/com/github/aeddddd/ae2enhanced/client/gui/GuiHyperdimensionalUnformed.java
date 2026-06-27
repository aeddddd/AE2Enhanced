package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerHyperdimensionalUnformed;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.block.Block;
import net.minecraft.entity.player.InventoryPlayer;

import java.util.Map;

/**
 * 超维度仓储中枢未成型状态 GUI.
 */
public class GuiHyperdimensionalUnformed extends GuiStructureUnformed {

    public GuiHyperdimensionalUnformed(InventoryPlayer playerInv, TileHyperdimensionalController tile) {
        super(playerInv, tile, new ContainerHyperdimensionalUnformed(playerInv, tile), 260,
                150, 140, 134, 170, 76, 54, 70, 46, 58, 70);
    }

    @Override
    protected Map<Block, Integer> getMissingMap() {
        return HyperdimensionalStructure.getMissingMap(mc.world, tile.getPos());
    }

    @Override
    protected String getTitleKey() {
        return "gui.ae2enhanced.unformed.title";
    }

    @Override
    protected String getSubtitleKey() {
        return "tile.ae2enhanced.hyperdimensional_controller.name";
    }

    @Override
    protected boolean isTileFormed() {
        return ((TileHyperdimensionalController) tile).isFormed();
    }
}
