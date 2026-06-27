package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementSelectPreset;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ME 放置工具径向菜单 —— 重做版。
 *
 * 特性：
 * - 合并同种选取：相同的预设物品在轮盘中只显示一个。
 * - 始终提供空选项：用于清除当前选择，最多 9 个扇区（8 个唯一物品 + 1 空）。
 * - 不需要把鼠标移到图标上；根据鼠标相对于屏幕中心的角度选中对应扇区。
 * - 松开按键即确认选择。
 */
public class GuiPlacementRadialMenu extends GuiScreen {

    private static final int RADIUS = 70;
    private static final int ITEM_SIZE = 18;
    private static final int DEADZONE = 20; // 中心死区，防止误触

    /** 空选项在逻辑槽位中的标记值 */
    public static final int SLOT_EMPTY = -2;

    private final EntityPlayer player;
    private final PlacementConfig config;
    private final int keyCode;

    private List<SlotEntry> visibleEntries = new ArrayList<>();
    private int hoveredSector = -1;

    public GuiPlacementRadialMenu(EntityPlayer player, int keyCode) {
        this.player = player;
        this.config = new PlacementConfig(player.getHeldItemMainhand());
        this.keyCode = keyCode;
    }

    private static class SlotEntry {
        final ItemStack stack;
        final int actualSlot; // 对应 PlacementConfig 槽位；SLOT_EMPTY 表示空选项

        SlotEntry(ItemStack stack, int actualSlot) {
            this.stack = stack;
            this.actualSlot = actualSlot;
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        refreshVisibleEntries();
    }

    private void refreshVisibleEntries() {
        visibleEntries.clear();

        // 收集唯一物品，保留第一次出现的实际槽位；合并同种物品（忽略数量）
        boolean[] used = new boolean[PlacementConfig.MAX_PRESETS];
        for (int i = 0; i < PlacementConfig.MAX_PRESETS; i++) {
            if (used[i]) continue;
            ItemStack s = config.getStackInSlot(i);
            if (s.isEmpty()) continue;

            ItemStack display = s.copy();
            display.setCount(1);
            visibleEntries.add(new SlotEntry(display, i));
            used[i] = true;

            for (int j = i + 1; j < PlacementConfig.MAX_PRESETS; j++) {
                ItemStack other = config.getStackInSlot(j);
                if (!other.isEmpty() && isSameItemType(s, other)) {
                    used[j] = true;
                }
            }

            if (visibleEntries.size() >= PlacementConfig.MAX_PRESETS - 1) break; // 留一个给空选项
        }

        // 始终提供一个空选项
        visibleEntries.add(new SlotEntry(ItemStack.EMPTY, SLOT_EMPTY));
    }

    private static boolean isSameItemType(ItemStack a, ItemStack b) {
        // 线缆按类型合并，忽略颜色
        if (com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver.isSameCableType(a, b)) {
            return true;
        }
        return a.getItem() == b.getItem()
                && a.getMetadata() == b.getMetadata()
                && ItemStack.areItemStackTagsEqual(a, b);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int cx = width / 2;
        int cy = height / 2;
        int sectors = visibleEntries.size();

        drawCenteredString(fontRenderer, I18n.format("gui.ae2enhanced.placement_radial.title"), cx, cy - RADIUS - 24, 0xFFFFFF);

        hoveredSector = getHoveredSector(mouseX, mouseY, cx, cy, sectors);

        // 绘制物品
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < sectors; i++) {
            double angle = getSectorAngle(i, sectors);
            int x = cx + (int) (Math.cos(angle) * RADIUS) - ITEM_SIZE / 2;
            int y = cy - (int) (Math.sin(angle) * RADIUS) - ITEM_SIZE / 2;

            if (i == hoveredSector) {
                drawRect(x - 2, y - 2, x + ITEM_SIZE + 2, y + ITEM_SIZE + 2, 0x80FFFFFF);
            }

            SlotEntry entry = visibleEntries.get(i);
            if (!entry.stack.isEmpty()) {
                itemRender.renderItemAndEffectIntoGUI(entry.stack, x + 1, y + 1);
                itemRender.renderItemOverlayIntoGUI(fontRenderer, entry.stack, x + 1, y + 1, null);
            } else {
                // 空选项：绘制一个空心框
                drawRect(x + 1, y + 1, x + ITEM_SIZE - 1, y + ITEM_SIZE - 1, 0x30FFFFFF);
                drawCenteredString(fontRenderer, "∅", x + ITEM_SIZE / 2 + 1, y + ITEM_SIZE / 2 - 4, 0xFFFFFF);
            }
        }
        RenderHelper.disableStandardItemLighting();

        // 底部提示
        String hint;
        if (hoveredSector >= 0 && hoveredSector < visibleEntries.size()) {
            SlotEntry entry = visibleEntries.get(hoveredSector);
            if (entry.actualSlot == SLOT_EMPTY) {
                hint = I18n.format("gui.ae2enhanced.placement_radial.empty");
            } else {
                hint = entry.stack.getDisplayName();
            }
        } else {
            hint = I18n.format("gui.ae2enhanced.placement_radial.aim_to_select");
        }
        drawCenteredString(fontRenderer, hint, cx, cy + RADIUS + 16, 0xCCCCCC);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!Keyboard.isKeyDown(keyCode)) {
            confirmSelection();
            mc.player.closeScreen();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        confirmSelection();
        mc.player.closeScreen();
    }

    private void confirmSelection() {
        if (hoveredSector < 0 || hoveredSector >= visibleEntries.size()) return;
        SlotEntry entry = visibleEntries.get(hoveredSector);
        AE2Enhanced.network.sendToServer(new PacketPlacementSelectPreset(entry.actualSlot));
    }

    private int getHoveredSector(int mouseX, int mouseY, int cx, int cy, int sectors) {
        double dx = mouseX - cx;
        double dy = cy - mouseY; // Y 轴向上
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < DEADZONE) return -1;

        double angle = Math.atan2(dy, dx); // 0 右侧，逆时针增加
        if (angle < 0) angle += 2 * Math.PI;

        double sectorSize = 2 * Math.PI / sectors;
        // 第一个扇区在上方（PI/2）
        double startOffset = Math.PI / 2 - sectorSize / 2;
        double adjusted = (angle - startOffset + 2 * Math.PI) % (2 * Math.PI);
        int index = MathHelper.floor(adjusted / sectorSize);
        return MathHelper.clamp(index, 0, sectors - 1);
    }

    private double getSectorAngle(int index, int sectors) {
        double sectorSize = 2 * Math.PI / sectors;
        double startOffset = Math.PI / 2 - sectorSize / 2;
        return startOffset + index * sectorSize + sectorSize / 2;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
