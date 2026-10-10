package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;

/**
 * 演示用占位面板：只画一个带边框的纯色内容块 + 身份/尺寸文字，用于验证 dock 的手势、拼接、
 * 分隔条与缩放行为。真实模块（阶段 2 的终端面板）会各自实现 {@link ModulePanel}。
 */
public abstract class DummyPanel extends ModulePanel {
    private final String moduleId;
    private final int minimumWidth;
    private final int minimumHeight;
    private final int defaultWidth;
    private final int defaultHeight;
    private final boolean expandVertically;

    protected DummyPanel(String moduleId, int minimumWidth, int minimumHeight, int defaultWidth,
            int defaultHeight, boolean expandVertically) {
        this.moduleId = moduleId;
        this.minimumWidth = minimumWidth;
        this.minimumHeight = minimumHeight;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.expandVertically = expandVertically;
    }

    @Override
    public String id() {
        return moduleId;
    }

    @Override
    public String title() {
        return I18n.format("gui.ae2enhanced.dock.demo." + moduleId.substring("demo_".length()));
    }

    @Override
    public int minWidth() {
        return minimumWidth;
    }

    @Override
    public int minHeight() {
        return minimumHeight;
    }

    @Override
    public int defaultWidth() {
        return defaultWidth;
    }

    @Override
    public int defaultHeight() {
        return defaultHeight;
    }

    @Override
    public boolean expandsVertically() {
        return expandVertically;
    }

    @Override
    public void layoutSlots() {
        // 演示面板没有槽位（本阶段不注册 Container）
    }

    @Override
    public void renderBackgroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
        int left = contentLeft();
        int top = contentTop();
        int right = left + contentWidth();
        int bottom = top + contentHeight();
        if (right <= left || bottom <= top) {
            return;
        }
        boolean pushed = NinePatch.pushZLevel(zLevel);
        Gui.drawRect(left, top, right, bottom, ChromeLayout.COLOR_LIGHT);
        // 1px 边框
        Gui.drawRect(left, top, right, top + 1, ChromeLayout.COLOR_MUTED);
        Gui.drawRect(left, bottom - 1, right, bottom, ChromeLayout.COLOR_MUTED);
        Gui.drawRect(left, top, left + 1, bottom, ChromeLayout.COLOR_MUTED);
        Gui.drawRect(right - 1, top, right, bottom, ChromeLayout.COLOR_MUTED);
        NinePatch.popZLevel(pushed);

        String label = moduleId + " " + width + "x" + height;
        if (spliced) {
            label = label + " (spliced)";
        }
        Minecraft.getMinecraft().fontRenderer.drawString(label, left + 3, top + 3,
                ChromeLayout.COLOR_TITLE_TEXT, false);
    }

    /** 演示用 5 个模块，顺序即 {@code DockWorkspaceDefaults} 的级联摆放顺序。 */
    public static List<ModulePanel> createDefaultPanels() {
        List<ModulePanel> panels = new ArrayList<ModulePanel>();
        panels.add(new RepoPanel());
        panels.add(new CraftingPanel());
        panels.add(new PatternPanel());
        panels.add(new StoragePanel());
        panels.add(new InventoryPanel());
        return panels;
    }

    public static final class RepoPanel extends DummyPanel {
        public RepoPanel() {
            super("demo_repo", 90, 60, 160, 110, false);
        }
    }

    public static final class CraftingPanel extends DummyPanel {
        public CraftingPanel() {
            super("demo_crafting", 100, 70, 180, 140, false);
        }
    }

    public static final class PatternPanel extends DummyPanel {
        public PatternPanel() {
            super("demo_pattern", 80, 60, 150, 120, false);
        }
    }

    public static final class StoragePanel extends DummyPanel {
        public StoragePanel() {
            super("demo_storage", 60, 42, 110, 70, false);
        }
    }

    public static final class InventoryPanel extends DummyPanel {
        public InventoryPanel() {
            super("demo_inventory", 90, 80, 170, 150, true);
        }
    }
}
