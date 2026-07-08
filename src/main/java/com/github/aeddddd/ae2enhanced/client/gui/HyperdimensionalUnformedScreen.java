package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 超维度仓储未成形状态 GUI。
 */
public class HyperdimensionalUnformedScreen extends StructureUnformedScreen<HyperdimensionalUnformedMenu> {

    public HyperdimensionalUnformedScreen(HyperdimensionalUnformedMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 260,
                150, 140, 134, 170, 76, 54, 70, 46, 58, 70);
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
