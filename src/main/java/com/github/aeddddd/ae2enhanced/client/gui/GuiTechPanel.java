package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

/**
 * Tech-panel 风格 GUI 抽象基类.
 *
 * 统一绘制主背景、外边框、角落装饰、顶部高亮条、内面板及 slot 边框,
 * 消除 GuiAssemblyFormed / GuiAssemblyPattern / GuiStructureUnformed 等
 * 5+ 个 GUI 中各自重复的 ~50 行背景绘制代码.
 */
public abstract class GuiTechPanel extends GuiContainer {

    public GuiTechPanel(Container container) {
        super(container);
    }

    /** 绘制通用 tech-panel 框架：主背景 + 外边框 + 角落装饰 + 顶部高亮条 */
    protected void drawTechPanelFrame() {
        // 主背景
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, GuiColors.PANEL_BG);

        // 顶部高亮条
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 2, GuiColors.ACCENT);

        // 外边框
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 1, GuiColors.BORDER_DIM);
        drawRect(guiLeft, guiTop + ySize - 1, guiLeft + xSize, guiTop + ySize, GuiColors.BORDER_DIM);
        drawRect(guiLeft, guiTop, guiLeft + 1, guiTop + ySize, GuiColors.BORDER_DIM);
        drawRect(guiLeft + xSize - 1, guiTop, guiLeft + xSize, guiTop + ySize, GuiColors.BORDER_DIM);

        // 角落装饰
        int corner = 10;
        drawRect(guiLeft, guiTop, guiLeft + corner, guiTop + 2, GuiColors.ACCENT);
        drawRect(guiLeft, guiTop, guiLeft + 2, guiTop + corner, GuiColors.ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop, guiLeft + xSize, guiTop + 2, GuiColors.ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop, guiLeft + xSize, guiTop + corner, GuiColors.ACCENT);
        drawRect(guiLeft, guiTop + ySize - 2, guiLeft + corner, guiTop + ySize, GuiColors.ACCENT);
        drawRect(guiLeft, guiTop + ySize - corner, guiLeft + 2, guiTop + ySize, GuiColors.ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop + ySize - 2, guiLeft + xSize, guiTop + ySize, GuiColors.ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop + ySize - corner, guiLeft + xSize, guiTop + ySize, GuiColors.ACCENT);
    }

    /** 绘制内面板区域(含上下边框) */
    protected void drawInnerPanel(int left, int top, int right, int bottom) {
        drawRect(left, top, right, bottom, GuiColors.PANEL_LIGHT);
        drawRect(left, top, right, top + 1, GuiColors.BORDER_DIM);
        drawRect(left, bottom - 1, right, bottom, GuiColors.BORDER_DIM);
    }

    /** 为所有启用的 slot 绘制边框(hover 高亮) */
    protected void drawSlotBorders(int mouseX, int mouseY) {
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!slot.isEnabled()) continue;
            int x = guiLeft + slot.xPos;
            int y = guiTop + slot.yPos;
            boolean hovered = this.isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY);
            int color = hovered ? GuiColors.SLOT_HOVER : GuiColors.SLOT_BORDER;
            drawRect(x - 1, y - 1, x + 18, y, color);
            drawRect(x - 1, y + 16, x + 18, y + 17, color);
            drawRect(x - 1, y, x, y + 16, color);
            drawRect(x + 16, y, x + 17, y + 16, color);
        }
    }
}
