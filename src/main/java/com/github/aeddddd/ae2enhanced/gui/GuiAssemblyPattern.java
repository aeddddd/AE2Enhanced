package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyPattern;
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

public class GuiAssemblyPattern extends GuiContainer {

    private static final int PANEL_BG = 0xFF1a1a2e;
    private static final int PANEL_LIGHT = 0xFF16213e;
    private static final int BORDER_DIM = 0xFF0f3460;
    private static final int SLOT_BORDER = 0xFF333355;
    private static final int SLOT_HOVER = 0xFF555577;
    private static final int ACCENT = 0xFF00d4ff;
    private static final int ACCENT_SOFT = 0xFF0f4c75;
    private static final int TEXT_MAIN = 0xFFe0e0e0;
    private static final int TEXT_DIM = 0xFF88aaaa;

    private final TileAssemblyController tile;
    private final int page;
    private GuiButtonTech backButton;
    private GuiButtonTech prevButton;
    private GuiButtonTech nextButton;

    public GuiAssemblyPattern(InventoryPlayer playerInv, TileAssemblyController tile, int page) {
        super(new ContainerAssemblyPattern(playerInv, tile, page));
        this.tile = tile;
        // 页码边界保护（与 Container 保持一致）
        int maxPage = tile.getPatternPages() - 1;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;
        this.page = page;
        this.xSize = 340;
        this.ySize = 250;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
        this.drawCustomTooltips(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // 主背景
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, PANEL_BG);

        // 内面板区域（样板槽区域）
        drawRect(guiLeft + 8, guiTop + 22, guiLeft + xSize - 8, guiTop + 146, PANEL_LIGHT);

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
        drawRect(guiLeft + 8, guiTop + 22, guiLeft + xSize - 8, guiTop + 23, BORDER_DIM);
        drawRect(guiLeft + 8, guiTop + 145, guiLeft + xSize - 8, guiTop + 146, BORDER_DIM);

        // 绘制所有 slot 边框
        drawSlotBorders(mouseX, mouseY);
    }

    private void drawSlotBorders(int mouseX, int mouseY) {
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!slot.isEnabled()) continue;
            int x = guiLeft + slot.xPos;
            int y = guiTop + slot.yPos;

            boolean hovered = this.isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY);
            int color = hovered ? SLOT_HOVER : SLOT_BORDER;

            drawRect(x - 1, y - 1, x + 18, y, color);
            drawRect(x - 1, y + 16, x + 18, y + 17, color);
            drawRect(x - 1, y, x, y + 16, color);
            drawRect(x + 16, y, x + 17, y + 16, color);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题
        String title = I18n.format("gui.ae2enhanced.pattern.title");
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, (xSize - titleWidth) / 2, 8, ACCENT);

        // 页码显示
        String pageStr = I18n.format("gui.ae2enhanced.pattern.page", page + 1, tile.getPatternPages());
        int pageWidth = fontRenderer.getStringWidth(pageStr);
        fontRenderer.drawString(pageStr, (xSize - pageWidth) / 2, 148, TEXT_DIM);

        // 样板槽标签
        String patternLabel = I18n.format("gui.ae2enhanced.formed.patterns");
        fontRenderer.drawString(patternLabel, 16, 24, TEXT_DIM);

        // 分隔线
        drawRect(16, 40, xSize - 16, 41, ACCENT_SOFT);

        // 背包上方分隔线
        drawRect(16, 148, xSize - 16, 149, ACCENT_SOFT);
    }

    @Override
    public void initGui() {
        super.initGui();
        backButton = new GuiButtonTech(0, guiLeft + xSize - 90, guiTop + 8, 80, 18,
            I18n.format("gui.ae2enhanced.pattern.back"));
        buttonList.add(backButton);

        // 上一页按钮（第0页禁用）
        prevButton = new GuiButtonTech(1, guiLeft + 16, guiTop + 8, 50, 18,
            I18n.format("gui.ae2enhanced.pattern.prev"));
        prevButton.enabled = page > 0;
        buttonList.add(prevButton);

        // 下一页按钮（最后一页禁用）
        nextButton = new GuiButtonTech(2, guiLeft + 72, guiTop + 8, 50, 18,
            I18n.format("gui.ae2enhanced.pattern.next"));
        nextButton.enabled = page < tile.getPatternPages() - 1;
        buttonList.add(nextButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            // 返回一级页面
            mc.player.openGui(AE2Enhanced.instance, GuiHandler.GUI_ASSEMBLY_CONTROLLER,
                mc.world, tile.getPos().getX(), tile.getPos().getY(), tile.getPos().getZ());
        } else if (button.id == 1) {
            // 上一页
            int targetPage = page - 1;
            if (targetPage >= 0) {
                AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), targetPage));
            }
        } else if (button.id == 2) {
            // 下一页
            int targetPage = page + 1;
            if (targetPage < tile.getPatternPages()) {
                AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), targetPage));
            }
        }
    }

    private void drawCustomTooltips(int mouseX, int mouseY) {
        if (isPointInRegion(8, 22, 324, 124, mouseX, mouseY)) {
            List<String> lines = new ArrayList<>();
            lines.add(I18n.format("gui.ae2enhanced.tooltip.patterns"));
            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.patterns.desc") + "§r");
            this.drawHoveringText(lines, mouseX, mouseY);
        }
    }
}
