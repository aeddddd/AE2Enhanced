package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;


/**
 * 超维度仓储中枢信息面板。
 * 纯展示 GUI，无物品槽位，风格与 GuiHyperdimensionalUnformed 统一。
 */
public class GuiHyperdimensionalNexus extends GuiScreen {

    private static final int PANEL_BG = 0xFF1a1a2e;
    private static final int PANEL_LIGHT = 0xFF16213e;
    private static final int BORDER_DIM = 0xFF0f3460;
    private static final int ACCENT = 0xFF00d4ff;
    private static final int ACCENT_SOFT = 0xFF0f4c75;
    private static final int TEXT_MAIN = 0xFFe0e0e0;
    private static final int TEXT_ERROR = 0xFFff5555;
    private static final int TEXT_SUCCESS = 0xFF55ff88;
    private static final int TEXT_WARN = 0xFFffaa55;

    private final TileHyperdimensionalController tile;
    private int xSize = 240;
    private int ySize = 180;
    private int guiLeft;
    private int guiTop;

    public GuiHyperdimensionalNexus(TileHyperdimensionalController tile) {
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

        // 主背景
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, PANEL_BG);

        // 顶部高亮条
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 2, ACCENT);

        // 外边框
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 1, BORDER_DIM);
        drawRect(guiLeft, guiTop + ySize - 1, guiLeft + xSize, guiTop + ySize, BORDER_DIM);
        drawRect(guiLeft, guiTop, guiLeft + 1, guiTop + ySize, BORDER_DIM);
        drawRect(guiLeft + xSize - 1, guiTop, guiLeft + xSize, guiTop + ySize, BORDER_DIM);

        // 角落装饰
        int corner = 10;
        drawRect(guiLeft, guiTop, guiLeft + corner, guiTop + 2, ACCENT);
        drawRect(guiLeft, guiTop, guiLeft + 2, guiTop + corner, ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop, guiLeft + xSize, guiTop + 2, ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop, guiLeft + xSize, guiTop + corner, ACCENT);
        drawRect(guiLeft, guiTop + ySize - 2, guiLeft + corner, guiTop + ySize, ACCENT);
        drawRect(guiLeft, guiTop + ySize - corner, guiLeft + 2, guiTop + ySize, ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop + ySize - 2, guiLeft + xSize, guiTop + ySize, ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop + ySize - corner, guiLeft + xSize, guiTop + ySize, ACCENT);

        // 内面板区域
        drawRect(guiLeft + 10, guiTop + 36, guiLeft + xSize - 10, guiTop + ySize - 10, PANEL_LIGHT);
        drawRect(guiLeft + 10, guiTop + 36, guiLeft + xSize - 10, guiTop + 37, BORDER_DIM);
        drawRect(guiLeft + 10, guiTop + ySize - 11, guiLeft + xSize - 10, guiTop + ySize - 10, BORDER_DIM);

        // 标题
        String title = I18n.format("gui.ae2enhanced.nexus.title");
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, guiLeft + (xSize - titleWidth) / 2, guiTop + 8, ACCENT);

        // 分隔线
        drawRect(guiLeft + 16, guiTop + 22, guiLeft + xSize - 16, guiTop + 23, ACCENT_SOFT);

        // 安全模式警告横幅
        if (tile != null && tile.getClientSafeMode()) {
            int bannerY = guiTop + 26;
            drawRect(guiLeft + 10, bannerY, guiLeft + xSize - 10, bannerY + 12, 0x55ff0000);
            String warn = I18n.format("gui.ae2enhanced.nexus.safe_mode");
            int warnW = fontRenderer.getStringWidth(warn);
            fontRenderer.drawString(warn, guiLeft + (xSize - warnW) / 2, bannerY + 2, 0xFFffaaaa);
        }

        if (tile == null) {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.tile_unavailable"), guiLeft + 20, guiTop + 40, TEXT_ERROR);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        int x = guiLeft + 20;
        int y = guiTop + 42;
        if (tile != null && tile.getClientSafeMode()) {
            y += 14; // 为安全模式横幅让出空间
        }
        int lineHeight = 14;

        // 结构状态
        String formedStr = tile.isFormed()
                ? I18n.format("gui.ae2enhanced.nexus.structure.formed")
                : I18n.format("gui.ae2enhanced.nexus.structure.unformed");
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.label.structure", formedStr), x, y, TEXT_MAIN);
        y += lineHeight;

        // 网络状态
        String networkStr = tile.isNetworkActive()
                ? I18n.format("gui.ae2enhanced.nexus.network.online")
                : I18n.format("gui.ae2enhanced.nexus.network.offline");
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.label.network", networkStr), x, y, TEXT_MAIN);
        y += lineHeight;

        // 能源状态
        String powerStr = tile.isNetworkPowered()
                ? I18n.format("gui.ae2enhanced.nexus.power.ok")
                : I18n.format("gui.ae2enhanced.nexus.power.none");
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.label.power", powerStr), x, y, TEXT_MAIN);
        y += lineHeight;

        // Nexus ID
        String nexusLabel;
        if (tile.getNexusId() != null) {
            String id = tile.getNexusId().toString().substring(0, 8);
            nexusLabel = I18n.format("gui.ae2enhanced.nexus.label.nexus_id", id + "...");
        } else {
            nexusLabel = I18n.format("gui.ae2enhanced.nexus.label.nexus_id", I18n.format("gui.ae2enhanced.nexus.nexus_id.none"));
        }
        fontRenderer.drawString(nexusLabel, x, y, TEXT_MAIN);
        y += lineHeight;

        // 存储统计（客户端同步字段，含物品+流体）
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        int types = tile.getClientStorageTypes();
        String total = tile.getClientStorageTotal();
        if (shift) {
            total = TileHyperdimensionalController.toScientificNotation(
                    new java.math.BigInteger(tile.getClientStorageTotalRaw()));
        }
        int storageYStart = y;
        int storageYEnd;
        if (types > 0) {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.label.storage_types", types), x, y, TEXT_MAIN);
            y += lineHeight;
            String totalLine = I18n.format("gui.ae2enhanced.nexus.label.storage_total", total);
            fontRenderer.drawString(totalLine, x, y, TEXT_MAIN);
            storageYEnd = y + lineHeight;
        } else {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.nexus.storage.empty"), x, y, TEXT_MAIN);
            storageYEnd = y + lineHeight;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Tooltip: show raw or scientific notation on hover over storage stats
        if (tile != null && mouseX >= x && mouseX <= guiLeft + xSize - 20
                && mouseY >= storageYStart && mouseY <= storageYEnd) {
            String display;
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
