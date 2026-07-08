package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 超因果计算核心未成形状态 GUI。
 */
public class ComputationUnformedScreen extends StructureUnformedScreen<ComputationUnformedMenu> {

    public ComputationUnformedScreen(ComputationUnformedMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 260,
                150, 140, 134, 170, 76, 54, 70, 46, 58, 70);
    }

    @Override
    protected String getTitleKey() {
        return "gui.ae2enhanced.computation.unformed.title";
    }

    @Override
    protected String getSubtitleKey() {
        return "block.ae2enhanced.computation_controller";
    }
}
