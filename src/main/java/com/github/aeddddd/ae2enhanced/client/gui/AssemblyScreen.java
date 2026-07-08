package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.AssemblyPagePacket;

/**
 * 装配枢纽成形状态 GUI。
 */
public class AssemblyScreen extends AbstractContainerScreen<AssemblyMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/1.png");

    private static final int BTN_X = 79;
    private static final int BTN_Y = 23;
    private static final int BTN_W = 91;
    private static final int BTN_H = 20;
    private static final int HIGHLIGHT_U = 0;
    private static final int HIGHLIGHT_V = 186;

    public AssemblyScreen(AssemblyMenu menu, Inventory inv, Component title) {
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
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        if (isMouseOverButton(mouseX, mouseY)) {
            graphics.blit(TEXTURE, this.leftPos + BTN_X, this.topPos + BTN_Y,
                    HIGHLIGHT_U, HIGHLIGHT_V, BTN_W, BTN_H, 256, 256);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component title = Component.translatable("gui.ae2enhanced.formed.title");
        graphics.drawString(this.font, title, BTN_X, 8, 0xFFffaa00, false);

        Component btnText = Component.translatable("gui.ae2enhanced.formed.open_patterns");
        int btnTextWidth = this.font.width(btnText);
        int textX = BTN_X + (BTN_W - btnTextWidth) / 2;
        int textY = BTN_Y + (BTN_H - 8) / 2;
        graphics.drawString(this.font, btnText, textX, textY, 0xFFFFFFFF, false);

        AssemblyControllerBlockEntity controller = this.menu.getController();
        long parallelCap = controller != null ? controller.getParallelCap() : 64;
        Component parallelText;
        if (parallelCap >= Long.MAX_VALUE / 2) {
            parallelText = Component.translatable("gui.ae2enhanced.formed.parallel.infinite");
        } else {
            parallelText = Component.translatable("gui.ae2enhanced.formed.parallel", parallelCap);
        }
        graphics.drawString(this.font, parallelText, 12, 50, GuiColors.TEXT_DIM, false);

        Component netStatus;
        int netColor;
        boolean active = controller != null && controller.isNetworkActive();
        boolean powered = controller != null && controller.isNetworkPowered();
        if (active) {
            netStatus = Component.translatable("gui.ae2enhanced.formed.network.active");
            netColor = GuiColors.TEXT_SUCCESS;
        } else if (powered) {
            netStatus = Component.translatable("gui.ae2enhanced.formed.network.booting");
            netColor = GuiColors.TEXT_WARN;
        } else {
            netStatus = Component.translatable("gui.ae2enhanced.formed.network.offline");
            netColor = GuiColors.TEXT_ERROR;
        }
        int nw = this.font.width(netStatus);
        graphics.drawString(this.font, netStatus, this.imageWidth - 12 - nw, 50, netColor, false);

        int jobs = controller != null ? controller.getJobCount() : 0;
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.formed.jobs", jobs), 12, 62, GuiColors.TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOverButton((int) mouseX, (int) mouseY)) {
            ModNetwork.CHANNEL.sendToServer(new AssemblyPagePacket(this.menu.getControllerPos(), 0));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOverButton(int mouseX, int mouseY) {
        return mouseX >= this.leftPos + BTN_X && mouseX < this.leftPos + BTN_X + BTN_W
                && mouseY >= this.topPos + BTN_Y && mouseY < this.topPos + BTN_Y + BTN_H;
    }
}
