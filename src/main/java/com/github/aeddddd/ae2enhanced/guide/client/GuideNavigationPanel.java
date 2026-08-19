package com.github.aeddddd.ae2enhanced.guide.client;

import com.github.aeddddd.ae2enhanced.guide.GuideBook;
import com.github.aeddddd.ae2enhanced.guide.GuideManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 指南导航面板 —— 左侧导航树（parent/position 排序、可折叠、支持二级目录）.
 * 颜色全部取自当前 GuideTheme。
 */
public final class GuideNavigationPanel {

    private static final int ROW_HEIGHT = 16;
    private static final int INDENT = 12;
    private static final int ARROW_WIDTH = 10;

    private final GuiGuide gui;
    private int x, y, width, height;
    private int scrollY;
    private final Set<String> collapsed = new HashSet<>();

    private final List<Row> rows = new ArrayList<>();

    private static final class Row {
        final GuideBook.NavNode node;
        final int depth;

        Row(GuideBook.NavNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }

    public GuideNavigationPanel(GuiGuide gui) {
        this.gui = gui;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * 重建可见行（展开状态变化或指南重载后调用）.
     */
    public void rebuildRows() {
        rows.clear();
        GuideBook book = GuideManager.getInstance().getBook();
        if (book == null) {
            return;
        }
        for (GuideBook.NavNode root : book.getNavRoots()) {
            addRows(root, 0);
        }
        clampScroll();
    }

    private void addRows(GuideBook.NavNode node, int depth) {
        rows.add(new Row(node, depth));
        if (!collapsed.contains(node.pageId)) {
            for (GuideBook.NavNode child : node.children) {
                addRows(child, depth + 1);
            }
        }
    }

    public void render(int mouseX, int mouseY) {
        GuideTheme theme = gui.getTheme();
        // 背景与分隔线
        GuiScreen.drawRect(x, y, x + width, y + height, theme.panelBg);
        GuiScreen.drawRect(x + width - 1, y, x + width, y + height, theme.border);

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;
        String currentPage = gui.getCurrentPageId();

        int rowY = y + 2 - scrollY;
        for (Row row : rows) {
            if (rowY + ROW_HEIGHT >= y && rowY <= y + height) {
                int rowX = x + 4 + row.depth * INDENT;
                boolean isCurrent = row.node.pageId.equals(currentPage);
                boolean hover = mouseX >= x && mouseX < x + width - 1 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

                if (isCurrent) {
                    GuiScreen.drawRect(x + 1, rowY, x + width - 1, rowY + ROW_HEIGHT, theme.selection);
                } else if (hover) {
                    GuiScreen.drawRect(x + 1, rowY, x + width - 1, rowY + ROW_HEIGHT, theme.hover);
                }

                int textX = rowX;
                // 折叠箭头
                if (!row.node.children.isEmpty()) {
                    String arrow = collapsed.contains(row.node.pageId) ? ">" : "v";
                    fr.drawString(arrow, rowX, rowY + 4, theme.textMuted);
                    textX += ARROW_WIDTH;
                }

                // 图标
                if (row.node.icon != null && !row.node.icon.isEmpty()) {
                    drawItemStack(mc, row.node.icon, textX, rowY);
                    textX += 18;
                }

                // 标题
                int maxTextWidth = x + width - 4 - textX;
                String title = fr.trimStringToWidth(row.node.title, Math.max(10, maxTextWidth));
                int color = isCurrent ? theme.heading1 : theme.text;
                fr.drawString(title, textX, rowY + 4, color);
            }
            rowY += ROW_HEIGHT;
        }

        // 简易滚动条
        int totalH = rows.size() * ROW_HEIGHT + 4;
        if (totalH > height) {
            int trackX = x + width - 4;
            GuiScreen.drawRect(trackX, y, trackX + 3, y + height, theme.windowBg);
            int thumbH = Math.max(16, height * height / totalH);
            int maxScroll = totalH - height;
            int thumbY = y + (int) ((long) scrollY * (height - thumbH) / maxScroll);
            GuiScreen.drawRect(trackX, thumbY, trackX + 3, thumbY + thumbH, theme.scrollThumb);
        }
    }

    /**
     * 点击处理；返回 true 表示事件被消费.
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int index = (mouseY - y - 2 + scrollY) / ROW_HEIGHT;
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        Row row = rows.get(index);
        int rowX = x + 4 + row.depth * INDENT;
        // 箭头区域：折叠/展开
        if (!row.node.children.isEmpty() && mouseX >= rowX && mouseX < rowX + ARROW_WIDTH) {
            if (!collapsed.remove(row.node.pageId)) {
                collapsed.add(row.node.pageId);
            }
            rebuildRows();
            return true;
        }
        gui.navigateTo(row.node.pageId, null);
        return true;
    }

    public void scrollWheel(int dWheel) {
        // 兼容 CRL lwjglxx（返回 ±1）与标准 LWJGL2（返回 ±120）,统一按符号滚动
        this.scrollY -= Integer.signum(dWheel) * ROW_HEIGHT * 2;
        clampScroll();
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void clampScroll() {
        int totalH = rows.size() * ROW_HEIGHT + 4;
        int maxScroll = Math.max(0, totalH - height);
        scrollY = Math.max(0, Math.min(scrollY, maxScroll));
    }

    private static void drawItemStack(Minecraft mc, ItemStack stack, int x, int y) {
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().zLevel = 100.0F;
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        mc.getRenderItem().zLevel = 0.0F;
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
