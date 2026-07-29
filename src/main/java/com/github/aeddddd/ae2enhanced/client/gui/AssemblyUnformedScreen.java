package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.menu.AssemblyUnformedMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 装配枢纽未成形状态 GUI.
 */
public class AssemblyUnformedScreen extends StructureUnformedScreen<AssemblyUnformedMenu> {

    public AssemblyUnformedScreen(AssemblyUnformedMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected String getTitleKey() {
        return "gui.ae2enhanced.unformed.title";
    }

    @Override
    protected String getSubtitleKey() {
        return "gui.ae2enhanced.unformed.subtitle";
    }
}
