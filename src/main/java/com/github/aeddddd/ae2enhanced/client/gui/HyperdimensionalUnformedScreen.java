package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.menu.HyperdimensionalUnformedMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 超维度仓储未成形状态 GUI.
 */
public class HyperdimensionalUnformedScreen extends StructureUnformedScreen<HyperdimensionalUnformedMenu> {

    public HyperdimensionalUnformedScreen(HyperdimensionalUnformedMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected String getTitleKey() {
        return "gui.ae2enhanced.unformed.title";
    }

    @Override
    protected String getSubtitleKey() {
        return "block.ae2enhanced.hyperdimensional_controller";
    }
}
