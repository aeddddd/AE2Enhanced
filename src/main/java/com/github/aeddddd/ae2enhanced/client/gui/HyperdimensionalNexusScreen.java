package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;

/**
 * 超维度仓储 Nexus 成形状态的 GUI。
 */
public class HyperdimensionalNexusScreen extends AbstractContainerScreen<HyperdimensionalNexusMenu> {

    public HyperdimensionalNexusScreen(HyperdimensionalNexusMenu menu, Inventory inv, Component title) {
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

        HyperdimensionalControllerBlockEntity controller = this.menu.getController();
        if (controller == null) {
            return;
        }

        Component online = controller.isNetworkActive()
                ? Component.translatable("gui.ae2enhanced.status.online")
                : Component.translatable("gui.ae2enhanced.status.offline");
        Component powered = controller.isNetworkPowered()
                ? Component.translatable("gui.ae2enhanced.status.powered")
                : Component.translatable("gui.ae2enhanced.status.unpowered");

        int line = this.titleLabelY + 20;
        graphics.drawString(this.font, online, 10, line, 0x404040, false);
        line += 12;
        graphics.drawString(this.font, powered, 10, line, 0x404040, false);
        line += 16;

        Component types = Component.translatable("gui.ae2enhanced.hyperdimensional.types", controller.getStorageTypes());
        graphics.drawString(this.font, types, 10, line, 0x404040, false);
        line += 12;

        long total = controller.getStorageTotal();
        Component totalText;
        if (hasShiftDown() && total > 0) {
            totalText = Component.translatable("gui.ae2enhanced.hyperdimensional.total_scientific", formatScientific(total));
        } else {
            totalText = Component.translatable("gui.ae2enhanced.hyperdimensional.total", total);
        }
        graphics.drawString(this.font, totalText, 10, line, 0x404040, false);
    }

    private static String formatScientific(long value) {
        if (value == 0) {
            return "0";
        }
        int exp = (int) Math.log10(value);
        double mantissa = value / Math.pow(10, exp);
        return String.format("%.2fE%d", mantissa, exp);
    }
}
