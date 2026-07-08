package com.github.aeddddd.ae2enhanced.client.gui;

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
        this.imageWidth = 280;
        this.imageHeight = 200;
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
        drawInnerPanel(graphics, this.leftPos + 10, this.topPos + 36, this.leftPos + this.imageWidth - 10, this.topPos + this.imageHeight - 28);

        Component title = Component.translatable("gui.ae2enhanced.computation.formed.title");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, this.leftPos + (this.imageWidth - titleWidth) / 2, this.topPos + 8, GuiColors.ACCENT, false);

        graphics.fill(this.leftPos + 16, this.topPos + 22, this.leftPos + this.imageWidth - 16, this.topPos + 23, GuiColors.ACCENT_SOFT);

        ComputationCoreBlockEntity controller = this.menu.getController();
        if (controller == null) {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.tile_unavailable"),
                    this.leftPos + 20, this.topPos + 40, GuiColors.TEXT_WARN, false);
            this.renderTooltip(graphics, mouseX, mouseY);
            return;
        }

        int x = this.leftPos + 20;
        int y = this.topPos + 42;
        int lineHeight = 14;

        Component formedStr = controller.isFormed()
                ? Component.translatable("gui.ae2enhanced.computation.status.online")
                : Component.translatable("gui.ae2enhanced.computation.status.offline");
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.status", formedStr), x, y, GuiColors.TEXT_MAIN, false);
        y += lineHeight + 4;

        int parallel = controller.getParallelLimit();
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.parallel", parallel), x, y, GuiColors.TEXT_MAIN, false);
        y += 12;
        drawBar(graphics, x, y, x + 140, 8, 1.0f, GuiColors.BAR_BG, GuiColors.BAR_FILL);
        y += 14;

        int orders = controller.getActiveJobs();
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.active_orders", orders), x, y, GuiColors.TEXT_MAIN, false);
        y += lineHeight;

        int maxOrders = AE2EnhancedConfig.COMMON.computationMaxParallel.get();
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.queue_capacity", maxOrders), x, y, GuiColors.TEXT_MAIN, false);
        y += lineHeight + 4;

        graphics.fill(x, y, this.leftPos + this.imageWidth - 20, y + 1, GuiColors.BORDER_DIM);
        y += 6;

        if (orders == 0) {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.orders.empty"), x, y, 0xFF668899, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.orders.placeholder"), x, y, 0xFF668899, false);
        }
        y += lineHeight + 4;

        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.engine.initializing"), x, y, 0xFF556677, false);

        Component hint = Component.translatable("gui.ae2enhanced.computation.hint.close");
        int hintW = this.font.width(hint);
        graphics.drawString(this.font, hint, this.leftPos + (this.imageWidth - hintW) / 2, this.topPos + this.imageHeight - 18, 0xFF445566, false);

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

    private void drawInnerPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, GuiColors.PANEL_LIGHT);
        graphics.fill(left, top, right, top + 1, GuiColors.BORDER_DIM);
        graphics.fill(left, bottom - 1, right, bottom, GuiColors.BORDER_DIM);
    }
}
