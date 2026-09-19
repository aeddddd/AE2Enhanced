package com.github.aeddddd.ae2enhanced.guide;

import com.github.aeddddd.ae2enhanced.guide.client.GuiGuide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * 按住 G 打开指南 —— 对齐 GuideME OpenGuideHotkey：
 * 悬停有关联页面的物品时 tooltip 显示「按住 G 查看指南」与进度条，
 * 按住满 10 tick（0.5s）打开对应页面，松开每 tick -2。
 */
@SideOnly(Side.CLIENT)
public final class GuideHotkeyHandler {

    public static final KeyBinding OPEN_GUIDE_KEY = new KeyBinding(
            "key.ae2enhanced.openGuide",
            KeyConflictContext.GUI,
            Keyboard.KEY_G,
            "key.categories.ae2enhanced"
    );

    private static final int TICKS_TO_OPEN = 10;
    private static final int BAR_LENGTH = 20;

    private Item lastItem;
    private PageAnchor lastAnchor;
    private int ticksHeld;

    /**
     * 追加「按住 G 查看指南」tooltip 行（含进度条）.
     */
    @SubscribeEvent
    public void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        if (!com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.guide.enabled) {
            return;
        }
        if (event.getEntityPlayer() != Minecraft.getMinecraft().player) {
            return;
        }
        if (OPEN_GUIDE_KEY.getKeyCode() == 0) {
            reset();
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            reset();
            return;
        }
        GuideBook book = GuideManager.getInstance().getBook();
        if (book == null) {
            reset();
            return;
        }
        Item item = stack.getItem();
        if (item != lastItem) {
            lastItem = item;
            lastAnchor = book.getPageForItem(item);
            ticksHeld = 0;
        }
        if (lastAnchor == null) {
            return;
        }
        // 已在目标页面上时不显示提示
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiGuide && lastAnchor.getPageId().equals(((GuiGuide) screen).getCurrentPageId())) {
            return;
        }

        List<String> tooltip = event.getToolTip();
        tooltip.add(Math.min(1, tooltip.size()), buildTooltipLine());
    }

    /**
     * 按 tick 累计按住时长，到阈值打开指南.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.guide.enabled) {
            reset();
            return;
        }
        if (lastAnchor == null || OPEN_GUIDE_KEY.getKeyCode() == 0) {
            ticksHeld = 0;
            return;
        }
        boolean holding = Keyboard.isKeyDown(OPEN_GUIDE_KEY.getKeyCode());
        if (holding) {
            if (++ticksHeld >= TICKS_TO_OPEN) {
                openGuide();
                ticksHeld = 0;
            }
        } else {
            ticksHeld = Math.max(0, ticksHeld - 2);
        }
    }

    private void openGuide() {
        PageAnchor anchor = lastAnchor;
        if (anchor == null) {
            return;
        }
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiGuide) {
            // 已是指南界面：直接导航而非重开（对齐 GuideME GuideUiHost 行为）
            ((GuiGuide) screen).navigateTo(anchor.getPageId(), anchor.getAnchor());
        } else {
            GuiGuide.open(anchor);
        }
    }

    private String buildTooltipLine() {
        String keyName = OPEN_GUIDE_KEY.getDisplayName();
        String label = net.minecraft.client.resources.I18n.format("guide.ae2enhanced.hold_to_show", keyName);
        if (ticksHeld <= 0) {
            return "§8" + label;
        }
        // 进度条：已填充部分亮灰，未填充暗灰（对齐 GuideME 的 | 字符进度条）
        int filled = Math.min(BAR_LENGTH, ticksHeld * BAR_LENGTH / TICKS_TO_OPEN);
        StringBuilder bar = new StringBuilder("§7");
        for (int i = 0; i < BAR_LENGTH; i++) {
            if (i == filled) {
                bar.append("§8");
            }
            bar.append('|');
        }
        return "§8" + label + " " + bar;
    }

    private void reset() {
        lastItem = null;
        lastAnchor = null;
        ticksHeld = 0;
    }
}
