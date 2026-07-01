package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.RequestAssemblyPacket;

/**
 * 超维度仓储未成形状态的 GUI。
 */
public class HyperdimensionalUnformedScreen extends AbstractContainerScreen<HyperdimensionalUnformedMenu> {

    public HyperdimensionalUnformedScreen(HyperdimensionalUnformedMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.ae2enhanced.button.assemble"), btn -> requestAssembly())
                .pos(x + 50, y + 120)
                .size(76, 20)
                .build());
    }

    private void requestAssembly() {
        ModNetwork.CHANNEL.sendToServer(new RequestAssemblyPacket(this.menu.getControllerPos()));
        this.onClose();
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
        Component hint = Component.translatable("gui.ae2enhanced.hyperdimensional.unformed_hint");
        graphics.drawString(this.font, hint, 10, line, 0xAA0000, false);
        line += 16;

        int index = 0;
        for (java.util.Map.Entry<Block, Integer> entry : this.menu.getMissing().entrySet()) {
            if (index >= 4) {
                break;
            }
            ItemStack stack = new ItemStack(entry.getKey());
            Component text = Component.translatable("gui.ae2enhanced.hyperdimensional.missing_entry",
                    stack.getHoverName(), entry.getValue());
            graphics.drawString(this.font, text, 10, line, 0x404040, false);
            line += 12;
            index++;
        }
    }
}
