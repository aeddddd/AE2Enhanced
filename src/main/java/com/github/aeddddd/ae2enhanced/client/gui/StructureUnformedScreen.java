package com.github.aeddddd.ae2enhanced.client.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.common.menu.StructureUnformedMenu;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.RequestAssemblyPacket;

/**
 * 多方块结构未成型状态 GUI 抽象基类.
 * <p>与成型 GUI 统一的纹理浅色风:2.png 背景 + 玩家背包槽位(见 StructureUnformedMenu).</p>
 */
public abstract class StructureUnformedScreen<T extends StructureUnformedMenu> extends AbstractContainerScreen<T> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/2.png");

    // 布局(176x190,与 2.png 匹配;背包槽位于 y108/y166,由菜单注册)
    private static final int TITLE_Y = 8;
    private static final int SUBTITLE_Y = 19;
    private static final int MISSING_TITLE_X = 20;
    private static final int MISSING_TITLE_Y = 32;
    private static final int LIST_START_Y = 44;
    private static final int LIST_ITEM_SPACING = 12;
    private static final int ITEM_NAME_X = 20;
    private static final int ITEM_COUNT_RIGHT_X = 156;
    private static final int READY_TEXT_Y = 44;
    private static final int HINT_TEXT_Y = 56;
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_Y = 82;

    // 浅色纹理背景上的文字颜色
    private static final int TEXT_GRAY = 0xFF555555;
    private static final int TEXT_RED = 0xFFAA0000;

    protected Button assembleButton;
    protected Map<Block, Integer> missingMap = new LinkedHashMap<>();
    protected int refreshTicks = 0;

    public StructureUnformedScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GuiConstants.DEFAULT_IMAGE_WIDTH;
        this.imageHeight = GuiConstants.NEXUS_IMAGE_HEIGHT;
    }

    protected abstract String getTitleKey();

    protected abstract String getSubtitleKey();

    @Override
    protected void init() {
        super.init();
        this.assembleButton = addRenderableWidget(Button
                .builder(getAssembleButtonText(), btn -> requestAssembly())
                .bounds(this.leftPos + (this.imageWidth - BUTTON_WIDTH) / 2, this.topPos + BUTTON_Y,
                        BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        refreshMissingMap();
        updateButtonState();
    }

    private void requestAssembly() {
        ModNetwork.CHANNEL.sendToServer(new RequestAssemblyPacket(this.menu.getControllerPos()));
    }

    private void refreshMissingMap() {
        this.missingMap = this.menu.getMissing();
    }

    private Component getAssembleButtonText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) {
            return Component.translatable("gui.ae2enhanced.assemble.creative");
        }
        return Component.translatable("gui.ae2enhanced.assemble.survival");
    }

    /**
     * 部分组装模式：只要背包中有任意一种缺失方块即可点击,
     * 每次点击消耗现有材料放置对应方块,逐步补齐直至成型.
     */
    private boolean hasAnyNeededMaterial() {
        if (missingMap.isEmpty()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.isEmpty()) continue;
            for (Block block : missingMap.keySet()) {
                if (stack.getItem() == block.asItem()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateButtonState() {
        Minecraft mc = Minecraft.getInstance();
        boolean creative = mc.player != null && mc.player.isCreative();
        if (missingMap.isEmpty()) {
            this.assembleButton.active = true;
            this.assembleButton.setMessage(getAssembleButtonText());
        } else {
            this.assembleButton.active = creative || hasAnyNeededMaterial();
            this.assembleButton.setMessage(this.assembleButton.active ? getAssembleButtonText()
                    : Component.translatable("gui.ae2enhanced.assemble.insufficient"));
        }
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
        Component title = Component.translatable(getTitleKey());
        graphics.drawString(this.font, title, (this.imageWidth - this.font.width(title)) / 2, TITLE_Y,
                GuiConstants.DARK_TEXT_COLOR, false);

        Component subtitle = Component.translatable(getSubtitleKey());
        graphics.drawString(this.font, subtitle, (this.imageWidth - this.font.width(subtitle)) / 2, SUBTITLE_Y,
                TEXT_GRAY, false);

        if (missingMap.isEmpty()) {
            Component ready = Component.translatable("gui.ae2enhanced.unformed.ready");
            graphics.drawString(this.font, ready, (this.imageWidth - this.font.width(ready)) / 2, READY_TEXT_Y,
                    GuiConstants.ASSEMBLY_STATUS_ACTIVE_COLOR, false);

            Component hint = Component.translatable("gui.ae2enhanced.unformed.hint");
            graphics.drawString(this.font, hint, (this.imageWidth - this.font.width(hint)) / 2, HINT_TEXT_Y,
                    TEXT_GRAY, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.unformed.missing"),
                    MISSING_TITLE_X, MISSING_TITLE_Y, TEXT_RED, false);

            int y = LIST_START_Y;
            for (Map.Entry<Block, Integer> entry : missingMap.entrySet()) {
                Component name = new ItemStack(entry.getKey(), 1).getHoverName();
                graphics.drawString(this.font, name, ITEM_NAME_X, y, GuiConstants.DARK_TEXT_COLOR, false);

                String countStr = "x" + entry.getValue();
                graphics.drawString(this.font, countStr, ITEM_COUNT_RIGHT_X - this.font.width(countStr), y,
                        TEXT_RED, false);
                y += LIST_ITEM_SPACING;
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.menu.isTileFormed()) {
            Minecraft.getInstance().player.closeContainer();
            return;
        }
        if (++refreshTicks >= GuiConstants.UNFORMED_REFRESH_INTERVAL_TICKS) {
            refreshTicks = 0;
            refreshMissingMap();
            updateButtonState();
        }
    }
}
