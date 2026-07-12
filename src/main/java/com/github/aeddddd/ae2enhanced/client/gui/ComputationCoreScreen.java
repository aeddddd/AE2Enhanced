package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.common.menu.ComputationCoreMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;

/**
 * 超因果计算核心成形状态 GUI。
 * <p>纯展示面板，无物品槽，无背包渲染。</p>
 */
public class ComputationCoreScreen extends AbstractContainerScreen<ComputationCoreMenu> {

    public ComputationCoreScreen(ComputationCoreMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GuiConstants.PANEL_WIDTH;
        this.imageHeight = GuiConstants.PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        drawTechPanelFrame(graphics);
        drawInnerPanel(graphics, this.leftPos + GuiConstants.PANEL_CONTENT_LEFT_MARGIN, this.topPos + GuiConstants.COMPUTATION_INNER_PANEL_TOP,
                this.leftPos + this.imageWidth - GuiConstants.PANEL_CONTENT_LEFT_MARGIN, this.topPos + this.imageHeight - GuiConstants.COMPUTATION_INNER_PANEL_BOTTOM_MARGIN);

        Component title = Component.translatable("gui.ae2enhanced.computation.formed.title");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, this.leftPos + (this.imageWidth - titleWidth) / 2, this.topPos + GuiConstants.COMPUTATION_TITLE_Y, GuiColors.ACCENT, false);

        graphics.fill(this.leftPos + GuiConstants.COMPUTATION_SEPARATOR_LEFT_MARGIN, this.topPos + GuiConstants.COMPUTATION_SEPARATOR_Y,
                this.leftPos + this.imageWidth - GuiConstants.COMPUTATION_SEPARATOR_LEFT_MARGIN, this.topPos + GuiConstants.COMPUTATION_SEPARATOR_Y + 1, GuiColors.ACCENT_SOFT);

        ComputationCoreBlockEntity controller = this.menu.getController();
        if (controller == null) {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.tile_unavailable"),
                    this.leftPos + GuiConstants.COMPUTATION_CONTENT_START_X, this.topPos + GuiConstants.COMPUTATION_TILE_UNAVAILABLE_Y, GuiColors.TEXT_WARN, false);
            this.renderTooltip(graphics, mouseX, mouseY);
            return;
        }

        int x = this.leftPos + GuiConstants.COMPUTATION_CONTENT_START_X;
        int y = this.topPos + GuiConstants.COMPUTATION_CONTENT_START_Y;
        int lineHeight = GuiConstants.COMPUTATION_LINE_HEIGHT;

        Component formedStr = controller.isFormed()
                ? Component.translatable("gui.ae2enhanced.computation.status.online")
                : Component.translatable("gui.ae2enhanced.computation.status.offline");
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.status", formedStr), x, y, GuiColors.TEXT_MAIN, false);
        y += lineHeight + GuiConstants.COMPUTATION_PARAGRAPH_SPACING;

        int parallel = controller.getParallelLimit();
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.parallel", parallel), x, y, GuiColors.TEXT_MAIN, false);
        y += GuiConstants.COMPUTATION_SMALL_LINE_SPACING;
        drawBar(graphics, x, y, x + GuiConstants.COMPUTATION_BAR_MAX_WIDTH, GuiConstants.COMPUTATION_BAR_HEIGHT, 1.0f, GuiColors.BAR_BG, GuiColors.BAR_FILL);
        y += GuiConstants.COMPUTATION_BAR_HEIGHT + GuiConstants.COMPUTATION_PARAGRAPH_SPACING;

        int orders = controller.getActiveJobs();
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.active_orders", orders), x, y, GuiColors.TEXT_MAIN, false);
        y += lineHeight;

        int maxOrders = AE2EnhancedConfig.COMMON.computationMaxParallel.get();
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.queue_capacity", maxOrders), x, y, GuiColors.TEXT_MAIN, false);
        y += lineHeight + GuiConstants.COMPUTATION_PARAGRAPH_SPACING;

        graphics.fill(x, y, this.leftPos + this.imageWidth - GuiConstants.COMPUTATION_CONTENT_START_X, y + 1, GuiColors.BORDER_DIM);
        y += GuiConstants.COMPUTATION_DIVIDER_VERTICAL_MARGIN;

        if (orders == 0) {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.orders.empty"), x, y, GuiConstants.COMPUTATION_EMPTY_TEXT_COLOR, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.orders.placeholder"), x, y, GuiConstants.COMPUTATION_EMPTY_TEXT_COLOR, false);
        }
        y += lineHeight + GuiConstants.COMPUTATION_PARAGRAPH_SPACING;

        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.engine.initializing"), x, y, GuiConstants.COMPUTATION_INITIALIZING_TEXT_COLOR, false);

        Component hint = Component.translatable("gui.ae2enhanced.computation.hint.close");
        int hintW = this.font.width(hint);
        graphics.drawString(this.font, hint, this.leftPos + (this.imageWidth - hintW) / 2, this.topPos + this.imageHeight - GuiConstants.COMPUTATION_HINT_BOTTOM_MARGIN, GuiConstants.COMPUTATION_HINT_TEXT_COLOR, false);

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        // 背景已在 render 中绘制
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标签已在 render 中绘制
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int maxX, int height, float ratio, int bgColor, int fillColor) {
        graphics.fill(x, y, maxX, y + height, bgColor);
        int fillWidth = (int) ((maxX - x) * ratio);
        if (fillWidth > 0) {
            graphics.fill(x, y, x + fillWidth, y + height, fillColor);
        }
    }

    private void drawTechPanelFrame(GuiGraphics graphics) {
        int left = this.leftPos;
        int top = this.topPos;
        int right = left + this.imageWidth;
        int bottom = top + this.imageHeight;

        graphics.fill(left, top, right, bottom, GuiColors.PANEL_BG);
        graphics.fill(left, top, right, top + GuiConstants.COMPUTATION_FRAME_ACCENT_THICKNESS, GuiColors.ACCENT);

        graphics.fill(left, top, right, top + GuiConstants.COMPUTATION_BORDER_THICKNESS, GuiColors.BORDER_DIM);
        graphics.fill(left, bottom - GuiConstants.COMPUTATION_BORDER_THICKNESS, right, bottom, GuiColors.BORDER_DIM);
        graphics.fill(left, top, left + GuiConstants.COMPUTATION_BORDER_THICKNESS, bottom, GuiColors.BORDER_DIM);
        graphics.fill(right - GuiConstants.COMPUTATION_BORDER_THICKNESS, top, right, bottom, GuiColors.BORDER_DIM);

        int corner = GuiConstants.COMPUTATION_CORNER_ACCENT_SIZE;
        graphics.fill(left, top, left + corner, top + GuiConstants.COMPUTATION_FRAME_ACCENT_THICKNESS, GuiColors.ACCENT);
        graphics.fill(left, top, left + GuiConstants.COMPUTATION_FRAME_ACCENT_THICKNESS, top + corner, GuiColors.ACCENT);
        graphics.fill(right - corner, top, right, top + GuiConstants.COMPUTATION_FRAME_ACCENT_THICKNESS, GuiColors.ACCENT);
        graphics.fill(right - GuiConstants.COMPUTATION_FRAME_ACCENT_THICKNESS, top, right, top + corner, GuiColors.ACCENT);
        graphics.fill(left, bottom - GuiConstants.COMPUTATION_FRAME_ACCENT_THICKNESS, left + corner, bottom, GuiColors.ACCENT);
        graphics.fill(left, bottom - corner, left + GuiConstants.COMPUTATION_FRAME_ACCENT_THICKNESS, bottom, GuiColors.ACCENT);
        graphics.fill(right - corner, bottom - GuiConstants.COMPUTATION_FRAME_ACCENT_THICKNESS, right, bottom, GuiColors.ACCENT);
        graphics.fill(right - GuiConstants.COMPUTATION_FRAME_ACCENT_THICKNESS, bottom - corner, right, bottom, GuiColors.ACCENT);
    }

    private void drawInnerPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, GuiColors.PANEL_LIGHT);
        graphics.fill(left, top, right, top + GuiConstants.COMPUTATION_BORDER_THICKNESS, GuiColors.BORDER_DIM);
        graphics.fill(left, bottom - GuiConstants.COMPUTATION_BORDER_THICKNESS, right, bottom, GuiColors.BORDER_DIM);
    }
}
