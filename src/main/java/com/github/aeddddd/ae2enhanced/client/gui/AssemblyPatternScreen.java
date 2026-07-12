package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.common.menu.AssemblyPatternMenu;

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

    public AssemblyPatternScreen(AssemblyPatternMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GuiConstants.PATTERN_IMAGE_WIDTH;
        this.imageHeight = GuiConstants.PATTERN_IMAGE_HEIGHT;
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
            graphics.blit(TEXTURE, this.leftPos + GuiConstants.PATTERN_PREV_BUTTON_X, this.topPos + GuiConstants.PATTERN_BUTTON_Y,
                    GuiConstants.PATTERN_HIGHLIGHT_U, GuiConstants.PATTERN_HIGHLIGHT_V, GuiConstants.PATTERN_BUTTON_WIDTH, GuiConstants.PATTERN_BUTTON_HEIGHT, 512, 512);
        }
        if (isNextEnabled() && isMouseOverNextButton(mouseX, mouseY)) {
            graphics.blit(TEXTURE, this.leftPos + GuiConstants.PATTERN_NEXT_BUTTON_X, this.topPos + GuiConstants.PATTERN_BUTTON_Y,
                    GuiConstants.PATTERN_HIGHLIGHT_U, GuiConstants.PATTERN_HIGHLIGHT_V, GuiConstants.PATTERN_BUTTON_WIDTH, GuiConstants.PATTERN_BUTTON_HEIGHT, 512, 512);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component title = Component.translatable("gui.ae2enhanced.pattern.title");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, (this.imageWidth - titleWidth) / 2, GuiConstants.TITLE_LABEL_Y, GuiConstants.PATTERN_TITLE_COLOR, false);

        int page = this.menu.getPage() + 1;
        int total = this.menu.getTotalPages();
        Component pageStr = Component.translatable("gui.ae2enhanced.pattern.page", page, total);
        int pageWidth = this.font.width(pageStr);
        graphics.drawString(this.font, pageStr, (this.imageWidth - pageWidth) / 2, GuiConstants.PATTERN_PAGE_TEXT_Y, GuiColors.TEXT_DIM, false);

        Component prevText = Component.translatable("gui.ae2enhanced.pattern.prev");
        int prevTextWidth = this.font.width(prevText);
        int prevTextX = GuiConstants.PATTERN_PREV_BUTTON_X + (GuiConstants.PATTERN_BUTTON_WIDTH - prevTextWidth) / 2;
        int prevTextY = GuiConstants.PATTERN_BUTTON_Y + (GuiConstants.PATTERN_BUTTON_HEIGHT - 8) / 2;
        int prevColor = isPrevEnabled() ? GuiConstants.BUTTON_TEXT_COLOR : GuiConstants.DISABLED_BUTTON_TEXT_COLOR;
        graphics.drawString(this.font, prevText, prevTextX, prevTextY, prevColor, false);

        Component nextText = Component.translatable("gui.ae2enhanced.pattern.next");
        int nextTextWidth = this.font.width(nextText);
        int nextTextX = GuiConstants.PATTERN_NEXT_BUTTON_X + (GuiConstants.PATTERN_BUTTON_WIDTH - nextTextWidth) / 2;
        int nextTextY = GuiConstants.PATTERN_BUTTON_Y + (GuiConstants.PATTERN_BUTTON_HEIGHT - 8) / 2;
        int nextColor = isNextEnabled() ? GuiConstants.BUTTON_TEXT_COLOR : GuiConstants.DISABLED_BUTTON_TEXT_COLOR;
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
        return mouseX >= this.leftPos + GuiConstants.PATTERN_PREV_BUTTON_X && mouseX < this.leftPos + GuiConstants.PATTERN_PREV_BUTTON_X + GuiConstants.PATTERN_BUTTON_WIDTH
                && mouseY >= this.topPos + GuiConstants.PATTERN_BUTTON_Y && mouseY < this.topPos + GuiConstants.PATTERN_BUTTON_Y + GuiConstants.PATTERN_BUTTON_HEIGHT;
    }

    private boolean isMouseOverNextButton(int mouseX, int mouseY) {
        return mouseX >= this.leftPos + GuiConstants.PATTERN_NEXT_BUTTON_X && mouseX < this.leftPos + GuiConstants.PATTERN_NEXT_BUTTON_X + GuiConstants.PATTERN_BUTTON_WIDTH
                && mouseY >= this.topPos + GuiConstants.PATTERN_BUTTON_Y && mouseY < this.topPos + GuiConstants.PATTERN_BUTTON_Y + GuiConstants.PATTERN_BUTTON_HEIGHT;
    }
}
