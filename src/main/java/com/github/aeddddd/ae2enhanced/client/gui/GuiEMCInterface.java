package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerEMCInterface;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

/**
 * EMC 接口 GUI.
 *
 * <p>复用 3.png 纹理图集配套布局：白名单过滤格起点 (8,25)、17 列 × 6 行，
 * 坐标与装配枢纽 ContainerAssemblyPattern 的位置代码保持一致；
 * 玩家背包 (80,145) 与快捷栏 (80,203) 同样沿用装配枢纽坐标；
 * 左右翻页按钮使用 3.png 按钮 UV。</p>
 */
public class GuiEMCInterface extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/3.png");

    // 3.png 左右按钮 UV（与 GuiAssemblyPattern 一致）
    private static final int PREV_X = 7;
    private static final int PREV_Y = 178;
    private static final int NEXT_X = 257;
    private static final int NEXT_Y = 178;
    private static final int BTN_W = 56;
    private static final int BTN_H = 20;

    private static final int HIGHLIGHT_U = 0;
    private static final int HIGHLIGHT_V = 247;

    private final TileEMCInterface tile;
    private final ContainerEMCInterface container;

    public GuiEMCInterface(InventoryPlayer playerInventory, ContainerEMCInterface container) {
        super(container);
        this.tile = container.getTile();
        this.container = container;
        this.xSize = 320;
        this.ySize = 228;
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
        this.drawModalRectWithCustomSizedTexture(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize, 512, 512);

        // 上一页/下一页高亮
        if (isPrevEnabled() && isMouseOverPrevButton(mouseX, mouseY)) {
            this.drawModalRectWithCustomSizedTexture(
                    this.guiLeft + PREV_X, this.guiTop + PREV_Y,
                    HIGHLIGHT_U, HIGHLIGHT_V, BTN_W, BTN_H, 512, 512);
        }
        if (isNextEnabled() && isMouseOverNextButton(mouseX, mouseY)) {
            this.drawModalRectWithCustomSizedTexture(
                    this.guiLeft + NEXT_X, this.guiTop + NEXT_Y,
                    HIGHLIGHT_U, HIGHLIGHT_V, BTN_W, BTN_H, 512, 512);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.emc_interface.title");
        this.fontRenderer.drawString(title,
                (this.xSize - this.fontRenderer.getStringWidth(title)) / 2, 6, 0x404040);

        String pageText = String.format("%d/%d", this.container.getCurrentPage() + 1, TileEMCInterface.WHITELIST_PAGES);
        this.fontRenderer.drawString(pageText,
                (this.xSize - this.fontRenderer.getStringWidth(pageText)) / 2, 182, 0x404040);

        String prevText = I18n.format("gui.ae2enhanced.emc_interface.prev");
        String nextText = I18n.format("gui.ae2enhanced.emc_interface.next");
        drawButtonText(prevText, PREV_X, PREV_Y, isPrevEnabled() ? 0xFFFFFFFF : 0xFF888888);
        drawButtonText(nextText, NEXT_X, NEXT_Y, isNextEnabled() ? 0xFFFFFFFF : 0xFF888888);

        String owner = tile.isBound() ? tile.getOwnerName() : I18n.format("gui.ae2enhanced.emc_interface.unbound");
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.emc_interface.owner", owner), 8, 138, 0x404040);

        if (tile.isBound()) {
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.emc_interface.shift_to_bind"), 8, 148, 0xFFAA00);
        }
    }

    private void drawButtonText(String text, int x, int y, int color) {
        int w = this.fontRenderer.getStringWidth(text);
        int tx = x + (BTN_W - w) / 2;
        int ty = y + (BTN_H - 8) / 2;
        this.fontRenderer.drawString(text, tx, ty, color);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            // Shift+左键点击标题区域绑定当前玩家
            if (isShiftKeyDown()
                    && mouseX >= this.guiLeft + 8 && mouseX <= this.guiLeft + 100
                    && mouseY >= this.guiTop + 6 && mouseY <= this.guiTop + 18) {
                AE2Enhanced.network.sendToServer(
                        new com.github.aeddddd.ae2enhanced.network.packet.PacketEMCInterfaceBind(tile.getPos()));
                return;
            }

            if (isPrevEnabled() && isMouseOverPrevButton(mouseX, mouseY)) {
                this.container.setCurrentPage(this.container.getCurrentPage() - 1);
                playClickSound();
                return;
            }
            if (isNextEnabled() && isMouseOverNextButton(mouseX, mouseY)) {
                this.container.setCurrentPage(this.container.getCurrentPage() + 1);
                playClickSound();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void playClickSound() {
        this.mc.getSoundHandler().playSound(
                net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private boolean isPrevEnabled() {
        return this.container.getCurrentPage() > 0;
    }

    private boolean isNextEnabled() {
        return this.container.getCurrentPage() < TileEMCInterface.WHITELIST_PAGES - 1;
    }

    private boolean isMouseOverPrevButton(int mouseX, int mouseY) {
        return mouseX >= guiLeft + PREV_X && mouseX < guiLeft + PREV_X + BTN_W
                && mouseY >= guiTop + PREV_Y && mouseY < guiTop + PREV_Y + BTN_H;
    }

    private boolean isMouseOverNextButton(int mouseX, int mouseY) {
        return mouseX >= guiLeft + NEXT_X && mouseX < guiLeft + NEXT_X + BTN_W
                && mouseY >= guiTop + NEXT_Y && mouseY < guiTop + NEXT_Y + BTN_H;
    }
}
