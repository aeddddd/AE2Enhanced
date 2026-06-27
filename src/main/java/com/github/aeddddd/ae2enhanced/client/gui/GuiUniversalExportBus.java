package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerUniversalExportBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalExportBus;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * E1b：通用输出总线的 GUI.
 */
public class GuiUniversalExportBus extends GuiUniversalBus {

    public GuiUniversalExportBus(InventoryPlayer inventoryPlayer, PartUniversalExportBus te) {
        super(new ContainerUniversalExportBus(inventoryPlayer, te));
    }

    @Override
    protected String getBusDisplayName() {
        return "Universal Export Bus";
    }
}
