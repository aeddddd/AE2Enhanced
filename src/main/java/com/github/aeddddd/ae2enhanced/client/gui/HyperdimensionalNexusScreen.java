package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;

/**
 * 超维度仓储 Nexus 信息面板。
 * <p>使用 2.png 纹理绘制背景，包含玩家背包和快捷栏。</p>
 */
public class HyperdimensionalNexusScreen extends AbstractContainerScreen<HyperdimensionalNexusMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/2.png");

    private static final int TEXT_DARK = 0xFF222222;

    public HyperdimensionalNexusScreen(HyperdimensionalNexusMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 190;
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
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().scale(0.85F, 0.85F, 1.0F);
        float invScale = 1.0F / 0.85F;

        Component title = Component.translatable("gui.ae2enhanced.nexus.title");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, (int) ((this.imageWidth - titleWidth) * invScale / 2), 10, TEXT_DARK, false);

        int sepY = (int) (22 * invScale);
        graphics.fill(16, sepY, this.imageWidth - 16, sepY + 1, GuiColors.ACCENT_SOFT);

        HyperdimensionalControllerBlockEntity controller = this.menu.getController();
        if (controller != null && controller.isSafeMode()) {
            int bannerY = (int) (26 * invScale);
            graphics.fill(10, bannerY, this.imageWidth - 10, bannerY + 10, 0x55ff0000);
            Component warn = Component.translatable("gui.ae2enhanced.nexus.safe_mode");
            int warnW = this.font.width(warn);
            graphics.drawString(this.font, warn, (int) ((this.imageWidth - warnW) * invScale / 2), bannerY + 1, 0xFFffaaaa, false);
        }

        if (controller == null) {
            Component unavailable = Component.translatable("gui.ae2enhanced.nexus.tile_unavailable");
            graphics.drawString(this.font, unavailable, (int) (20 * invScale), (int) (34 * invScale), GuiColors.TEXT_ERROR, false);
            graphics.pose().popPose();
            return;
        }

        int x = (int) (20 * invScale);
        int y = (int) (34 * invScale);
        if (controller.isSafeMode()) {
            y += (int) (12 * invScale);
        }
        int lineHeight = (int) (11 * invScale);

        Component formedStr = controller.isFormed()
                ? Component.translatable("gui.ae2enhanced.nexus.structure.formed")
                : Component.translatable("gui.ae2enhanced.nexus.structure.unformed");
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.label.structure", formedStr), x, y, TEXT_DARK, false);
        y += lineHeight;

        Component networkStr = controller.isNetworkActive()
                ? Component.translatable("gui.ae2enhanced.nexus.network.online")
                : Component.translatable("gui.ae2enhanced.nexus.network.offline");
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.label.network", networkStr), x, y, TEXT_DARK, false);
        y += lineHeight;

        Component powerStr = controller.isNetworkPowered()
                ? Component.translatable("gui.ae2enhanced.nexus.power.ok")
                : Component.translatable("gui.ae2enhanced.nexus.power.none");
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.label.power", powerStr), x, y, TEXT_DARK, false);
        y += lineHeight;

        Component nexusLabel;
        if (controller.getNexusId() != null) {
            String id = controller.getNexusId().toString().substring(0, 8);
            nexusLabel = Component.translatable("gui.ae2enhanced.nexus.label.nexus_id", id + "...");
        } else {
            nexusLabel = Component.translatable("gui.ae2enhanced.nexus.label.nexus_id",
                    Component.translatable("gui.ae2enhanced.nexus.nexus_id.none"));
        }
        graphics.drawString(this.font, nexusLabel, x, y, TEXT_DARK, false);
        y += lineHeight;

        boolean shift = hasShiftDown();
        int types = controller.getStorageTypes();
        String total = String.valueOf(controller.getStorageTotal());
        if (shift) {
            total = toScientificNotation(controller.getStorageTotal());
        }
        if (types > 0) {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.label.storage_types", types), x, y, TEXT_DARK, false);
            y += lineHeight;
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.label.storage_total", total), x, y, TEXT_DARK, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.storage.empty"), x, y, TEXT_DARK, false);
        }

        graphics.pose().popPose();
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);

        HyperdimensionalControllerBlockEntity controller = this.menu.getController();
        if (controller == null) {
            return;
        }

        int x = 20;
        int y = 34;
        if (controller.isSafeMode()) {
            y += 12;
        }
        int lineHeight = 11;
        int storageYStart = y + lineHeight * 4;
        int storageYEnd;
        int types = controller.getStorageTypes();
        if (types > 0) {
            storageYEnd = storageYStart + lineHeight * 2;
        } else {
            storageYEnd = storageYStart + lineHeight;
        }

        if (mouseX >= this.leftPos + x && mouseX <= this.leftPos + this.imageWidth - 20
                && mouseY >= this.topPos + storageYStart && mouseY <= this.topPos + storageYEnd) {
            String display = hasShiftDown() ? toScientificNotation(controller.getStorageTotal()) : controller.getStorageTotalRaw();
            graphics.renderTooltip(this.font, Component.literal(display), mouseX, mouseY);
        }
    }

    private static String toScientificNotation(long value) {
        if (value == 0) {
            return "0";
        }
        String s = String.valueOf(value);
        int len = s.length();
        if (len <= 1) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(s.charAt(0));
        sb.append('.');
        int end = Math.min(len, 4);
        sb.append(s.substring(1, end));
        while (sb.charAt(sb.length() - 1) == '0') {
            sb.setLength(sb.length() - 1);
        }
        if (sb.charAt(sb.length() - 1) == '.') {
            sb.setLength(sb.length() - 1);
        }
        sb.append(" x 10^").append(len - 1);
        return sb.toString();
    }
}
