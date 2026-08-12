package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketCollectorConfig;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * 先进 ME 收集器的收集区域设置子界面.
 *
 * <p>设置中心点偏移(相对方块坐标)、每轴 min/max 延伸以及区域可视化开关.
 * 确认/取消均通过 {@link PacketCollectorConfig} 通知服务端并重新打开主 GUI.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiCollectorRegion extends GuiScreen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 172;

    private final TileAdvancedMECollector tile;

    private GuiTextField centerX, centerY, centerZ;
    private GuiTextField minX, minY, minZ;
    private GuiTextField maxX, maxY, maxZ;
    private GuiButton visualizeButton;
    private boolean showBounds;

    private int panelX, panelY;

    public GuiCollectorRegion(TileAdvancedMECollector tile) {
        this.tile = tile;
        this.showBounds = tile.isShowBounds();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = (this.height - PANEL_H) / 2;

        this.centerX = createField(0, panelX + 90, panelY + 24, tile.getCenterOffsetX());
        this.centerY = createField(1, panelX + 135, panelY + 24, tile.getCenterOffsetY());
        this.centerZ = createField(2, panelX + 180, panelY + 24, tile.getCenterOffsetZ());

        this.minX = createField(3, panelX + 40, panelY + 66, tile.getRangeMinX());
        this.maxX = createField(4, panelX + 110, panelY + 66, tile.getRangeMaxX());
        this.minY = createField(5, panelX + 40, panelY + 88, tile.getRangeMinY());
        this.maxY = createField(6, panelX + 110, panelY + 88, tile.getRangeMaxY());
        this.minZ = createField(7, panelX + 40, panelY + 110, tile.getRangeMinZ());
        this.maxZ = createField(8, panelX + 110, panelY + 110, tile.getRangeMaxZ());

        this.visualizeButton = new GuiButton(20, panelX + 12, panelY + 130, 216, 20, "");
        this.buttonList.add(this.visualizeButton);
        this.buttonList.add(new GuiButton(21, panelX + 40, panelY + 152, 70, 16, I18n.format("gui.done")));
        this.buttonList.add(new GuiButton(22, panelX + 130, panelY + 152, 70, 16, I18n.format("gui.cancel")));
    }

    private GuiTextField createField(int id, int x, int y, int value) {
        GuiTextField field = new GuiTextField(id, this.fontRenderer, x, y, 40, 14);
        field.setMaxStringLength(4);
        field.setValidator(s -> s == null || s.matches("-?\\d*"));
        field.setText(Integer.toString(value));
        return field;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 20) {
            this.showBounds = !this.showBounds;
        } else if (button.id == 21) {
            sendConfig(true);
        } else if (button.id == 22) {
            sendConfig(false);
        }
    }

    /**
     * 发送配置到服务端.apply=false 时仅请求重开主 GUI(取消).
     */
    private void sendConfig(boolean apply) {
        int maxOffset = AE2EnhancedConfig.collector.maxCenterOffset;
        int maxRange = AE2EnhancedConfig.collector.maxRange;
        int cx = clamp(parse(centerX, tile.getCenterOffsetX()), -maxOffset, maxOffset);
        int cy = clamp(parse(centerY, tile.getCenterOffsetY()), -maxOffset, maxOffset);
        int cz = clamp(parse(centerZ, tile.getCenterOffsetZ()), -maxOffset, maxOffset);
        int lo = clamp(parse(minX, tile.getRangeMinX()), -maxRange, 0);
        int hi = clamp(parse(maxX, tile.getRangeMaxX()), 0, maxRange);
        int loY = clamp(parse(minY, tile.getRangeMinY()), -maxRange, 0);
        int hiY = clamp(parse(maxY, tile.getRangeMaxY()), 0, maxRange);
        int loZ = clamp(parse(minZ, tile.getRangeMinZ()), -maxRange, 0);
        int hiZ = clamp(parse(maxZ, tile.getRangeMaxZ()), 0, maxRange);
        AE2Enhanced.network.sendToServer(new PacketCollectorConfig(
                tile.getPos(), tile.getWorld().provider.getDimension(), apply,
                cx, cy, cz, lo, loY, loZ, hi, hiY, hiZ, this.showBounds));
        this.mc.displayGuiScreen(null);
    }

    private static int parse(GuiTextField field, int fallback) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            // ESC 视为取消:通知服务端重开主 GUI
            sendConfig(false);
            return;
        }
        for (GuiTextField field : allFields()) {
            if (field.isFocused()) {
                field.textboxKeyTyped(typedChar, keyCode);
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField field : allFields()) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : allFields()) {
            field.updateCursorCounter();
        }
    }

    private GuiTextField[] allFields() {
        return new GuiTextField[]{centerX, centerY, centerZ, minX, minY, minZ, maxX, maxY, maxZ};
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        // 面板
        drawRect(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xC0101015);
        drawRect(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFF555555);
        drawRect(panelX, panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H, 0xFF555555);
        drawRect(panelX, panelY, panelX + 1, panelY + PANEL_H, 0xFF555555);
        drawRect(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF555555);

        String title = I18n.format("gui.ae2enhanced.advanced_me_collector.region.title");
        this.fontRenderer.drawString(title, panelX + (PANEL_W - this.fontRenderer.getStringWidth(title)) / 2, panelY + 7, 0xFFFFFF);

        // 中心偏移行
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.advanced_me_collector.center"), panelX + 12, panelY + 28, 0xA0A0A0);
        this.fontRenderer.drawString("X", panelX + 82, panelY + 28, 0xFF5555);
        this.fontRenderer.drawString("Y", panelX + 127, panelY + 28, 0x55FF55);
        this.fontRenderer.drawString("Z", panelX + 172, panelY + 28, 0x5555FF);

        // 上下限提示
        int maxOffset = AE2EnhancedConfig.collector.maxCenterOffset;
        int maxRange = AE2EnhancedConfig.collector.maxRange;
        String hint = I18n.format("gui.ae2enhanced.advanced_me_collector.region.hint", maxOffset, maxRange);
        this.fontRenderer.drawString(hint, panelX + 12, panelY + 44, 0x606060);

        // 每轴 min/max
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.advanced_me_collector.range.min"), panelX + 40, panelY + 56, 0xA0A0A0);
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.advanced_me_collector.range.max"), panelX + 110, panelY + 56, 0xA0A0A0);
        this.fontRenderer.drawString("X", panelX + 12, panelY + 70, 0xFF5555);
        this.fontRenderer.drawString("Y", panelX + 12, panelY + 92, 0x55FF55);
        this.fontRenderer.drawString("Z", panelX + 12, panelY + 114, 0x5555FF);

        this.visualizeButton.displayString = I18n.format(this.showBounds
                ? "gui.ae2enhanced.advanced_me_collector.visualize.on"
                : "gui.ae2enhanced.advanced_me_collector.visualize.off");

        for (GuiTextField field : allFields()) {
            field.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
