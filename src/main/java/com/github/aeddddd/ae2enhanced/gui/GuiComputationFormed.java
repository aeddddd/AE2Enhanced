package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

import java.io.IOException;

/**
 * Supercausal Computation Core formed state GUI.
 * Pure display, no item slots, no inventory rendering.
 */
public class GuiComputationFormed extends GuiScreen {

    private static final int PANEL_BG = 0xFF1a1a2e;
    private static final int PANEL_LIGHT = 0xFF16213e;
    private static final int BORDER_DIM = 0xFF0f3460;
    private static final int ACCENT = 0xFF00d4ff;
    private static final int ACCENT_SOFT = 0xFF0f4c75;
    private static final int TEXT_MAIN = 0xFFe0e0e0;
    private static final int TEXT_SUCCESS = 0xFF55ff88;
    private static final int TEXT_WARN = 0xFFffaa55;
    private static final int BAR_BG = 0xFF0a1a2a;
    private static final int BAR_FILL = 0xFF00d4ff;

    private final TileComputationCore tile;
    private int xSize = 280;
    private int ySize = 200;
    private int guiLeft;
    private int guiTop;

    public GuiComputationFormed(TileComputationCore tile) {
        this.tile = tile;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - this.xSize) / 2;
        this.guiTop = (this.height - this.ySize) / 2;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, PANEL_BG);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 2, ACCENT);

        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 1, BORDER_DIM);
        drawRect(guiLeft, guiTop + ySize - 1, guiLeft + xSize, guiTop + ySize, BORDER_DIM);
        drawRect(guiLeft, guiTop, guiLeft + 1, guiTop + ySize, BORDER_DIM);
        drawRect(guiLeft + xSize - 1, guiTop, guiLeft + xSize, guiTop + ySize, BORDER_DIM);

        int corner = 10;
        drawRect(guiLeft, guiTop, guiLeft + corner, guiTop + 2, ACCENT);
        drawRect(guiLeft, guiTop, guiLeft + 2, guiTop + corner, ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop, guiLeft + xSize, guiTop + 2, ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop, guiLeft + xSize, guiTop + corner, ACCENT);
        drawRect(guiLeft, guiTop + ySize - 2, guiLeft + corner, guiTop + ySize, ACCENT);
        drawRect(guiLeft, guiTop + ySize - corner, guiLeft + 2, guiTop + ySize, ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop + ySize - 2, guiLeft + xSize, guiTop + ySize, ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop + ySize - corner, guiLeft + xSize, guiTop + ySize, ACCENT);

        drawRect(guiLeft + 10, guiTop + 36, guiLeft + xSize - 10, guiTop + ySize - 28, PANEL_LIGHT);
        drawRect(guiLeft + 10, guiTop + 36, guiLeft + xSize - 10, guiTop + 37, BORDER_DIM);
        drawRect(guiLeft + 10, guiTop + ySize - 29, guiLeft + xSize - 10, guiTop + ySize - 28, BORDER_DIM);

        String title = I18n.format("gui.ae2enhanced.computation.formed.title");
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, guiLeft + (xSize - titleWidth) / 2, guiTop + 8, ACCENT);

        drawRect(guiLeft + 16, guiTop + 22, guiLeft + xSize - 16, guiTop + 23, ACCENT_SOFT);

        if (tile == null) {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.tile_unavailable"), guiLeft + 20, guiTop + 40, TEXT_WARN);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        int x = guiLeft + 20;
        int y = guiTop + 42;
        int lineHeight = 14;

        // Status indicator
        String formedStr = tile.isFormed()
                ? I18n.format("gui.ae2enhanced.computation.status.online")
                : I18n.format("gui.ae2enhanced.computation.status.offline");
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.label.status", formedStr), x, y, TEXT_MAIN);
        y += lineHeight + 4;

        // Parallel limit with bar
        int parallel = tile.getParallelLimit();
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.label.parallel", parallel), x, y, TEXT_MAIN);
        y += 12;
        drawBar(x, y, x + 140, 8, 1.0f, BAR_BG, BAR_FILL);
        y += 14;

        // Active orders
        int orders = tile.getActiveOrderCount();
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.label.active_orders", orders), x, y, TEXT_MAIN);
        y += lineHeight;

        // Max orders from config
        int maxOrders = AE2EnhancedConfig.crafting.maxActiveOrders;
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.label.queue_capacity", maxOrders), x, y, TEXT_MAIN);
        y += lineHeight + 4;

        // Divider
        drawRect(x, y, guiLeft + xSize - 20, y + 1, BORDER_DIM);
        y += 6;

        // Placeholder for order list (P1 engine)
        if (orders == 0) {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.orders.empty"), x, y, 0xFF668899);
        } else {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.orders.placeholder"), x, y, 0xFF668899);
        }
        y += lineHeight + 4;

        // Crafting engine placeholder
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.engine.initializing"), x, y, 0xFF556677);

        // Bottom hint
        String hint = I18n.format("gui.ae2enhanced.computation.hint.close");
        int hintW = fontRenderer.getStringWidth(hint);
        fontRenderer.drawString(hint, guiLeft + (xSize - hintW) / 2, guiTop + ySize - 18, 0xFF445566);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawBar(int x, int y, int maxX, int height, float ratio, int bgColor, int fillColor) {
        drawRect(x, y, maxX, y + height, bgColor);
        int fillWidth = (int) ((maxX - x) * ratio);
        if (fillWidth > 0) {
            drawRect(x, y, x + fillWidth, y + height, fillColor);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || this.mc.gameSettings.keyBindInventory.isActiveAndMatches(keyCode)) {
            this.mc.displayGuiScreen(null);
        }
        super.keyTyped(typedChar, keyCode);
    }
}
