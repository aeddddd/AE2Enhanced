package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.menu.ComputationUnformedMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 超因果计算核心未成形状态 GUI.
 */
public class ComputationUnformedScreen extends StructureUnformedScreen<ComputationUnformedMenu> {

    public ComputationUnformedScreen(ComputationUnformedMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
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
