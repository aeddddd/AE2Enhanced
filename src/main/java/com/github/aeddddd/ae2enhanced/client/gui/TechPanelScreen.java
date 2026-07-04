package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Tech-panel 风格 GUI 抽象基类。
 */
public abstract class TechPanelScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    public TechPanelScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    protected void drawTechPanelFrame(GuiGraphics graphics) {
        int left = this.leftPos;
        int top = this.topPos;
        int right = left + this.imageWidth;
        int bottom = top + this.imageHeight;

        graphics.fill(left, top, right, bottom, GuiColors.PANEL_BG);
        graphics.fill(left, top, right, top + 2, GuiColors.ACCENT);

        graphics.fill(left, top, right, top + 1, GuiColors.BORDER_DIM);
        graphics.fill(left, bottom - 1, right, bottom, GuiColors.BORDER_DIM);
        graphics.fill(left, top, left + 1, bottom, GuiColors.BORDER_DIM);
        graphics.fill(right - 1, top, right, bottom, GuiColors.BORDER_DIM);

        int corner = 10;
        graphics.fill(left, top, left + corner, top + 2, GuiColors.ACCENT);
        graphics.fill(left, top, left + 2, top + corner, GuiColors.ACCENT);
        graphics.fill(right - corner, top, right, top + 2, GuiColors.ACCENT);
        graphics.fill(right - 2, top, right, top + corner, GuiColors.ACCENT);
        graphics.fill(left, bottom - 2, left + corner, bottom, GuiColors.ACCENT);
        graphics.fill(left, bottom - corner, left + 2, bottom, GuiColors.ACCENT);
        graphics.fill(right - corner, bottom - 2, right, bottom, GuiColors.ACCENT);
        graphics.fill(right - 2, bottom - corner, right, bottom, GuiColors.ACCENT);
    }

    protected void drawInnerPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, GuiColors.PANEL_LIGHT);
        graphics.fill(left, top, right, top + 1, GuiColors.BORDER_DIM);
        graphics.fill(left, bottom - 1, right, bottom, GuiColors.BORDER_DIM);
    }

    protected void drawSlotBorders(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = this.leftPos + slot.x;
            int y = this.topPos + slot.y;
            boolean hovered = this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
            int color = hovered ? GuiColors.SLOT_HOVER : GuiColors.SLOT_BORDER;
            graphics.fill(x - 1, y - 1, x + 18, y, color);
            graphics.fill(x - 1, y + 16, x + 18, y + 17, color);
            graphics.fill(x - 1, y, x, y + 16, color);
            graphics.fill(x + 16, y, x + 17, y + 16, color);
        }
    }
}
