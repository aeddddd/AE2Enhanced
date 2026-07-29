package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.menu.ComputationCoreMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.blockentity.ComputationCoreBlockEntity;

/**
 * 超因果计算核心成形状态 GUI.
 * <p>使用 2.png 纹理绘制背景（与 Nexus 面板一致）,包含玩家背包和快捷栏,
 * 显示结构状态、网络状态、子 CPU 池规模、活跃任务与单 CPU 规格.</p>
 */
public class ComputationCoreScreen extends AbstractContainerScreen<ComputationCoreMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/multiblock_status.png");

    public ComputationCoreScreen(ComputationCoreMenu menu, Inventory inv, Component title) {
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

        Component title = Component.translatable("gui.ae2enhanced.computation.formed.title");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, (int) ((this.imageWidth - titleWidth) * invScale / 2), GuiConstants.NEXUS_TITLE_Y, GuiConstants.DARK_TEXT_COLOR, false);

        int sepY = (int) (GuiConstants.NEXUS_SEPARATOR_Y * invScale);
        graphics.fill(GuiConstants.NEXUS_SEPARATOR_LEFT_MARGIN, sepY,
                this.imageWidth - GuiConstants.NEXUS_SEPARATOR_LEFT_MARGIN, sepY + 1, GuiColors.ACCENT_SOFT);

        ComputationCoreBlockEntity controller = this.menu.getController();
        int x = (int) (GuiConstants.NEXUS_CONTENT_START_X * invScale);
        int y = (int) (GuiConstants.NEXUS_CONTENT_START_Y * invScale);
        if (controller == null) {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.tile_unavailable"),
                    x, y, GuiColors.TEXT_ERROR, false);
            graphics.pose().popPose();
            return;
        }

        int lineHeight = (int) (GuiConstants.NEXUS_LINE_HEIGHT * invScale);

        Component formedStr = controller.isFormed()
                ? Component.translatable("gui.ae2enhanced.computation.status.online")
                : Component.translatable("gui.ae2enhanced.computation.status.offline");
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.status", formedStr), x, y, GuiConstants.DARK_TEXT_COLOR, false);
        y += lineHeight;

        Component networkStr = controller.isClientNetworkActive()
                ? Component.translatable("gui.ae2enhanced.computation.status.online")
                : Component.translatable("gui.ae2enhanced.computation.status.offline");
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.network", networkStr), x, y, GuiConstants.DARK_TEXT_COLOR, false);
        y += lineHeight;

        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.pool", controller.getClientPoolSize()), x, y, GuiConstants.DARK_TEXT_COLOR, false);
        y += lineHeight;

        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.active_jobs", controller.getClientActiveJobs()), x, y, GuiConstants.DARK_TEXT_COLOR, false);
        y += lineHeight;

        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.parallel", controller.getParallelLimit()), x, y, GuiConstants.DARK_TEXT_COLOR, false);
        y += lineHeight;

        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.computation.label.storage"), x, y, GuiConstants.DARK_TEXT_COLOR, false);

        graphics.pose().popPose();
    }
}
