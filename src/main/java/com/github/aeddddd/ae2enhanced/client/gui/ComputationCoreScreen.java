package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 超因果计算核心 GUI。
 */
public class ComputationCoreScreen extends AbstractContainerScreen<ComputationCoreMenu> {

    public ComputationCoreScreen(ComputationCoreMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
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
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        graphics.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF373737);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);

        int line = this.titleLabelY + 20;
        Component status = this.menu.isFormed()
                ? Component.translatable("gui.ae2enhanced.status.online")
                : Component.translatable("gui.ae2enhanced.status.offline");
        graphics.drawString(this.font, status, 10, line, 0x404040, false);
        line += 12;

        Component poolSize = Component.translatable("gui.ae2enhanced.computation_core.pool_size", this.menu.getPoolSize());
        graphics.drawString(this.font, poolSize, 10, line, 0x404040, false);
        line += 12;

        Component activeJobs = Component.translatable("gui.ae2enhanced.computation_core.active_jobs", this.menu.getActiveJobs());
        graphics.drawString(this.font, activeJobs, 10, line, 0x404040, false);
        line += 12;

        Component maxParallel = Component.translatable("gui.ae2enhanced.computation_core.max_parallel", this.menu.getMaxParallel());
        graphics.drawString(this.font, maxParallel, 10, line, 0x404040, false);
    }
}
