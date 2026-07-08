package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.AssemblyPagePacket;

/**
 * 装配枢纽样板分页 GUI。
 */
public class AssemblyPatternScreen extends AbstractContainerScreen<AssemblyPatternMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/3.png");

    private static final int PREV_X = 7;
    private static final int PREV_Y = 178;
    private static final int NEXT_X = 257;
    private static final int NEXT_Y = 178;
    private static final int BTN_W = 56;
    private static final int BTN_H = 20;
    private static final int HIGHLIGHT_U = 0;
    private static final int HIGHLIGHT_V = 247;

    public AssemblyPatternScreen(AssemblyPatternMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 320;
        this.imageHeight = 228;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 512, 512);
        if (isPrevEnabled() && isMouseOverPrevButton(mouseX, mouseY)) {
            graphics.blit(TEXTURE, this.leftPos + PREV_X, this.topPos + PREV_Y,
                    HIGHLIGHT_U, HIGHLIGHT_V, BTN_W, BTN_H, 512, 512);
        }
        if (isNextEnabled() && isMouseOverNextButton(mouseX, mouseY)) {
            graphics.blit(TEXTURE, this.leftPos + NEXT_X, this.topPos + NEXT_Y,
                    HIGHLIGHT_U, HIGHLIGHT_V, BTN_W, BTN_H, 512, 512);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component title = Component.translatable("gui.ae2enhanced.pattern.title");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, (this.imageWidth - titleWidth) / 2, 8, 0xFF00ccff, false);

        int page = this.menu.getPage() + 1;
        int total = this.menu.getTotalPages();
        Component pageStr = Component.translatable("gui.ae2enhanced.pattern.page", page, total);
        int pageWidth = this.font.width(pageStr);
        graphics.drawString(this.font, pageStr, (this.imageWidth - pageWidth) / 2, 200, GuiColors.TEXT_DIM, false);

        Component prevText = Component.translatable("gui.ae2enhanced.pattern.prev");
        int prevTextWidth = this.font.width(prevText);
        int prevTextX = PREV_X + (BTN_W - prevTextWidth) / 2;
        int prevTextY = PREV_Y + (BTN_H - 8) / 2;
        int prevColor = isPrevEnabled() ? 0xFFFFFFFF : 0xFF888888;
        graphics.drawString(this.font, prevText, prevTextX, prevTextY, prevColor, false);

        Component nextText = Component.translatable("gui.ae2enhanced.pattern.next");
        int nextTextWidth = this.font.width(nextText);
        int nextTextX = NEXT_X + (BTN_W - nextTextWidth) / 2;
        int nextTextY = NEXT_Y + (BTN_H - 8) / 2;
        int nextColor = isNextEnabled() ? 0xFFFFFFFF : 0xFF888888;
        graphics.drawString(this.font, nextText, nextTextX, nextTextY, nextColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isPrevEnabled() && isMouseOverPrevButton((int) mouseX, (int) mouseY)) {
                int target = Math.max(0, this.menu.getPage() - 1);
                ModNetwork.CHANNEL.sendToServer(new AssemblyPagePacket(this.menu.getControllerPos(), target));
                return true;
            }
            if (isNextEnabled() && isMouseOverNextButton((int) mouseX, (int) mouseY)) {
                int target = Math.min(this.menu.getTotalPages() - 1, this.menu.getPage() + 1);
                ModNetwork.CHANNEL.sendToServer(new AssemblyPagePacket(this.menu.getControllerPos(), target));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isPrevEnabled() {
        return this.menu.getPage() > 0;
    }

    private boolean isNextEnabled() {
        return this.menu.getPage() < this.menu.getTotalPages() - 1;
    }

    private boolean isMouseOverPrevButton(int mouseX, int mouseY) {
        return mouseX >= this.leftPos + PREV_X && mouseX < this.leftPos + PREV_X + BTN_W
                && mouseY >= this.topPos + PREV_Y && mouseY < this.topPos + PREV_Y + BTN_H;
    }

    private boolean isMouseOverNextButton(int mouseX, int mouseY) {
        return mouseX >= this.leftPos + NEXT_X && mouseX < this.leftPos + NEXT_X + BTN_W
                && mouseY >= this.topPos + NEXT_Y && mouseY < this.topPos + NEXT_Y + BTN_H;
    }
}
