/*
 * Derived from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockAxis;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutProjection;

/**
 * 面板 chrome 的像素级原语：整窗 9-slice 背景、投影、区段分隔线。
 *
 * <p>MEST 侧对应 {@code ModulePanel.drawGeneratedBackground}（AE2 {@code BackgroundGenerator}：
 * 4px bevel 包住平铺中部）、{@code ModulePanel.renderDropShadow}、{@code ModulePanel.renderRootChrome}
 * 与 {@code ModulePanel.drawSectionRules}。1.12.2 没有 {@code GuiGraphics}/pose stack，因此：</p>
 * <ul>
 *   <li>纹理绘制用 {@code Minecraft.getMinecraft().getTextureManager().bindTexture(...)} +
 *       {@code Gui.drawModalRectWithCustomSizedTexture}（源矩形与目标矩形同尺寸，1 纹理像素 = 1 GUI 像素）；</li>
 *   <li>投影与分隔线用 {@code Gui.drawRect}（1.12.2 的实现自带 blend 开关与 blendFunc）；</li>
 *   <li>分层：每次绘制先 {@code pushMatrix} 并 {@code translate(0,0,zLevel)}，返回前 {@code popMatrix}，
 *       因此 {@code zLevel} 就是 MEST 里"画在谁上面"的显式表达；同一 zLevel 内则按调用顺序叠加
 *       （后画的在上），与 MEST 的绘制顺序一致。</li>
 * </ul>
 *
 * <p>绘制结束时混合（blend）被关闭、{@code GlStateManager.color} 复位为 (1,1,1,1)，与 vanilla GUI
 * 的默认状态一致；调用方若在两次 chrome 绘制之间自行开启了混合/改色，需要自己重设。</p>
 */
public final class NinePatch {
    private NinePatch() {
    }

    /**
     * 绘制 AE2 生成式窗口背景：4px bevel 边框 + 平铺中部。
     *
     * <p>{@code width} 或 {@code height} 小于 8 时直接返回（MEST 的早退条件），这样极窄的分段不会画出
     * 互相重叠的边框。</p>
     */
    public static void draw(int x, int y, int width, int height, float zLevel) {
        if (width < 2 * ChromeLayout.BACKGROUND_BORDER || height < 2 * ChromeLayout.BACKGROUND_BORDER) {
            return;
        }
        float tex = ChromeLayout.TEXTURE_SIZE;
        boolean pushed = pushZLevel(zLevel);
        Minecraft.getMinecraft().getTextureManager().bindTexture(ChromeLayout.BACKGROUND_TEXTURE);
        // drawRect 会把 GlStateManager 的当前颜色留在矩形色上，纹理绘制前必须复位，否则面板会被染色
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        forEachSlice(x, y, width, height, (u, v, destX, destY, sliceW, sliceH) ->
                Gui.drawModalRectWithCustomSizedTexture(destX, destY, u, v, sliceW, sliceH, tex, tex));

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        popZLevel(pushed);
    }

    /** 一个 9-slice 切片：源矩形左上角 {@code (u,v)} + 目标左上角，两者尺寸相同（1 纹理像素 = 1 GUI 像素）。 */
    interface SliceConsumer {
        void accept(float u, float v, int destX, int destY, int width, int height);
    }

    /**
     * 遍历整窗背景的全部切片：四角 → 上/下边条 → 平铺中部 → 左/右边条（顺序与 MEST 相同，后画者在上）。
     *
     * <p>切片的"无缝且不重叠覆盖整个面板矩形"是 9-slice 唯一的硬性不变量，因此这段几何单独抽出成
     * 纯计算，供 {@link #draw} 与单元测试共用；{@link SliceConsumer} 收到的尺寸恒等于目标尺寸，
     * 所以测试可以据此逐像素统计覆盖率。</p>
     */
    static void forEachSlice(int x, int y, int width, int height, SliceConsumer consumer) {
        int border = ChromeLayout.BACKGROUND_BORDER;
        if (width < 2 * border || height < 2 * border) {
            return;
        }
        int tile = ChromeLayout.BACKGROUND_TILE;
        float tex = ChromeLayout.TEXTURE_SIZE;
        int innerX = x + border;
        int innerY = y + border;
        int innerW = width - 2 * border;
        int innerH = height - 2 * border;
        int right = x + width;
        int bottom = y + height;

        // 四角
        consumer.accept(0.0F, 0.0F, x, y, border, border);
        consumer.accept(tex - border, 0.0F, right - border, y, border, border);
        consumer.accept(0.0F, tex - border, x, bottom - border, border, border);
        consumer.accept(tex - border, tex - border, right - border, bottom - border, border, border);

        // 上/下边条 + 平铺中部
        for (int cx = 0; cx < innerW; cx += tile) {
            int tileW = Math.min(tile, innerW - cx);
            consumer.accept(border, 0.0F, innerX + cx, y, tileW, border);
            consumer.accept(border, tex - border, innerX + cx, bottom - border, tileW, border);
            for (int cy = 0; cy < innerH; cy += tile) {
                int tileH = Math.min(tile, innerH - cy);
                consumer.accept(border, border, innerX + cx, innerY + cy, tileW, tileH);
            }
        }
        // 左/右边条
        for (int cy = 0; cy < innerH; cy += tile) {
            int tileH = Math.min(tile, innerH - cy);
            consumer.accept(0.0F, border, x, innerY + cy, border, tileH);
            consumer.accept(tex - border, border, right - border, innerY + cy, border, tileH);
        }
    }

    /** 与 {@link #draw(int, int, int, int, float)} 相同，{@code zLevel = 0}。 */
    public static void draw(int x, int y, int width, int height) {
        draw(x, y, width, height, 0.0F);
    }

    /**
     * 两笔投影：底边（含右下角）与右边。
     *
     * <p>MEST 在面板右侧停靠了"越界 chrome"（编码模式页签）时会跳过右边那笔，1.12.2 阶段不做越界
     * chrome，因此 {@code skipRightShadow} 只保留参数语义，由调用方决定；{@link #drawRootChrome}
     * 会把 {@code DockManager} 的判断透传进来。</p>
     */
    public static void drawDropShadow(int x, int y, int width, int height, boolean skipRightShadow,
            float zLevel) {
        boolean pushed = pushZLevel(zLevel);
        Gui.drawRect(x + 2, y + height, x + width + 2, y + height + 2, ChromeLayout.DROP_SHADOW_COLOR);
        if (!skipRightShadow) {
            Gui.drawRect(x + width, y + 2, x + width + 2, y + height + 2, ChromeLayout.DROP_SHADOW_COLOR);
        }
        popZLevel(pushed);
    }

    /**
     * 一个拼接根窗口的完整 chrome：投影 + 生成式背景。
     *
     * <p>MEST 只在整个 root 上画一次 9-slice（而不是每个叶子各画一次），这正是"外壳模式"不会出现接缝的
     * 原因；叶子只画区段标题与内容。宽度/高度小于 2 时连投影都不画（MEST 的早退条件）。</p>
     */
    public static void drawRootChrome(DockRect bounds, boolean skipRightShadow, float zLevel) {
        if (bounds == null || bounds.width() < 2 || bounds.height() < 2) {
            return;
        }
        drawDropShadow(bounds.x(), bounds.y(), bounds.width(), bounds.height(), skipRightShadow, zLevel);
        draw(bounds.x(), bounds.y(), bounds.width(), bounds.height(), zLevel);
    }

    /** 与 {@link #drawRootChrome(DockRect, boolean, float)} 相同，{@code zLevel = 0}。 */
    public static void drawRootChrome(DockRect bounds, boolean skipRightShadow) {
        drawRootChrome(bounds, skipRightShadow, 0.0F);
    }

    /**
     * 单条区段分隔线（1px）。
     *
     * <p>外壳模式用一整块背景盖住整个复合窗口，区段边界靠这条线而不是每个叶子的 bevel 来表达；
     * 舍入误差最多让线偏移 1px，绝不会像"隐藏边拼接"那样露出双边框。</p>
     */
    public static void drawSectionRule(DockRect bounds, DockAxis axis, boolean hovered, float zLevel) {
        if (bounds == null || axis == null) {
            return;
        }
        int color = hovered ? ChromeLayout.SECTION_RULE_HOVER_COLOR : ChromeLayout.SECTION_RULE_COLOR;
        boolean pushed = pushZLevel(zLevel);
        if (axis == DockAxis.HORIZONTAL) {
            Gui.drawRect(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), color);
        } else {
            Gui.drawRect(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, color);
        }
        popZLevel(pushed);
    }

    /**
     * 画出一组投影分隔线；{@code hoveredSplitNodeId} 命中的那条用高亮色（MEST
     * {@code ModulePanel.drawSectionRules} 的等价实现，直接消费 {@link LayoutProjection} 的产物）。
     */
    public static void drawSectionRules(
            List<LayoutProjection.DividerPlacement> dividers,
            String hoveredSplitNodeId,
            float zLevel) {
        if (dividers == null || dividers.isEmpty()) {
            return;
        }
        for (LayoutProjection.DividerPlacement divider : dividers) {
            if (divider == null) {
                continue;
            }
            boolean hovered = hoveredSplitNodeId != null && hoveredSplitNodeId.equals(divider.splitNodeId());
            drawSectionRule(divider.bounds(), divider.axis(), hovered, zLevel);
        }
    }

    /** 与 {@link #drawSectionRules(List, String, float)} 相同，{@code zLevel = 0}。 */
    public static void drawSectionRules(List<LayoutProjection.DividerPlacement> dividers,
            String hoveredSplitNodeId) {
        drawSectionRules(dividers, hoveredSplitNodeId, 0.0F);
    }

    // ==================== AE2 19.x 视觉原语（素材直搬，见 licenses/AE2-ASSETS.txt） ====================

    /**
     * 画一个 AE2 19.x 槽格（18x18 精灵：16x16 槽体 #ADB0C4 + 1px 深色顶线，四周 1px 透明）。
     *
     * <p>透明边让面板底（#CBCCD4）透出，正好复现 AE2 19.x 终端里槽与槽之间 2px 间隙的观感；
     * 旧版"逐格凹陷 + 右下高光"的 vanilla 画法已删除。</p>
     */
    public static void drawSlotCell(int x, int y, float zLevel) {
        Ae2ModernArt.drawSlot(x, y, zLevel);
    }

    /** 画 {@code columns × rows} 的槽带（每格 18×18，AE2 19.x 槽格）。 */
    public static void drawSlotBand(int left, int top, int columns, int rows, float zLevel) {
        Ae2ModernArt.drawSlotBand(left, top, columns, rows, zLevel);
    }

    /** 槽内的槽位图案（view cell / 空白样板 / 已编码样板）。 */
    public static void drawSlotPattern(Ae2ModernArt.Icon pattern, int slotX, int slotY, float zLevel) {
        Ae2ModernArt.drawSlotPattern(pattern, slotX, slotY, zLevel);
    }

    /** 贴一个 states.png 图标。 */
    public static void drawIcon(Ae2ModernArt.Icon icon, int x, int y, float zLevel) {
        Ae2ModernArt.drawIcon(icon, x, y, zLevel);
    }

    /** 画 AE2 19.x 输入框（text_field.png：左右 1px 边 + 中间拉伸，12px 高）。 */
    public static void drawTextField(int x, int y, int width, int height, float zLevel) {
        Ae2ModernArt.drawTextField(x, y, width, height, false, true, zLevel);
    }

    /** 同上，带聚焦态（19.x 输入框聚焦时换一条带）。 */
    public static void drawTextField(int x, int y, int width, int height, boolean focused, float zLevel) {
        Ae2ModernArt.drawTextField(x, y, width, height, focused, true, zLevel);
    }

    /**
     * 画 AE2 19.x 滚动条（只有滑块贴图，没有轨道——19.x 的 {@code Scrollbar} 只画 handle）。
     *
     * @param scroll    当前滚动位置（{@code GuiScrollbar.getCurrentScroll()}）
     * @param maxScroll 最大滚动位置（0 = 禁用态）
     */
    public static void drawScrollbar(int x, int y, int width, int height,
            int scroll, int maxScroll, boolean hovered, float zLevel) {
        Ae2ModernArt.drawScrollbar(x, y, width, height, scroll, maxScroll, hovered, zLevel);
    }

    /** 画 AE2 19.x 按钮底（button*.png 九宫格，border=3）。 */
    public static void drawButtonBase(int x, int y, int width, int height, boolean hovered, float zLevel) {
        Ae2ModernArt.drawButton(x, y, width, height, hovered, true, zLevel);
    }

    /** 画按钮底（带禁用态）。 */
    public static void drawButtonBase(int x, int y, int width, int height, boolean hovered,
            boolean enabled, float zLevel) {
        Ae2ModernArt.drawButton(x, y, width, height, hovered, enabled, zLevel);
    }

    /** 画合成箭头（矩阵 → 输出）：AE2 19.x crafting.png 里的官方箭头。 */
    public static void drawCraftingArrow(int x, int y, int width, int height, float zLevel) {
        Ae2ModernArt.drawCraftingArrow(x, y, width, height, zLevel);
    }

    /** 画编码区模式底（124x66，craft / process）。 */
    public static void drawPatternModeBlock(int x, int y, boolean craftMode, float zLevel) {
        Ae2ModernArt.drawPatternModeBlock(x, y, craftMode, zLevel);
    }

    /** 画数量弹窗底（176x107）。 */
    public static void drawAmountDialog(int x, int y, float zLevel) {
        Ae2ModernArt.drawAmountDialog(x, y, zLevel);
    }

    /** 按需平移出 z 深度；返回是否需要 popMatrix。公开给面板/宿主在自绘内容时表达分层。 */
    public static boolean pushZLevel(float zLevel) {
        if (zLevel == 0.0F) {
            return false;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, zLevel);
        return true;
    }

    public static void popZLevel(boolean pushed) {
        if (pushed) {
            GlStateManager.popMatrix();
        }
    }
}
