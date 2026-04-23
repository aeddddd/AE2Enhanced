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

public class GuiAssemblyPattern extends GuiContainer {

    private static final int PANEL_BG = 0xFF1a1a2e;
    private static final int PANEL_LIGHT = 0xFF16213e;
    private static final int BORDER_DIM = 0xFF0f3460;
    private static final int SLOT_BORDER = 0xFF333355;
    private static final int SLOT_HOVER = 0xFF555577;
    private static final int ACCENT = 0xFF00d4ff;
    private static final int ACCENT_SOFT = 0xFF0f4c75;
    private static final int TEXT_DIM = 0xFF88aaaa;

    private static final int PREV_BUTTON_ID = 1;
    private static final int NEXT_BUTTON_ID = 2;

    private final TileAssemblyController tile;
    private int page;
    private GuiButtonTech prevButton;
    private GuiButtonTech nextButton;

    public GuiAssemblyPattern(InventoryPlayer playerInv, TileAssemblyController tile, int page) {
        super(new ContainerAssemblyPattern(playerInv, tile, page));
        this.xSize = 350;
        this.ySize = 234;
        this.tile = tile;
        this.page = page;
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

        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, PANEL_BG);
        drawRect(guiLeft + 8, guiTop + 22, guiLeft + 332, guiTop + 146, PANEL_LIGHT);
        drawRect(guiLeft + 87, guiTop + 150, guiLeft + 253, guiTop + 208, PANEL_LIGHT);
        drawRect(guiLeft + 87, guiTop + 208, guiLeft + 253, guiTop + 230, PANEL_LIGHT);

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

        drawRect(guiLeft + 8, guiTop + 22, guiLeft + 332, guiTop + 23, BORDER_DIM);
        drawRect(guiLeft + 8, guiTop + 145, guiLeft + 332, guiTop + 146, BORDER_DIM);
        drawRect(guiLeft + 87, guiTop + 150, guiLeft + 253, guiTop + 151, BORDER_DIM);
        drawRect(guiLeft + 87, guiTop + 207, guiLeft + 253, guiTop + 208, BORDER_DIM);
        drawRect(guiLeft + 87, guiTop + 208, guiLeft + 253, guiTop + 209, BORDER_DIM);
        drawRect(guiLeft + 87, guiTop + 229, guiLeft + 253, guiTop + 230, BORDER_DIM);

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
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        int cx = (this.width - this.xSize) / 2;
        int cy = (this.height - this.ySize) / 2;

        this.prevButton = new GuiButtonTech(PREV_BUTTON_ID, cx + 20, cy + 200, 40, 16,
                I18n.format("gui.ae2enhanced.pattern.prev"));
        this.nextButton = new GuiButtonTech(NEXT_BUTTON_ID, cx + 290, cy + 200, 40, 16,
                I18n.format("gui.ae2enhanced.pattern.next"));

        this.buttonList.add(this.prevButton);
        this.buttonList.add(this.nextButton);

        updateButtonStates();
    }

    private void updateButtonStates() {
        int maxPage = tile.getPatternPages() - 1;
        if (this.prevButton != null) {
            this.prevButton.enabled = page > 0;
        }
        if (this.nextButton != null) {
            this.nextButton.enabled = page < maxPage;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateButtonStates();

        int maxPage = tile.getPatternPages() - 1;
        if (page > maxPage && maxPage >= 0) {
            mc.player.openGui(AE2Enhanced.instance, GuiHandler.encodePatternId(maxPage),
                mc.world, tile.getPos().getX(), tile.getPos().getY(), tile.getPos().getZ());
            return;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int maxPage = tile.getPatternPages() - 1;
        if (button.id == PREV_BUTTON_ID) {
            int targetPage = Math.max(0, page - 1);
            AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), targetPage));
        } else if (button.id == NEXT_BUTTON_ID) {
            int targetPage = Math.min(maxPage, page + 1);
            AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), targetPage));
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.pattern.title");
        this.fontRenderer.drawString(title,
                (this.xSize - this.fontRenderer.getStringWidth(title)) / 2, 8, ACCENT);

        String pageStr = I18n.format("gui.ae2enhanced.pattern.page",
                page + 1, tile.getPatternPages());
        this.fontRenderer.drawString(pageStr,
                (this.xSize - this.fontRenderer.getStringWidth(pageStr)) / 2, 200, TEXT_DIM);
    }
}
