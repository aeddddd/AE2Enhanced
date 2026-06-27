package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerHyperdimensionalNexus;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import java.io.IOException;


/**
 * 超维度仓储中枢信息面板.
 * 使用 2.png 纹理绘制背景,包含玩家背包和快捷栏.
 */
public class GuiHyperdimensionalNexus extends GuiContainer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/2.png");

    /** 深色字体,在 2.png 浅灰背景上提高对比度 */
    private static final int TEXT_DARK = 0xFF222222;

    private final TileHyperdimensionalController tile;

    public GuiHyperdimensionalNexus(InventoryPlayer playerInv, TileHyperdimensionalController tile) {
        super(new ContainerHyperdimensionalNexus(playerInv));
        this.tile = tile;
        this.xSize = 176;
        this.ySize = 190;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.85F, 0.85F, 1.0F);
        float invScale = 1.0F / 0.85F;

        // 标题 (缩放后坐标需除以 0.85)
        String title = I18n.format("gui.ae2enhanced.nexus.title");
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, (int)((xSize - titleWidth) * invScale / 2), 10, TEXT_DARK);

        // 分隔线
        int sepY = (int)(22 * invScale);
        drawRect(16, sepY, xSize - 16, sepY + 1, GuiColors.ACCENT_SOFT);

        // 安全模式警告横幅
        if (tile != null && tile.getClientSafeMode()) {
            int bannerY = (int)(26 * invScale);
            drawRect(10, bannerY, xSize - 10, bannerY + 10, 0x55ff0000);
            String warn = I18n.format("gui.ae2enhanced.nexus.safe_mode");
            int warnW = fontRenderer.getStringWidth(warn);
            fontRenderer.drawString(warn, (int)((xSize - warnW) * invScale / 2), bannerY + 1, 0xFFffaaaa);
        }

        if (tile == null) {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.tile_unavailable"),
                (int)(20 * invScale), (int)(34 * invScale), GuiColors.TEXT_ERROR);
            GlStateManager.popMatrix();
            return;
        }

        int x = (int)(20 * invScale);
        int y = (int)(34 * invScale);
        if (tile != null && tile.getClientSafeMode()) {
            y += (int)(12 * invScale);
        }
        int lineHeight = (int)(11 * invScale);

        // 结构状态
        String formedStr = tile.isFormed()
                ? I18n.format("gui.ae2enhanced.nexus.structure.formed")
                : I18n.format("gui.ae2enhanced.nexus.structure.unformed");
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.label.structure", formedStr), x, y, TEXT_DARK);
        y += lineHeight;

        // 网络状态
        String networkStr = tile.isNetworkActive()
                ? I18n.format("gui.ae2enhanced.nexus.network.online")
                : I18n.format("gui.ae2enhanced.nexus.network.offline");
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.label.network", networkStr), x, y, TEXT_DARK);
        y += lineHeight;

        // 能源状态
        String powerStr = tile.isNetworkPowered()
                ? I18n.format("gui.ae2enhanced.nexus.power.ok")
                : I18n.format("gui.ae2enhanced.nexus.power.none");
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.label.power", powerStr), x, y, TEXT_DARK);
        y += lineHeight;

        // Nexus ID
        String nexusLabel;
        if (tile.getNexusId() != null) {
            String id = tile.getNexusId().toString().substring(0, 8);
            nexusLabel = I18n.format("gui.ae2enhanced.nexus.label.nexus_id", id + "...");
        } else {
            nexusLabel = I18n.format("gui.ae2enhanced.nexus.label.nexus_id", I18n.format("gui.ae2enhanced.nexus.nexus_id.none"));
        }
        fontRenderer.drawString(nexusLabel, x, y, TEXT_DARK);
        y += lineHeight;

        // 存储统计
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        int types = tile.getClientStorageTypes();
        String total = tile.getClientStorageTotal();
        if (shift) {
            total = TileHyperdimensionalController.toScientificNotation(
                    new java.math.BigInteger(tile.getClientStorageTotalRaw()));
        }
        if (types > 0) {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.label.storage_types", types), x, y, TEXT_DARK);
            y += lineHeight;
            String totalLine = I18n.format("gui.ae2enhanced.nexus.label.storage_total", total);
            fontRenderer.drawString(totalLine, x, y, TEXT_DARK);
        } else {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.storage.empty"), x, y, TEXT_DARK);
        }

        GlStateManager.popMatrix();
    }

    @Override
    public void renderHoveredToolTip(int mouseX, int mouseY) {
        super.renderHoveredToolTip(mouseX, mouseY);

        if (tile == null) return;

        // Tooltip: show raw or scientific notation on hover over storage stats
        int x = 20;
        int y = 34;
        if (tile.getClientSafeMode()) {
            y += 12;
        }
        int lineHeight = 11;
        int storageYStart = y + lineHeight * 4;
        int storageYEnd;
        int types = tile.getClientStorageTypes();
        if (types > 0) {
            storageYEnd = storageYStart + lineHeight * 2;
        } else {
            storageYEnd = storageYStart + lineHeight;
        }

        if (mouseX >= guiLeft + x && mouseX <= guiLeft + xSize - 20
                && mouseY >= guiTop + storageYStart && mouseY <= guiTop + storageYEnd) {
            String display;
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            if (shift) {
                display = TileHyperdimensionalController.toScientificNotation(
                        new java.math.BigInteger(tile.getClientStorageTotalRaw()));
            } else {
                display = tile.getClientStorageTotalRaw();
            }
            if (display != null) {
                java.util.List<String> lines = new java.util.ArrayList<>();
                lines.add("§7" + display);
                drawHoveringText(lines, mouseX, mouseY);
            }
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
