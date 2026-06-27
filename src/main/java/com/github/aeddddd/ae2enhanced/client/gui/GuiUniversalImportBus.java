package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerUniversalImportBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalImportBus;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * E1a：通用输入总线的 GUI.
 */
public class GuiUniversalImportBus extends GuiUniversalBus {

    public GuiUniversalImportBus(InventoryPlayer inventoryPlayer, PartUniversalImportBus te) {
        super(new ContainerUniversalImportBus(inventoryPlayer, te));
    }

    @Override
    protected String getBusDisplayName() {
        return "Universal Import Bus";
    }
}
