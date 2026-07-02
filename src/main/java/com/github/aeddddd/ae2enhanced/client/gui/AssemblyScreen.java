package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 装配枢纽控制器 GUI。
 */
public class AssemblyScreen extends AbstractContainerScreen<AssemblyMenu> {

    private static final Component PAGE_DOWN_LABEL = Component.literal("<");
    private static final Component PAGE_UP_LABEL = Component.literal(">");
    private static final String[] UPGRADE_LABELS = { "P", "S", "C", "", "A", "" };

    private Button pageDownButton;
    private Button pageUpButton;

    public AssemblyScreen(AssemblyMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 338;
        this.imageHeight = 244;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int btnY = y + 16;
        int rightX = x + this.imageWidth - 28;

        this.pageDownButton = this.addRenderableWidget(Button.builder(PAGE_DOWN_LABEL, btn -> menu.pageDown())
                .pos(x + 8, btnY)
                .size(20, 20)
                .build());
        this.pageUpButton = this.addRenderableWidget(Button.builder(PAGE_UP_LABEL, btn -> menu.pageUp())
                .pos(rightX, btnY)
                .size(20, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        graphics.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF373737);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);

        Component upgrades = Component.translatable("gui.ae2enhanced.assembly.upgrades");
        graphics.drawString(this.font, upgrades, 8, 6, 0x404040, false);

        Component patterns = Component.translatable("gui.ae2enhanced.assembly.patterns");
        graphics.drawString(this.font, patterns, 8, 32, 0x404040, false);

        int page = menu.getPageIndex() + 1;
        int total = menu.getTotalPages();
        Component pageText = Component.translatable("gui.ae2enhanced.assembly.page", page, total);
        int pageWidth = this.font.width(pageText);
        int pageX = (this.imageWidth - pageWidth) / 2;
        graphics.drawString(this.font, pageText, pageX, 20, 0x404040, false);

        for (int i = 0; i < UPGRADE_LABELS.length; i++) {
            String label = UPGRADE_LABELS[i];
            if (label.isEmpty()) {
                continue;
            }
            int labelWidth = this.font.width(label);
            int slotX = 8 + i * 18;
            int labelX = slotX + (18 - labelWidth) / 2;
            graphics.drawString(this.font, label, labelX, 10, 0x404040, false);
        }
    }
}
