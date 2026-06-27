package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyPattern;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPatternPage;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

public class GuiAssemblyPattern extends GuiContainer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/3.png");

    private static final int PREV_BUTTON_ID = 1;
    private static final int NEXT_BUTTON_ID = 2;

    // 上一页按钮：纹理坐标 7,178 -> 63,198，尺寸 56×20
    private static final int PREV_X = 7;
    private static final int PREV_Y = 178;
    private static final int BTN_W = 56;
    private static final int BTN_H = 20;

    // 下一页按钮：纹理坐标 257,178 -> 313,198，尺寸 56×20
    private static final int NEXT_X = 257;
    private static final int NEXT_Y = 178;

    // 高亮条纹理坐标 0,247，尺寸 56×20
    private static final int HIGHLIGHT_U = 0;
    private static final int HIGHLIGHT_V = 247;

    private final TileAssemblyController tile;
    private int page;

    public GuiAssemblyPattern(InventoryPlayer playerInv, TileAssemblyController tile, int page, int patternPages) {
        super(new ContainerAssemblyPattern(playerInv, tile, page, patternPages));
        this.xSize = 320;
        this.ySize = 228;
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
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawModalRectWithCustomSizedTexture(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize, 512, 512);

        // 鼠标悬停时复制高亮条覆盖按钮
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
        String title = I18n.format("gui.ae2enhanced.pattern.title");
        this.fontRenderer.drawString(title,
                (this.xSize - this.fontRenderer.getStringWidth(title)) / 2, 8, 0xFF00ccff);

        String pageStr = I18n.format("gui.ae2enhanced.pattern.page",
                page + 1, tile.getPatternPages());
        this.fontRenderer.drawString(pageStr,
                (this.xSize - this.fontRenderer.getStringWidth(pageStr)) / 2, 200, GuiColors.TEXT_DIM);

        // 上一页按钮文字
        String prevText = I18n.format("gui.ae2enhanced.pattern.prev");
        int prevTextWidth = this.fontRenderer.getStringWidth(prevText);
        int prevTextX = PREV_X + (BTN_W - prevTextWidth) / 2;
        int prevTextY = PREV_Y + (BTN_H - 8) / 2;
        int prevColor = isPrevEnabled() ? 0xFFFFFFFF : 0xFF888888;
        this.fontRenderer.drawString(prevText, prevTextX, prevTextY, prevColor);

        // 下一页按钮文字
        String nextText = I18n.format("gui.ae2enhanced.pattern.next");
        int nextTextWidth = this.fontRenderer.getStringWidth(nextText);
        int nextTextX = NEXT_X + (BTN_W - nextTextWidth) / 2;
        int nextTextY = NEXT_Y + (BTN_H - 8) / 2;
        int nextColor = isNextEnabled() ? 0xFFFFFFFF : 0xFF888888;
        this.fontRenderer.drawString(nextText, nextTextX, nextTextY, nextColor);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int maxPage = tile.getPatternPages() - 1;
            if (isPrevEnabled() && isMouseOverPrevButton(mouseX, mouseY)) {
                int targetPage = Math.max(0, page - 1);
                AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), targetPage));
                this.mc.getSoundHandler().playSound(
                    net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                        SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return;
            }
            if (isNextEnabled() && isMouseOverNextButton(mouseX, mouseY)) {
                int targetPage = Math.min(maxPage, page + 1);
                AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), targetPage));
                this.mc.getSoundHandler().playSound(
                    net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                        SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean isPrevEnabled() {
        return page > 0;
    }

    private boolean isNextEnabled() {
        return page < tile.getPatternPages() - 1;
    }

    private boolean isMouseOverPrevButton(int mouseX, int mouseY) {
        return mouseX >= guiLeft + PREV_X && mouseX < guiLeft + PREV_X + BTN_W
            && mouseY >= guiTop + PREV_Y && mouseY < guiTop + PREV_Y + BTN_H;
    }

    private boolean isMouseOverNextButton(int mouseX, int mouseY) {
        return mouseX >= guiLeft + NEXT_X && mouseX < guiLeft + NEXT_X + BTN_W
            && mouseY >= guiTop + NEXT_Y && mouseY < guiTop + NEXT_Y + BTN_H;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        int maxPage = tile.getPatternPages() - 1;
        if (page > maxPage && maxPage >= 0) {
            mc.player.openGui(AE2Enhanced.instance, GuiHandler.encodePatternId(maxPage, tile.getPatternPages()),
                mc.world, tile.getPos().getX(), tile.getPos().getY(), tile.getPos().getZ());
            return;
        }
    }
}
