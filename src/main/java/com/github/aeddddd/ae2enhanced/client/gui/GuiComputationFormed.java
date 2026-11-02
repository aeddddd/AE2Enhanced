package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerComputationFormed;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

/**
 * Supercausal Computation Core formed state GUI.
 * Pure display, no item slots, no inventory rendering.
 */
public class GuiComputationFormed extends GuiContainer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/computation_formed.png");

    // 手绘纹理配色（浅色背景上的文字颜色）
    private static final int TEXT_TITLE = 0xFF413F54;
    private static final int TEXT_BODY  = 0xFF3C4055;
    private static final int TEXT_DIM   = 0xFF696D88;
    private static final int TEXT_WARN  = 0xFFA85F00;
    private static final int BAR_BG     = 0xFF696D88;
    private static final int BAR_FILL   = 0xFF708CBA;

    private final TileComputationCore tile;

    public GuiComputationFormed(TileComputationCore tile) {
        super(new ContainerComputationFormed(tile));
        this.tile = tile;
    }

    @Override
    public void initGui() {
        this.xSize = 280;
        this.ySize = 200;
        super.initGui();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawModalRectWithCustomSizedTexture(guiLeft, guiTop, 0, 0, xSize, ySize, 512, 512);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        String title = I18n.format("gui.ae2enhanced.computation.formed.title");
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, guiLeft + (xSize - titleWidth) / 2, guiTop + 8, TEXT_TITLE);

        drawRect(guiLeft + 16, guiTop + 22, guiLeft + xSize - 16, guiTop + 23, 0xFF878FA5);

        if (tile == null) {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.tile_unavailable"), guiLeft + 20, guiTop + 40, TEXT_WARN);
            return;
        }

        int x = guiLeft + 20;
        int y = guiTop + 42;
        int lineHeight = 14;

        // Status indicator
        String formedStr = tile.isFormed()
                ? I18n.format("gui.ae2enhanced.computation.status.online")
                : I18n.format("gui.ae2enhanced.computation.status.offline");
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.label.status", formedStr), x, y, TEXT_BODY);
        y += lineHeight + 4;

        // Parallel limit with bar
        int parallel = tile.getParallelLimit();
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.label.parallel", parallel), x, y, TEXT_BODY);
        y += 12;
        drawBar(x, y, x + 140, 8, 1.0f, BAR_BG, BAR_FILL);
        y += 14;

        // Active orders
        int orders = tile.getActiveOrderCount();
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.label.active_orders", orders), x, y, TEXT_BODY);
        y += lineHeight;

        // Max orders from config
        int maxOrders = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().crafting.maxActiveOrders;
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.label.queue_capacity", maxOrders), x, y, TEXT_BODY);
        y += lineHeight + 4;

        // Divider
        drawRect(x, y, guiLeft + xSize - 20, y + 1, 0xFF878FA5);
        y += 6;

        if (orders == 0) {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.computation.orders.empty"), x, y, TEXT_DIM);
        } else {
            java.util.List<com.github.aeddddd.ae2enhanced.tile.ComputationCoreTaskSummary> summaries = tile.getTaskSummaries();
            for (int i = 0; i < summaries.size() && i < 3; i++) {
                com.github.aeddddd.ae2enhanced.tile.ComputationCoreTaskSummary summary = summaries.get(i);
                String output = summary.output.isEmpty() ? summary.name : summary.output.getDisplayName();
                String progress = summary.startItemCount > 0
                        ? " " + (summary.startItemCount - summary.remainingItemCount) + "/" + summary.startItemCount : "";
                fontRenderer.drawString(fontRenderer.trimStringToWidth(output + progress, xSize - 40), x, y, TEXT_DIM);
                y += 12;
            }
        }

        // Bottom hint
        String hint = I18n.format("gui.ae2enhanced.computation.hint.close");
        int hintW = fontRenderer.getStringWidth(hint);
        fontRenderer.drawString(hint, guiLeft + (xSize - hintW) / 2, guiTop + ySize - 18, TEXT_DIM);
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
            this.mc.player.closeScreen();
        }
        super.keyTyped(typedChar, keyCode);
    }
}
