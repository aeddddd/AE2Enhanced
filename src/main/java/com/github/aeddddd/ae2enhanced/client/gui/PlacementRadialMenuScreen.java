package com.github.aeddddd.ae2enhanced.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementSelectPreset;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver;

/**
 * ME 放置工具径向菜单 —— 重做版。
 *
 * 特性：
 * - 合并同种选取：相同的预设物品在轮盘中只显示一个。
 * - 始终提供空选项：用于清除当前选择，最多 9 个扇区（8 个唯一物品 + 1 空）。
 * - 不需要把鼠标移到图标上；根据鼠标相对于屏幕中心的角度选中对应扇区。
 * - 松开按键即确认选择。
 */
public class PlacementRadialMenuScreen extends Screen {

    private static final int RADIUS = 70;
    private static final int ITEM_SIZE = 18;
    private static final int DEADZONE = 20; // 中心死区，防止误触

    /** 空选项在逻辑槽位中的标记值 */
    public static final int SLOT_EMPTY = -2;

    private final Player player;
    private final PlacementConfig config;
    private final int keyCode;

    private List<SlotEntry> visibleEntries = new ArrayList<>();
    private int hoveredSector = -1;

    public PlacementRadialMenuScreen(Player player, int keyCode) {
        super(Component.translatable("gui.ae2enhanced.placement_radial.title"));
        this.player = player;
        this.config = new PlacementConfig(player.getMainHandItem());
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
    protected void init() {
        super.init();
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
        if (PlacementTargetResolver.isSameCableType(a, b)) {
            return true;
        }
        return a.getItem() == b.getItem()
                && ItemStack.isSameItemSameTags(a, b);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int cx = width / 2;
        int cy = height / 2;
        int sectors = visibleEntries.size();

        guiGraphics.drawCenteredString(font, title, cx, cy - RADIUS - 24, 0xFFFFFF);

        hoveredSector = getHoveredSector(mouseX, mouseY, cx, cy, sectors);

        // 绘制物品
        for (int i = 0; i < sectors; i++) {
            double angle = getSectorAngle(i, sectors);
            int x = cx + (int) (Math.cos(angle) * RADIUS) - ITEM_SIZE / 2;
            int y = cy - (int) (Math.sin(angle) * RADIUS) - ITEM_SIZE / 2;

            if (i == hoveredSector) {
                guiGraphics.fill(x - 2, y - 2, x + ITEM_SIZE + 2, y + ITEM_SIZE + 2, 0x80FFFFFF);
            }

            SlotEntry entry = visibleEntries.get(i);
            if (!entry.stack.isEmpty()) {
                guiGraphics.renderItem(entry.stack, x + 1, y + 1);
                guiGraphics.renderItemDecorations(font, entry.stack, x + 1, y + 1);
            } else {
                // 空选项：绘制一个空心框
                guiGraphics.fill(x + 1, y + 1, x + ITEM_SIZE - 1, y + ITEM_SIZE - 1, 0x30FFFFFF);
                guiGraphics.drawCenteredString(font, "∅", x + ITEM_SIZE / 2 + 1, y + ITEM_SIZE / 2 - 4, 0xFFFFFF);
            }
        }

        // 底部提示
        Component hint;
        if (hoveredSector >= 0 && hoveredSector < visibleEntries.size()) {
            SlotEntry entry = visibleEntries.get(hoveredSector);
            if (entry.actualSlot == SLOT_EMPTY) {
                hint = Component.translatable("gui.ae2enhanced.placement_radial.empty");
            } else {
                hint = entry.stack.getHoverName();
            }
        } else {
            hint = Component.translatable("gui.ae2enhanced.placement_radial.aim_to_select");
        }
        guiGraphics.drawCenteredString(font, hint, cx, cy + RADIUS + 16, 0xCCCCCC);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        if (!InputConstants.isKeyDown(minecraft.getWindow().getWindow(), keyCode)) {
            confirmSelection();
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        confirmSelection();
        onClose();
        return true;
    }

    private void confirmSelection() {
        if (hoveredSector < 0 || hoveredSector >= visibleEntries.size()) return;
        SlotEntry entry = visibleEntries.get(hoveredSector);
        ModNetwork.CHANNEL.sendToServer(new PacketPlacementSelectPreset(entry.actualSlot));
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
        int index = Mth.floor(adjusted / sectorSize);
        return Mth.clamp(index, 0, sectors - 1);
    }

    private double getSectorAngle(int index, int sectors) {
        double sectorSize = 2 * Math.PI / sectors;
        double startOffset = Math.PI / 2 - sectorSize / 2;
        return startOffset + index * sectorSize + sectorSize / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
