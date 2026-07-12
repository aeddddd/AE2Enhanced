package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.common.menu.HyperdimensionalUnformedMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 超维度仓储未成形状态 GUI。
 */
public class HyperdimensionalUnformedScreen extends StructureUnformedScreen<HyperdimensionalUnformedMenu> {

    public HyperdimensionalUnformedScreen(HyperdimensionalUnformedMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, GuiConstants.UNFORMED_SMALL_YSIZE,
                GuiConstants.UNFORMED_SMALL_BUTTON_Y_OFFSET, GuiConstants.UNFORMED_SMALL_INNER_PANEL_BOTTOM,
                GuiConstants.UNFORMED_SMALL_STATUS_Y_OFFSET, GuiConstants.UNFORMED_SMALL_INVENTORY_DIVIDER_Y_OFFSET,
                GuiConstants.UNFORMED_SMALL_MISSING_LIST_START_Y, GuiConstants.UNFORMED_SMALL_READY_TEXT_Y,
                GuiConstants.UNFORMED_SMALL_HINT_TEXT_Y, GuiConstants.UNFORMED_SMALL_MISSING_TITLE_Y,
                GuiConstants.UNFORMED_SMALL_HEADER_Y, GuiConstants.UNFORMED_SMALL_HEADER_DIVIDER_Y);
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
