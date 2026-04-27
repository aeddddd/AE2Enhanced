package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyFormed;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.PacketPatternPage;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiAssemblyFormed extends GuiContainer {

    private static final int PANEL_BG = 0xFF1a1a2e;
    private static final int PANEL_LIGHT = 0xFF16213e;
    private static final int BORDER_DIM = 0xFF0f3460;
    private static final int SLOT_BORDER = 0xFF333355;
    private static final int SLOT_HOVER = 0xFF555577;
    private static final int ACCENT = 0xFF00d4ff;
    private static final int ACCENT_SOFT = 0xFF0f4c75;
    private static final int TEXT_MAIN = 0xFFe0e0e0;
    private static final int TEXT_SUCCESS = 0xFF55ff88;
    private static final int TEXT_DIM = 0xFF88aaaa;
    private static final int TEXT_WARN = 0xFFffaa55;
    private static final int TEXT_ERROR = 0xFFff5555;

    private final TileAssemblyController tile;
    private GuiButtonTech patternButton;

    public GuiAssemblyFormed(InventoryPlayer playerInv, TileAssemblyController tile) {
        super(new ContainerAssemblyFormed(playerInv, tile));
        this.tile = tile;
        this.xSize = 280;
        this.ySize = 270;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (!this.drawCustomTooltips(mouseX, mouseY)) {
            this.renderHoveredToolTip(mouseX, mouseY);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // 主背景
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, PANEL_BG);

        // 内面板区域
        drawRect(guiLeft + 10, guiTop + 26, guiLeft + xSize - 10, guiTop + 170, PANEL_LIGHT);

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

        // 内面板边框
        drawRect(guiLeft + 10, guiTop + 26, guiLeft + xSize - 10, guiTop + 27, BORDER_DIM);
        drawRect(guiLeft + 10, guiTop + 169, guiLeft + xSize - 10, guiTop + 170, BORDER_DIM);

        // 绘制所有 slot 边框
        drawSlotBorders(mouseX, mouseY);
    }

    private void drawSlotBorders(int mouseX, int mouseY) {
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!slot.isEnabled()) continue;
            int x = guiLeft + slot.xPos;
            int y = guiTop + slot.yPos;

            // 判定鼠标是否悬停在这个 slot 上
            boolean hovered = this.isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY);
            int color = hovered ? SLOT_HOVER : SLOT_BORDER;

            // 外边框
            drawRect(x - 1, y - 1, x + 18, y, color);
            drawRect(x - 1, y + 16, x + 18, y + 17, color);
            drawRect(x - 1, y, x, y + 16, color);
            drawRect(x + 16, y, x + 17, y + 16, color);
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        patternButton = new GuiButtonTech(0, guiLeft + 90, guiTop + 28, 120, 20, I18n.format("gui.ae2enhanced.formed.open_patterns"));
        buttonList.add(patternButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            // 通过 PacketPatternPage 让服务端打开样板 GUI，确保第一次打开与翻页使用完全相同的代码路径
            AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), 0));
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题
        String title = I18n.format("gui.ae2enhanced.formed.title");
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, (xSize - titleWidth) / 2, 8, ACCENT);

        // 升级槽标签
        String upgradeLabel = I18n.format("gui.ae2enhanced.formed.upgrades");
        fontRenderer.drawString(upgradeLabel, 16, 28, TEXT_DIM);

        // 分隔线
        drawRect(16, 40, 78, 41, ACCENT_SOFT);

        // 并行状态
        long parallelCap = tile.getParallelCap();
        String parallelText;
        if (parallelCap >= Long.MAX_VALUE / 2) {
            parallelText = I18n.format("gui.ae2enhanced.formed.parallel.infinite");
        } else {
            parallelText = I18n.format("gui.ae2enhanced.formed.parallel", parallelCap);
        }
        fontRenderer.drawString(parallelText, 16, 130, TEXT_DIM);

        // 活跃任务数
        String jobs = I18n.format("gui.ae2enhanced.formed.jobs", tile.getJobCount());
        fontRenderer.drawString(jobs, 16, 142, TEXT_DIM);

        // 网络状态
        String netStatus;
        int netColor;
        if (tile.isNetworkActive()) {
            netStatus = I18n.format("gui.ae2enhanced.formed.network.active");
            netColor = TEXT_SUCCESS;
        } else if (tile.isNetworkPowered()) {
            netStatus = I18n.format("gui.ae2enhanced.formed.network.booting");
            netColor = TEXT_WARN;
        } else {
            netStatus = I18n.format("gui.ae2enhanced.formed.network.offline");
            netColor = TEXT_ERROR;
        }
        int nw = fontRenderer.getStringWidth(netStatus);
        fontRenderer.drawString(netStatus, xSize - 16 - nw, 130, netColor);

        // 背包上方分隔线
        drawRect(16, 176, xSize - 16, 177, ACCENT_SOFT);
    }

    private boolean drawCustomTooltips(int mouseX, int mouseY) {
        // 升级槽区域 tooltip
        if (isPointInRegion(16, 40, 58, 38, mouseX, mouseY)) {
            List<String> lines = new ArrayList<>();
            lines.add(I18n.format("gui.ae2enhanced.tooltip.upgrades"));
            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.parallel") + "§r");
            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.speed") + "§r");
            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.efficiency") + "§r");
            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.capacity") + "§r");
            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.upload") + "§r");
            this.drawHoveringText(lines, mouseX, mouseY);
            return true;
        }
        // 样板存储按钮 tooltip
        if (patternButton != null && patternButton.isMouseOver()) {
            List<String> lines = new ArrayList<>();
            lines.add(I18n.format("gui.ae2enhanced.tooltip.patterns"));
            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.patterns.desc") + "§r");
            this.drawHoveringText(lines, mouseX, mouseY);
            return true;
        }
        // 网络状态区域 tooltip
        if (isPointInRegion(140, 125, 120, 20, mouseX, mouseY)) {
            List<String> lines = new ArrayList<>();
            if (tile.isNetworkActive()) {
                lines.add(I18n.format("gui.ae2enhanced.formed.network.active"));
            } else if (tile.isNetworkPowered()) {
                lines.add(I18n.format("gui.ae2enhanced.formed.network.booting"));
                lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.network.booting") + "§r");
            } else {
                lines.add(I18n.format("gui.ae2enhanced.formed.network.offline"));
                lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.network.offline") + "§r");
            }
            this.drawHoveringText(lines, mouseX, mouseY);
            return true;
        }
        return false;
    }
}
