package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerChunkPowerNode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChunkPowerNodeAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChunkPowerNodeSync;
import com.github.aeddddd.ae2enhanced.tile.TileChunkPowerNode;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 区块供电节点的客户端 GUI.
 *
 * <p>实时显示网络状态、供电目标数量与每 tick 实际输出，
 * 并列出供电目标（坐标 + 设备名），允许排除（解除绑定）或恢复单个目标.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiChunkPowerNode extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/chunk_power_node.png");

    private static final int LIST_TOP = 56;
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_HEIGHT = 13;

    /** 排除/恢复按钮区域 */
    private static final int BTN_X = 138;
    private static final int BTN_W = 26;
    private static final int BTN_H = 11;

    /** 滚动条区域 */
    private static final int SCROLL_X = 166;
    private static final int SCROLL_W = 3;

    private final TileChunkPowerNode tile;
    private int scrollOffset = 0;

    public GuiChunkPowerNode(InventoryPlayer playerInventory, TileChunkPowerNode tile) {
        super(new ContainerChunkPowerNode(playerInventory, tile));
        this.tile = tile;
        this.xSize = 176;
        this.ySize = 222;
    }

    private List<PacketChunkPowerNodeSync.TargetInfo> getTargets() {
        return tile.getClientTargetList();
    }

    /**
     * 获取目标的本地化显示名：优先物品形式，回退到方块翻译键.
     */
    private String getTargetName(PacketChunkPowerNodeSync.TargetInfo t) {
        if (!t.getDisplay().isEmpty()) {
            return t.getDisplay().getDisplayName();
        }
        return I18n.format(t.getFallbackKey() + ".name");
    }

    private int getMaxScroll() {
        return Math.max(0, getTargets().size() - VISIBLE_ROWS);
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format(tile.getBlockType().getTranslationKey() + ".name");
        fontRenderer.drawString(title, (xSize - fontRenderer.getStringWidth(title)) / 2, 6, 0x404040);

        // 网络状态
        String statusKey = tile.isActive() ? "gui.ae2enhanced.chunk_power_node.status.active"
                : tile.isPowered() ? "gui.ae2enhanced.chunk_power_node.status.powered"
                : "gui.ae2enhanced.chunk_power_node.status.offline";
        fontRenderer.drawString(I18n.format(statusKey), 8, 18, 0x404040);

        // 目标数量（含排除数）
        List<PacketChunkPowerNodeSync.TargetInfo> targets = getTargets();
        int excludedCount = 0;
        for (PacketChunkPowerNodeSync.TargetInfo t : targets) {
            if (t.isExcluded()) excludedCount++;
        }
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.chunk_power_node.targets",
                targets.size(), excludedCount), 8, 29, 0x404040);

        // 每 tick 实际输出
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.chunk_power_node.output",
                tile.getLastTickOutput()), 8, 40, 0x404040);

        clampScroll();

        if (targets.isEmpty()) {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.chunk_power_node.empty"),
                    8, LIST_TOP + 4, 0x808080);
        }

        // 目标列表
        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scrollOffset + row;
            if (idx >= targets.size()) break;
            PacketChunkPowerNodeSync.TargetInfo t = targets.get(idx);
            int rowY = LIST_TOP + row * ROW_HEIGHT;

            int nameColor = t.isExcluded() ? 0x909090 : 0x404040;
            String name = getTargetName(t);
            if (fontRenderer.getStringWidth(name) > 92) {
                name = fontRenderer.trimStringToWidth(name, 86) + "...";
            }
            fontRenderer.drawString(name, 8, rowY + 2, nameColor);

            String delivered = t.getDelivered() + " FE";
            fontRenderer.drawString(delivered, 134 - fontRenderer.getStringWidth(delivered),
                    rowY + 2, nameColor);

            // 排除/恢复按钮
            boolean hovered = relMouseX >= BTN_X && relMouseX < BTN_X + BTN_W
                    && relMouseY >= rowY + 1 && relMouseY < rowY + 1 + BTN_H;
            drawRect(BTN_X, rowY + 1, BTN_X + BTN_W, rowY + 1 + BTN_H, 0xFF373737);
            drawRect(BTN_X + 1, rowY + 2, BTN_X + BTN_W - 1, rowY + BTN_H,
                    hovered ? 0xFFDEDEDE : 0xFFC6C6C6);
            String btnText = I18n.format(t.isExcluded()
                    ? "gui.ae2enhanced.chunk_power_node.include"
                    : "gui.ae2enhanced.chunk_power_node.exclude");
            fontRenderer.drawString(btnText,
                    BTN_X + (BTN_W - fontRenderer.getStringWidth(btnText)) / 2, rowY + 3, 0x404040);
        }

        // 滚动条
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int trackTop = LIST_TOP;
            int trackBottom = LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT;
            drawRect(SCROLL_X, trackTop, SCROLL_X + SCROLL_W, trackBottom, 0xFF8B8B8B);
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(10, trackH * VISIBLE_ROWS / targets.size());
            int thumbY = trackTop + (trackH - thumbH) * scrollOffset / maxScroll;
            drawRect(SCROLL_X, thumbY, SCROLL_X + SCROLL_W, thumbY + thumbH, 0xFF4A4A4A);
        }

        fontRenderer.drawString(I18n.format("container.inventory"), 8, ySize - 96 + 2, 0x404040);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);

        // 目标行悬停提示：名称、坐标、本 tick 交付量
        List<PacketChunkPowerNodeSync.TargetInfo> targets = getTargets();
        int relX = mouseX - guiLeft;
        int relY = mouseY - guiTop;
        if (relX >= 8 && relX < BTN_X && relY >= LIST_TOP && relY < LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT) {
            int idx = scrollOffset + (relY - LIST_TOP) / ROW_HEIGHT;
            if (idx >= 0 && idx < targets.size()) {
                PacketChunkPowerNodeSync.TargetInfo t = targets.get(idx);
                BlockPos p = t.getPos();
                List<String> tooltip = new ArrayList<>();
                tooltip.add(getTargetName(t));
                tooltip.add(I18n.format("gui.ae2enhanced.chunk_power_node.tooltip.coords",
                        p.getX(), p.getY(), p.getZ()));
                tooltip.add(I18n.format("gui.ae2enhanced.chunk_power_node.tooltip.delivered",
                        t.getDelivered()));
                if (t.isExcluded()) {
                    tooltip.add(I18n.format("gui.ae2enhanced.chunk_power_node.tooltip.excluded"));
                }
                drawHoveringText(tooltip, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            List<PacketChunkPowerNodeSync.TargetInfo> targets = getTargets();
            int relX = mouseX - guiLeft;
            int relY = mouseY - guiTop;
            if (relX >= BTN_X && relX < BTN_X + BTN_W
                    && relY >= LIST_TOP && relY < LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT) {
                int row = (relY - LIST_TOP) / ROW_HEIGHT;
                int idx = scrollOffset + row;
                if (idx >= 0 && idx < targets.size()
                        && relY >= LIST_TOP + row * ROW_HEIGHT + 1
                        && relY < LIST_TOP + row * ROW_HEIGHT + 1 + BTN_H) {
                    PacketChunkPowerNodeSync.TargetInfo t = targets.get(idx);
                    boolean newExcluded = !t.isExcluded();
                    AE2Enhanced.network.sendToServer(new PacketChunkPowerNodeAction(
                            tile.getPos(), t.getPos(), newExcluded));
                    // 乐观更新本地显示，等待服务端同步纠正
                    targets.set(idx, new PacketChunkPowerNodeSync.TargetInfo(
                            t.getPos(), t.getDisplay(), t.getFallbackKey(), newExcluded, t.getDelivered()));
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollOffset -= Integer.signum(wheel);
            clampScroll();
        }
    }
}
