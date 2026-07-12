package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.common.menu.AssemblyUnformedMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 装配枢纽未成形状态 GUI。
 */
public class AssemblyUnformedScreen extends StructureUnformedScreen<AssemblyUnformedMenu> {

    public AssemblyUnformedScreen(AssemblyUnformedMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, GuiConstants.UNFORMED_LARGE_YSIZE,
                GuiConstants.UNFORMED_LARGE_BUTTON_Y_OFFSET, GuiConstants.UNFORMED_LARGE_INNER_PANEL_BOTTOM,
                GuiConstants.UNFORMED_LARGE_STATUS_Y_OFFSET, GuiConstants.UNFORMED_LARGE_INVENTORY_DIVIDER_Y_OFFSET,
                GuiConstants.UNFORMED_LARGE_MISSING_LIST_START_Y, GuiConstants.UNFORMED_LARGE_READY_TEXT_Y,
                GuiConstants.UNFORMED_LARGE_HINT_TEXT_Y, GuiConstants.UNFORMED_LARGE_MISSING_TITLE_Y,
                GuiConstants.UNFORMED_LARGE_HEADER_Y, GuiConstants.UNFORMED_LARGE_HEADER_DIVIDER_Y);
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
