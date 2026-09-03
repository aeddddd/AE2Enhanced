package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.container.slot.SlotFake;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerDisplayWall;
import com.github.aeddddd.ae2enhanced.display.ChartType;
import com.github.aeddddd.ae2enhanced.display.DisplayPalette;
import com.github.aeddddd.ae2enhanced.display.TimeRange;
import com.github.aeddddd.ae2enhanced.network.packet.PacketDisplayAction;
import com.github.aeddddd.ae2enhanced.tile.TileDisplayPanel;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.Collections;

/**
 * 趋势显示幕墙配置 GUI.
 *
 * <p>背景使用手绘风格贴图(与 wireless_channel_transmitter 等同一经典容器风格:
 * 本体 #CBCCD4 / 外框 #413F54 / 亮边 #F2F2F2 / 暗边 #878FA5).</p>
 *
 * <p>操作:左键色板顺向换色、右键反向;左键显隐开关切换;
 * 选项条按钮悬停高亮并显示说明;所有点击带音效.</p>
 */
public class GuiDisplayWall extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/display_wall.png");

    // 调色板(与贴图一致)
    private static final int C_FRAME = 0xFF413F54;
    private static final int C_BODY = 0xFFCBCCD4;
    private static final int C_LIGHT = 0xFFF2F2F2;
    private static final int C_DARK = 0xFF878FA5;
    private static final int C_TEXT = 0xFF404040;

    private static final int ROWS = TileDisplayPanel.MAX_TRACKED;
    private static final int ROW_Y = 18;
    private static final int ROW_H = 18;
    private static final int SWATCH_X = 30;
    private static final int VIS_X = 48;
    private static final int NAME_X = 66;
    private static final int NAME_W = 86;

    private static final int STRIP_X = 162;
    private static final int STRIP_W = 64;

    private final TileDisplayPanel tile;

    public GuiDisplayWall(InventoryPlayer ip, TileDisplayPanel tile) {
        super(new ContainerDisplayWall(ip, tile));
        this.tile = tile;
        this.xSize = 232;
        this.ySize = 256;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        // 监控项行:色板 + 显隐开关 + 名称
        for (int i = 0; i < ROWS; i++) {
            int rowY = guiTop + ROW_Y + i * ROW_H;
            ItemStack cfg = tile.getConfigInv().getStackInSlot(i);
            boolean has = !cfg.isEmpty();
            boolean hovSwatch = isPointInRegion(SWATCH_X, ROW_Y + i * ROW_H + 2, 14, 14, mouseX, mouseY);
            boolean hovVis = isPointInRegion(VIS_X, ROW_Y + i * ROW_H + 2, 14, 14, mouseX, mouseY);

            // 颜色色板(凸起小格)
            drawRaisedCell(guiLeft + SWATCH_X, rowY + 2, 14, 14,
                    has ? DisplayPalette.get(tile.getSlotColor(i)) : C_BODY,
                    has && hovSwatch, false);

            // 显隐开关
            boolean vis = tile.isSlotVisible(i);
            int visFill = !has ? C_BODY : (vis ? 0xFF7BC47F : 0xFFD47F7F);
            drawRaisedCell(guiLeft + VIS_X, rowY + 2, 14, 14, visFill, has && hovVis, has && !vis);

            // 名称(截断)
            if (has) {
                String name = fontRenderer.trimStringToWidth(cfg.getDisplayName(), NAME_W);
                fontRenderer.drawString(name, guiLeft + NAME_X, rowY + 5,
                        vis ? C_TEXT : 0xFF7A7A8A);
            }
        }

        // 右侧:图表类型
        int btnY = drawStripLabel("gui.ae2enhanced.display_wall.chart", guiTop + 17);
        for (ChartType type : ChartType.values()) {
            drawStripButton(guiLeft + STRIP_X, btnY,
                    I18n.format("gui.ae2enhanced.display_wall.chart." + type.name().toLowerCase()),
                    tile.getChartType() == type, hitStrip(mouseX - guiLeft, mouseY - guiTop, btnY));
            btnY += 13;
        }
        btnY += 2;
        btnY = drawStripLabel("gui.ae2enhanced.display_wall.range", btnY);
        for (TimeRange range : TimeRange.values()) {
            drawStripButton(guiLeft + STRIP_X, btnY, range.getLabel(),
                    tile.getTimeRange() == range, hitStrip(mouseX - guiLeft, mouseY - guiTop, btnY));
            btnY += 13;
        }
        btnY += 2;
        drawStripButton(guiLeft + STRIP_X, btnY,
                "Y: " + I18n.format("gui.ae2enhanced.display_wall.ymode."
                        + tile.getYMode().name().toLowerCase()),
                false, hitStrip(mouseX - guiLeft, mouseY - guiTop, btnY));
    }

    private int drawStripLabel(String key, int y) {
        fontRenderer.drawString(I18n.format(key), guiLeft + STRIP_X, y, C_TEXT);
        return y + 8;
    }

    /** 凸起小格(色板/开关):1px 外框 + TL 亮 BR 暗斜面. */
    private void drawRaisedCell(int x, int y, int w, int h, int fill, boolean hovered, boolean pressed) {
        drawRect(x, y, x + w, y + h, hovered ? C_LIGHT : C_FRAME);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        int tl = pressed ? C_DARK : C_LIGHT;
        int br = pressed ? C_LIGHT : C_DARK;
        drawRect(x + 1, y + 1, x + w - 1, y + 2, tl);
        drawRect(x + 1, y + 1, x + 2, y + h - 1, tl);
        drawRect(x + 1, y + h - 2, x + w - 1, y + h - 1, br);
        drawRect(x + w - 2, y + 1, x + w - 1, y + h - 1, br);
    }

    /** 选项条按钮:经典凸起风格;激活呈按下态,悬停提亮. */
    private void drawStripButton(int x, int y, String text, boolean active, boolean hovered) {
        int fill = active ? 0xFF9A9FB4 : hovered ? 0xFFDCDDE4 : C_BODY;
        drawRaisedCell(x, y, STRIP_W, 12, fill, false, active);
        if (hovered && !active) {
            drawRect(x, y, x + STRIP_W, y + 1, C_LIGHT);
            drawRect(x, y, x + 1, y + 12, C_LIGHT);
        }
        String trimmed = fontRenderer.trimStringToWidth(text, STRIP_W - 6);
        int tx = x + (STRIP_W - fontRenderer.getStringWidth(trimmed)) / 2;
        fontRenderer.drawString(trimmed, tx, y + 2, active ? C_LIGHT : C_TEXT);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.display_wall.title"), 8, 5, C_TEXT);
        fontRenderer.drawString(I18n.format("container.inventory"), 8, 163, C_TEXT);

        // 悬停提示
        for (int i = 0; i < ROWS; i++) {
            int rowY = ROW_Y + i * ROW_H;
            if (isPointInRegion(SWATCH_X, rowY + 2, 14, 14, mouseX, mouseY)) {
                drawHoveringText(Collections.singletonList(
                        I18n.format("gui.ae2enhanced.display_wall.tip.color")),
                        mouseX - guiLeft, mouseY - guiTop);
                return;
            }
            if (isPointInRegion(VIS_X, rowY + 2, 14, 14, mouseX, mouseY)) {
                drawHoveringText(Collections.singletonList(
                        I18n.format("gui.ae2enhanced.display_wall.tip.visible")),
                        mouseX - guiLeft, mouseY - guiTop);
                return;
            }
        }
        int btnY = 25;
        for (ChartType type : ChartType.values()) {
            if (hitStrip(mouseX - guiLeft, mouseY - guiTop, btnY)) {
                drawHoveringText(Collections.singletonList(I18n.format(
                        "gui.ae2enhanced.display_wall.chart." + type.name().toLowerCase() + ".tip")),
                        mouseX - guiLeft, mouseY - guiTop);
                return;
            }
            btnY += 13;
        }
        btnY += 10; // 范围 label
        for (int i = 0; i < TimeRange.values().length; i++) {
            if (hitStrip(mouseX - guiLeft, mouseY - guiTop, btnY)) {
                drawHoveringText(Collections.singletonList(
                        I18n.format("gui.ae2enhanced.display_wall.tip.range")),
                        mouseX - guiLeft, mouseY - guiTop);
                return;
            }
            btnY += 13;
        }
        btnY += 2;
        if (hitStrip(mouseX - guiLeft, mouseY - guiTop, btnY)) {
            drawHoveringText(Collections.singletonList(
                    I18n.format("gui.ae2enhanced.display_wall.tip.ymode")),
                    mouseX - guiLeft, mouseY - guiTop);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int relX = mouseX - guiLeft;
        int relY = mouseY - guiTop;
        for (int i = 0; i < ROWS; i++) {
            int rowY = ROW_Y + i * ROW_H;
            boolean empty = tile.getConfigInv().getStackInSlot(i).isEmpty();
            if (relX >= SWATCH_X && relX < SWATCH_X + 14 && relY >= rowY + 2 && relY < rowY + 16) {
                if (!empty) {
                    // 左键顺向换色,右键反向
                    AE2Enhanced.network.sendToServer(new PacketDisplayAction(tile.getPos(),
                            mouseButton == 1 ? PacketDisplayAction.ACTION_CYCLE_COLOR_PREV
                                    : PacketDisplayAction.ACTION_CYCLE_COLOR, i));
                    playClick();
                }
                return;
            }
            if (relX >= VIS_X && relX < VIS_X + 14 && relY >= rowY + 2 && relY < rowY + 16) {
                if (!empty) {
                    AE2Enhanced.network.sendToServer(new PacketDisplayAction(
                            tile.getPos(), PacketDisplayAction.ACTION_TOGGLE_VISIBLE, i));
                    playClick();
                }
                return;
            }
        }
        // 右侧选项条
        int btnY = 25;
        for (ChartType type : ChartType.values()) {
            if (hitStrip(relX, relY, btnY)) {
                AE2Enhanced.network.sendToServer(new PacketDisplayAction(
                        tile.getPos(), PacketDisplayAction.ACTION_SET_CHART, type.ordinal()));
                playClick();
                return;
            }
            btnY += 13;
        }
        btnY += 10;
        for (TimeRange range : TimeRange.values()) {
            if (hitStrip(relX, relY, btnY)) {
                AE2Enhanced.network.sendToServer(new PacketDisplayAction(
                        tile.getPos(), PacketDisplayAction.ACTION_SET_RANGE, range.ordinal()));
                playClick();
                return;
            }
            btnY += 13;
        }
        btnY += 2;
        if (hitStrip(relX, relY, btnY)) {
            AE2Enhanced.network.sendToServer(new PacketDisplayAction(
                    tile.getPos(), PacketDisplayAction.ACTION_CYCLE_YMODE, 0));
            playClick();
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * 假槽位点击走 AE2 的 PacketInventoryAction 通道(参照 AEBaseGui),
     * 否则原版 windowClick 会被 SlotFake 的 isItemValid/canTakeStack 拒绝,
     * 导致手持物品(含流体容器)无法标记、已标记目标无法删除.
     */
    @Override
    protected void handleMouseClick(Slot slotIn, int slotId, int mouseButton, ClickType type) {
        if (slotIn instanceof SlotFake) {
            InventoryAction action = mouseButton == 1
                    ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
            NetworkHandler.instance().sendToServer(new PacketInventoryAction(action, slotId, 0L));
            playClick();
            return;
        }
        super.handleMouseClick(slotIn, slotId, mouseButton, type);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    private void playClick() {
        mc.getSoundHandler().playSound(
                PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private boolean hitStrip(int relX, int relY, int btnY) {
        return relX >= STRIP_X && relX < STRIP_X + STRIP_W && relY >= btnY && relY < btnY + 12;
    }
}
