package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import com.github.aeddddd.ae2enhanced.common.menu.StructureUnformedMenu;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.RequestAssemblyPacket;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多方块结构未成型状态 GUI 抽象基类。
 */
public abstract class StructureUnformedScreen<T extends StructureUnformedMenu> extends TechPanelScreen<T> {

    protected final TechButton assembleButton;
    protected Map<Block, Integer> missingMap = new LinkedHashMap<>();
    protected int refreshTicks = 0;

    private final int buttonYOffset;
    private final int innerPanelBottom;
    private final int statusYOffset;
    private final int inventoryDividerYOffset;
    private final int missingListStartY;
    private final int readyTextY;
    private final int hintTextY;
    private final int missingTitleY;
    private final int headerY;
    private final int headerDividerY;

    public StructureUnformedScreen(T menu, Inventory inv, Component title, int ySize,
                                    int buttonYOffset, int innerPanelBottom, int statusYOffset,
                                    int inventoryDividerYOffset, int missingListStartY,
                                    int readyTextY, int hintTextY, int missingTitleY,
                                    int headerY, int headerDividerY) {
        super(menu, inv, title);
        this.imageWidth = GuiConstants.PANEL_WIDTH;
        this.imageHeight = ySize;
        this.buttonYOffset = buttonYOffset;
        this.innerPanelBottom = innerPanelBottom;
        this.statusYOffset = statusYOffset;
        this.inventoryDividerYOffset = inventoryDividerYOffset;
        this.missingListStartY = missingListStartY;
        this.readyTextY = readyTextY;
        this.hintTextY = hintTextY;
        this.missingTitleY = missingTitleY;
        this.headerY = headerY;
        this.headerDividerY = headerDividerY;
        this.assembleButton = new TechButton(0, 0, GuiConstants.ASSEMBLE_BUTTON_WIDTH, GuiConstants.ASSEMBLE_BUTTON_HEIGHT, getAssembleButtonText(), btn -> requestAssembly());
    }

    protected abstract String getTitleKey();

    protected abstract String getSubtitleKey();

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        int centerX = this.leftPos + this.imageWidth / 2;
        this.assembleButton.setX(centerX - GuiConstants.ASSEMBLE_BUTTON_WIDTH / 2);
        this.assembleButton.setY(this.topPos + buttonYOffset);
        this.addRenderableWidget(this.assembleButton);
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

    private boolean hasEnoughMaterials() {
        if (missingMap.isEmpty()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Map<Block, Integer> needed = new LinkedHashMap<>(missingMap);
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.isEmpty()) continue;
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                Block block = entry.getKey();
                if (stack.getItem() == block.asItem()) {
                    int need = entry.getValue();
                    int have = stack.getCount();
                    if (have >= need) {
                        entry.setValue(0);
                    } else {
                        entry.setValue(need - have);
                    }
                    break;
                }
            }
        }
        for (int count : needed.values()) {
            if (count > 0) return false;
        }
        return true;
    }

    private void updateButtonState() {
        Minecraft mc = Minecraft.getInstance();
        boolean creative = mc.player != null && mc.player.isCreative();
        if (missingMap.isEmpty()) {
            this.assembleButton.active = true;
            this.assembleButton.setMessage(getAssembleButtonText());
        } else {
            this.assembleButton.active = creative || hasEnoughMaterials();
            this.assembleButton.setMessage(this.assembleButton.active ? getAssembleButtonText()
                    : Component.translatable("gui.ae2enhanced.assemble.insufficient"));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        this.drawTechPanelFrame(graphics);
        this.drawInnerPanel(graphics, this.leftPos + GuiConstants.PANEL_CONTENT_LEFT_MARGIN, this.topPos + GuiConstants.PANEL_CONTENT_TOP_MARGIN, this.leftPos + this.imageWidth - GuiConstants.PANEL_CONTENT_LEFT_MARGIN, this.topPos + innerPanelBottom);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component title = Component.translatable(getTitleKey());
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, (this.imageWidth - titleWidth) / 2, GuiConstants.UNFORMED_TITLE_Y, GuiColors.ACCENT, false);

        Component subtitle = Component.translatable(getSubtitleKey());
        int subWidth = this.font.width(subtitle);
        graphics.drawString(this.font, subtitle, (this.imageWidth - subWidth) / 2, GuiConstants.UNFORMED_SUBTITLE_Y, GuiConstants.SUBTITLE_COLOR, false);

        graphics.fill(GuiConstants.UNFORMED_OUTER_DIVIDER_LEFT_MARGIN, GuiConstants.UNFORMED_HEADER_DIVIDER_Y,
                this.imageWidth - GuiConstants.UNFORMED_OUTER_DIVIDER_LEFT_MARGIN, GuiConstants.UNFORMED_HEADER_DIVIDER_Y + 1, GuiColors.ACCENT_SOFT);

        if (missingMap.isEmpty()) {
            Component ready = Component.translatable("gui.ae2enhanced.unformed.ready");
            int rw = this.font.width(ready);
            graphics.drawString(this.font, ready, (this.imageWidth - rw) / 2, readyTextY, GuiColors.TEXT_SUCCESS, false);

            Component hint = Component.translatable("gui.ae2enhanced.unformed.hint");
            int hw = this.font.width(hint);
            graphics.drawString(this.font, hint, (this.imageWidth - hw) / 2, hintTextY, GuiConstants.HINT_COLOR, false);
        } else {
            Component missingTitle = Component.translatable("gui.ae2enhanced.unformed.missing");
            graphics.drawString(this.font, missingTitle, GuiConstants.UNFORMED_MISSING_TITLE_X, missingTitleY, GuiColors.TEXT_WARN, false);

            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.unformed.header.material"), GuiConstants.UNFORMED_MATERIAL_HEADER_X, headerY, GuiConstants.HEADER_TEXT_COLOR, false);
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.unformed.header.quantity"), this.imageWidth - GuiConstants.UNFORMED_QUANTITY_HEADER_RIGHT_OFFSET, headerY, GuiConstants.HEADER_TEXT_COLOR, false);
            graphics.fill(GuiConstants.UNFORMED_HEADER_DIVIDER_LEFT_MARGIN, headerDividerY, this.imageWidth - GuiConstants.UNFORMED_HEADER_DIVIDER_LEFT_MARGIN, headerDividerY + 1, GuiColors.BORDER_DIM);

            int y = missingListStartY;
            for (Map.Entry<Block, Integer> entry : missingMap.entrySet()) {
                Block block = entry.getKey();
                int count = entry.getValue();
                ItemStack stack = new ItemStack(block, 1);
                Component name = stack.getHoverName();

                graphics.drawString(this.font, name, GuiConstants.UNFORMED_MISSING_ITEM_NAME_X, y, GuiColors.TEXT_MAIN, false);
                String countStr = "x" + count;
                graphics.drawString(this.font, countStr, this.imageWidth - GuiConstants.UNFORMED_MISSING_ITEM_COUNT_RIGHT_MARGIN - this.font.width(countStr), y, GuiColors.TEXT_ERROR, false);
                y += GuiConstants.UNFORMED_LIST_ITEM_SPACING;
            }
        }

        if (missingMap.isEmpty()) {
            Component status = Component.translatable("gui.ae2enhanced.unformed.status.ready");
            int sw = this.font.width(status);
            graphics.drawString(this.font, status, (this.imageWidth - sw) / 2, statusYOffset, GuiColors.TEXT_SUCCESS, false);
        } else {
            Component status = Component.translatable("gui.ae2enhanced.unformed.status.missing");
            int sw = this.font.width(status);
            graphics.drawString(this.font, status, (this.imageWidth - sw) / 2, statusYOffset, GuiColors.TEXT_ERROR, false);
        }

        graphics.fill(GuiConstants.UNFORMED_OUTER_DIVIDER_LEFT_MARGIN, inventoryDividerYOffset, this.imageWidth - GuiConstants.UNFORMED_OUTER_DIVIDER_LEFT_MARGIN, inventoryDividerYOffset + 1, GuiColors.ACCENT_SOFT);
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
