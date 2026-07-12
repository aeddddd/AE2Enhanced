package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.common.menu.HyperdimensionalNexusMenu;

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

    public HyperdimensionalNexusScreen(HyperdimensionalNexusMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GuiConstants.DEFAULT_IMAGE_WIDTH;
        this.imageHeight = GuiConstants.NEXUS_IMAGE_HEIGHT;
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
        graphics.pose().scale(GuiConstants.DEFAULT_INV_SCALE, GuiConstants.DEFAULT_INV_SCALE, 1.0F);
        float invScale = GuiConstants.DEFAULT_INV_SCALE_INVERSE;

        Component title = Component.translatable("gui.ae2enhanced.nexus.title");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, (int) ((this.imageWidth - titleWidth) * invScale / 2), GuiConstants.NEXUS_TITLE_Y, GuiConstants.DARK_TEXT_COLOR, false);

        int sepY = (int) (GuiConstants.NEXUS_SEPARATOR_Y * invScale);
        graphics.fill(GuiConstants.NEXUS_SEPARATOR_LEFT_MARGIN, sepY,
                this.imageWidth - GuiConstants.NEXUS_SEPARATOR_LEFT_MARGIN, sepY + 1, GuiColors.ACCENT_SOFT);

        HyperdimensionalControllerBlockEntity controller = this.menu.getController();
        if (controller != null && controller.isSafeMode()) {
            int bannerY = (int) (GuiConstants.NEXUS_SAFE_MODE_BANNER_Y * invScale);
            graphics.fill(GuiConstants.NEXUS_SAFE_MODE_BANNER_LEFT_MARGIN, bannerY,
                    this.imageWidth - GuiConstants.NEXUS_SAFE_MODE_BANNER_LEFT_MARGIN,
                    bannerY + GuiConstants.NEXUS_SAFE_MODE_BANNER_HEIGHT, GuiConstants.SAFE_MODE_BANNER_COLOR);
            Component warn = Component.translatable("gui.ae2enhanced.nexus.safe_mode");
            int warnW = this.font.width(warn);
            graphics.drawString(this.font, warn, (int) ((this.imageWidth - warnW) * invScale / 2), bannerY + 1, GuiConstants.SAFE_MODE_TEXT_COLOR, false);
        }

        if (controller == null) {
            Component unavailable = Component.translatable("gui.ae2enhanced.nexus.tile_unavailable");
            graphics.drawString(this.font, unavailable, (int) (GuiConstants.NEXUS_CONTENT_START_X * invScale), (int) (GuiConstants.NEXUS_CONTENT_START_Y * invScale), GuiColors.TEXT_ERROR, false);
            graphics.pose().popPose();
            return;
        }

        int x = (int) (GuiConstants.NEXUS_CONTENT_START_X * invScale);
        int y = (int) (GuiConstants.NEXUS_CONTENT_START_Y * invScale);
        if (controller.isSafeMode()) {
            y += (int) (GuiConstants.NEXUS_SAFE_MODE_BANNER_OFFSET * invScale);
        }
        int lineHeight = (int) (GuiConstants.NEXUS_LINE_HEIGHT * invScale);

        Component formedStr = controller.isFormed()
                ? Component.translatable("gui.ae2enhanced.nexus.structure.formed")
                : Component.translatable("gui.ae2enhanced.nexus.structure.unformed");
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.label.structure", formedStr), x, y, GuiConstants.DARK_TEXT_COLOR, false);
        y += lineHeight;

        Component networkStr = controller.isNetworkActive()
                ? Component.translatable("gui.ae2enhanced.nexus.network.online")
                : Component.translatable("gui.ae2enhanced.nexus.network.offline");
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.label.network", networkStr), x, y, GuiConstants.DARK_TEXT_COLOR, false);
        y += lineHeight;

        Component powerStr = controller.isNetworkPowered()
                ? Component.translatable("gui.ae2enhanced.nexus.power.ok")
                : Component.translatable("gui.ae2enhanced.nexus.power.none");
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.label.power", powerStr), x, y, GuiConstants.DARK_TEXT_COLOR, false);
        y += lineHeight;

        Component nexusLabel;
        if (controller.getNexusId() != null) {
            String id = controller.getNexusId().toString().substring(0, 8);
            nexusLabel = Component.translatable("gui.ae2enhanced.nexus.label.nexus_id", id + "...");
        } else {
            nexusLabel = Component.translatable("gui.ae2enhanced.nexus.label.nexus_id",
                    Component.translatable("gui.ae2enhanced.nexus.nexus_id.none"));
        }
        graphics.drawString(this.font, nexusLabel, x, y, GuiConstants.DARK_TEXT_COLOR, false);
        y += lineHeight;

        boolean shift = hasShiftDown();
        int types = controller.getStorageTypes();
        String total = String.valueOf(controller.getStorageTotal());
        if (shift) {
            total = toScientificNotation(controller.getStorageTotal());
        }
        if (types > 0) {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.label.storage_types", types), x, y, GuiConstants.DARK_TEXT_COLOR, false);
            y += lineHeight;
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.label.storage_total", total), x, y, GuiConstants.DARK_TEXT_COLOR, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.nexus.storage.empty"), x, y, GuiConstants.DARK_TEXT_COLOR, false);
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

        int x = GuiConstants.NEXUS_TOOLTIP_START_X;
        int y = GuiConstants.NEXUS_TOOLTIP_START_Y;
        if (controller.isSafeMode()) {
            y += GuiConstants.NEXUS_SAFE_MODE_BANNER_OFFSET;
        }
        int lineHeight = GuiConstants.NEXUS_LINE_HEIGHT;
        int storageYStart = y + lineHeight * 4;
        int storageYEnd;
        int types = controller.getStorageTypes();
        if (types > 0) {
            storageYEnd = storageYStart + lineHeight * 2;
        } else {
            storageYEnd = storageYStart + lineHeight;
        }

        if (mouseX >= this.leftPos + x && mouseX <= this.leftPos + this.imageWidth - GuiConstants.NEXUS_CONTENT_START_X
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
