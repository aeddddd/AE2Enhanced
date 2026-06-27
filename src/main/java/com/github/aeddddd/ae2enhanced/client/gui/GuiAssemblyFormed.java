package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyFormed;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPatternPage;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiAssemblyFormed extends GuiContainer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/1.png");

    private final TileAssemblyController tile;

    // 按钮区域（纹理坐标 79,23 -> 170,43，尺寸 91×20）
    private static final int BTN_X = 79;
    private static final int BTN_Y = 23;
    private static final int BTN_W = 91;
    private static final int BTN_H = 20;

    // 高亮条纹理坐标 0,186 -> 91,206，尺寸 91×20
    private static final int HIGHLIGHT_U = 0;
    private static final int HIGHLIGHT_V = 186;

    public GuiAssemblyFormed(InventoryPlayer playerInv, TileAssemblyController tile) {
        super(new ContainerAssemblyFormed(playerInv, tile));
        this.tile = tile;
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);

        // 鼠标悬停时复制高亮条覆盖按钮
        if (isMouseOverButton(mouseX, mouseY)) {
            this.drawTexturedModalRect(
                this.guiLeft + BTN_X, this.guiTop + BTN_Y,
                HIGHLIGHT_U, HIGHLIGHT_V, BTN_W, BTN_H);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题画在按钮上方（格子区右侧）
        String title = I18n.format("gui.ae2enhanced.formed.title");
        fontRenderer.drawString(title, BTN_X, 8, 0xFFffaa00);

        // 按钮文字画在按钮中央
        String btnText = I18n.format("gui.ae2enhanced.formed.open_patterns");
        int btnTextWidth = fontRenderer.getStringWidth(btnText);
        int textX = BTN_X + (BTN_W - btnTextWidth) / 2;
        int textY = BTN_Y + (BTN_H - 8) / 2; // 字体高度约8px，垂直居中
        fontRenderer.drawString(btnText, textX, textY, 0xFFFFFFFF);

        // 信息展示区：名称栏内 (7,47) -> (169,70)
        long parallelCap = tile.getParallelCap();
        String parallelText;
        if (parallelCap >= Long.MAX_VALUE / 2) {
            parallelText = I18n.format("gui.ae2enhanced.formed.parallel.infinite");
        } else {
            parallelText = I18n.format("gui.ae2enhanced.formed.parallel", parallelCap);
        }
        fontRenderer.drawString(parallelText, 12, 50, GuiColors.TEXT_DIM);

        String netStatus;
        int netColor;
        if (tile.isNetworkActive()) {
            netStatus = I18n.format("gui.ae2enhanced.formed.network.active");
            netColor = GuiColors.TEXT_SUCCESS;
        } else if (tile.isNetworkPowered()) {
            netStatus = I18n.format("gui.ae2enhanced.formed.network.booting");
            netColor = GuiColors.TEXT_WARN;
        } else {
            netStatus = I18n.format("gui.ae2enhanced.formed.network.offline");
            netColor = GuiColors.TEXT_ERROR;
        }
        int nw = fontRenderer.getStringWidth(netStatus);
        fontRenderer.drawString(netStatus, xSize - 12 - nw, 50, netColor);

        // 任务数画在信息区第二行
        String jobs = I18n.format("gui.ae2enhanced.formed.jobs", tile.getJobCount());
        fontRenderer.drawString(jobs, 12, 62, GuiColors.TEXT_DIM);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && isMouseOverButton(mouseX, mouseY)) {
            AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), 0));
            this.mc.getSoundHandler().playSound(
                net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                    SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean isMouseOverButton(int mouseX, int mouseY) {
        return mouseX >= guiLeft + BTN_X && mouseX < guiLeft + BTN_X + BTN_W
            && mouseY >= guiTop + BTN_Y && mouseY < guiTop + BTN_Y + BTN_H;
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        if (drawCustomTooltips(mouseX, mouseY)) {
            return;
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private boolean drawCustomTooltips(int mouseX, int mouseY) {
        int[][] upgradeSlots = {
            {8, 8}, {26, 8}, {44, 8},
            {8, 26}, {26, 26}, {44, 26}
        };
        String[] upgradeKeys = {
            "item.ae2enhanced.upgrade_card.parallel.name",
            "item.ae2enhanced.upgrade_card.speed.name",
            "item.ae2enhanced.upgrade_card.efficiency.name",
            "item.ae2enhanced.upgrade_card.capacity.name",
            "item.ae2enhanced.upgrade_card.reserved1.name",
            "item.ae2enhanced.upgrade_card.reserved2.name"
        };
        for (int i = 0; i < upgradeSlots.length; i++) {
            int sx = upgradeSlots[i][0];
            int sy = upgradeSlots[i][1];
            if (isPointInRegion(sx, sy, 16, 16, mouseX, mouseY)) {
                List<String> lines = new ArrayList<>();
                net.minecraft.inventory.Slot slot = this.inventorySlots.inventorySlots.get(i);
                boolean installed = slot != null && slot.getHasStack();
                int count = installed ? slot.getStack().getCount() : 0;
                String name = I18n.format(upgradeKeys[i]);
                if (installed) {
                    lines.add("§a● §r" + name);
                    switch (i) {
                        case 0:
                            long parallel = tile.getParallelCap();
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.parallel.status",
                                parallel >= Long.MAX_VALUE / 2 ? "∞" : String.valueOf(parallel)) + "§r");
                            break;
                        case 1:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.speed.status", tile.getCraftingTicks()) + "§r");
                            break;
                        case 2:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.efficiency.status", count) + "§r");
                            break;
                        case 3:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.capacity.status",
                                tile.getPatternPages(), tile.getPatternSlotCount()) + "§r");
                            break;
                        case 4:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.upload.status") + "§r");
                            break;
                        case 5:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.reserved2.status") + "§r");
                            break;
                    }
                } else {
                    lines.add("§7○ §r" + name);
                    switch (i) {
                        case 0:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.parallel.empty") + "§r");
                            break;
                        case 1:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.speed.empty") + "§r");
                            break;
                        case 2:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.efficiency.empty") + "§r");
                            break;
                        case 3:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.capacity.empty") + "§r");
                            break;
                        case 4:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.upload.empty") + "§r");
                            break;
                        case 5:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.reserved2.empty") + "§r");
                            break;
                    }
                }
                this.drawHoveringText(lines, mouseX, mouseY);
                return true;
            }
        }

        // 按钮 tooltip
        if (isMouseOverButton(mouseX, mouseY)) {
            List<String> lines = new ArrayList<>();
            lines.add(I18n.format("gui.ae2enhanced.tooltip.patterns"));
            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.patterns.desc") + "§r");
            this.drawHoveringText(lines, mouseX, mouseY);
            return true;
        }

        // 网络状态 tooltip（名称栏区域）
        if (isPointInRegion(7, 47, 162, 23, mouseX, mouseY)) {
            List<String> lines = new ArrayList<>();
            if (tile.isNetworkActive()) {
                lines.add(I18n.format("gui.ae2enhanced.formed.network.active"));
            } else if (tile.isNetworkPowered()) {
                lines.add(I18n.format("gui.ae2enhanced.formed.network.booting"));
                lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.network.booting") + "§r");
            } else {
                lines.add(I18n.format("gui.ae2enhanced.formed.network.offline"));
                lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.network.offline") + "§r");
            }
            this.drawHoveringText(lines, mouseX, mouseY);
            return true;
        }
        return false;
    }
}
