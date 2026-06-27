package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyUnformed;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.block.Block;
import net.minecraft.entity.player.InventoryPlayer;

import java.util.Map;

/**
 * 装配枢纽未成型状态 GUI.
 */
public class GuiAssemblyUnformed extends GuiStructureUnformed {

    public GuiAssemblyUnformed(InventoryPlayer playerInv, TileAssemblyController tile) {
        super(playerInv, tile, new ContainerAssemblyUnformed(playerInv, tile), 350,
                236, 210, 224, 256, 80, 62, 82, 46, 62, 74);
    }

    @Override
    protected Map<Block, Integer> getMissingMap() {
        return AssemblyStructure.getMissingMap(mc.world, tile.getPos());
    }

    @Override
    protected String getTitleKey() {
        return "gui.ae2enhanced.unformed.title";
    }

    @Override
    protected String getSubtitleKey() {
        return "gui.ae2enhanced.unformed.subtitle";
    }

    @Override
    protected boolean isTileFormed() {
        return ((TileAssemblyController) tile).isFormed();
    }
}
