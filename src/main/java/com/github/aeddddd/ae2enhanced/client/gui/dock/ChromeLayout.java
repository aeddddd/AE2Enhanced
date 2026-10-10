/*
 * Derived from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock;

import net.minecraft.util.ResourceLocation;


/**
 * 停靠面板 chrome 的几何/颜色常量集中处。
 *
 * <p>对应 MEST 的 {@code DockChromeLayout}（几何）+ {@code MestPanelSkin}（皮肤）+ {@code ModulePanel}
 * 内部的那批 chrome 常量。1.12.2 重写时不引入 AE2 的 {@code Blitter}/{@code Icon} 抽象，所有 9-slice
 * 与调色板数值集中在这里，渲染动作在 {@link NinePatch}，面板基类在 {@link ModulePanel}。</p>
 *
 * <p>本阶段<strong>不</strong>包含 MEST {@code DockChromeLayout} 的两个算法（{@code markRightmostLeaves}
 * 标记最右侧叶子、{@code joinOutsideRails} 合并相邻滚动轨背景），它们属于停靠引擎（DockManager），
 * 由后续任务移植。</p>
 */
public final class ChromeLayout {
    // --- 面板几何（MEST ModulePanel 的公开常量，数值逐字保留） -------------------------

    /** 标题栏（拖动手柄）高度。 */
    public static final int TITLE_BAR_HEIGHT = 17;
    /** 内容区与面板边框之间的内边距。 */
    public static final int CONTENT_PADDING = 7;
    /** 右下角缩放握把的命中尺寸。 */
    public static final int RESIZE_HANDLE = 10;
    /** 标题文字相对面板左边的缩进。 */
    public static final int TITLE_LEFT_INSET = 8;
    /** 标题栏 chrome 按钮边长。 */
    public static final int PIN_SIZE = 12;
    /** 标题栏 chrome 按钮间距。 */
    public static final int CHROME_BUTTON_GAP = 2;

    // --- 9-slice 窗口背景 --------------------------------------------------------------

    /**
     * 9-slice 源纹理（256x256，4px 边框，中部 248x248 平铺）。
     *
     * <p>这是 <b>Applied Energistics 2 19.2.17</b> 的官方 {@code guis/background.png} 原样文件
     * （窗口框：外缘 #413F54、高光 #F2F2F2、主体 #CBCCD4、右/下阴影 #878FA5）。见
     * {@code licenses/AE2-ASSETS.txt}（CC BY-NC-SA 3.0）。</p>
     */
    public static final ResourceLocation BACKGROUND_TEXTURE = Ae2ModernArt.BACKGROUND;
    /** 源纹理边长。 */
    public static final int TEXTURE_SIZE = Ae2ModernArt.BACKGROUND_SIZE;
    /** 9-slice 边框宽度（AE2 {@code BackgroundGenerator} 的 {@code BG_BORDER}）。 */
    public static final int BACKGROUND_BORDER = Ae2ModernArt.BACKGROUND_BORDER;
    /** 9-slice 中部平铺步长（{@code TEXTURE_SIZE - 2 * BACKGROUND_BORDER}）。 */
    public static final int BACKGROUND_TILE = Ae2ModernArt.BACKGROUND_TILE;

    // --- 调色板（AE2 19.2.17 逐像素采样，与 ae2modern/ 贴图同族） ----------------------

    /** 面板底（与 background.png 主体一致）。 */
    public static final int COLOR_PANEL = Ae2ModernArt.PLATE;
    /** 高光 / hover 面。 */
    public static final int COLOR_LIGHT = Ae2ModernArt.HIGHLIGHT;
    /** 深压色（禁用面、深描边）。 */
    public static final int COLOR_DARK = Ae2ModernArt.DEEP;
    /** 中调（次要文字、阴影）。 */
    public static final int COLOR_MUTED = Ae2ModernArt.MUTED;
    /** 正文/标题文字色。 */
    public static final int COLOR_TITLE_TEXT = Ae2ModernArt.OUTLINE;

    /** Faint 1px rule separating sections inside an outer shell. */
    public static final int SECTION_RULE_COLOR = 0x66777B8C;
    /** The same rule while the pointer sits on its divider. */
    public static final int SECTION_RULE_HOVER_COLOR = 0xCCACE9FF;

    // --- AE2 19.x 视觉原语色值（贴图之外的纯色补边 / 文字 / 投影） ---

    /** AE2 槽体 / 内容底（states.png SLOT_BACKGROUND 主体）。 */
    public static final int SLOT_BG = Ae2ModernArt.SLOT;
    /** AE2 槽格深色线 / 按钮面。 */
    public static final int SLOT_SHADOW = Ae2ModernArt.LINE;
    /** AE2 高光（19.x 的 F2F2F2）。 */
    public static final int SLOT_HIGHLIGHT = Ae2ModernArt.HIGHLIGHT;
    /** 输入框描边（19.x OUTLINE）。 */
    public static final int FIELD_BORDER = Ae2ModernArt.OUTLINE;
    /** 输入框内沿（19.x LINE）。 */
    public static final int FIELD_BAND = Ae2ModernArt.LINE;
    /** 输入区底色（19.x SLOT）。 */
    public static final int FIELD_BG = Ae2ModernArt.SLOT;
    /** 滚动条轨道（19.x 已无轨道，保留常量供旧调用点/主题一致性使用）。 */
    public static final int SCROLL_TRACK = Ae2ModernArt.PLATE;
    /** 滚动条滑块。 */
    public static final int SCROLL_THUMB = Ae2ModernArt.SLOT;
    /** 滚动条滑块 hover。 */
    public static final int SCROLL_THUMB_HOVER = Ae2ModernArt.HOVER;
    /** 滚动条轨道宽（大滑块 12px）。 */
    public static final int SCROLL_WIDTH = 12;
    /** 滚动条滑块高（AE2 {@code big_scroller} 高 15px）。 */
    public static final int SCROLL_THUMB_HEIGHT = 15;

    /** 面板投影（MEST {@code renderDropShadow} 的 0x55000000）。 */
    public static final int DROP_SHADOW_COLOR = 0x55000000;

    /** 标题栏按钮底色（AE2 19.x 按钮面 #9A9FB4）。 */
    public static final int CHROME_BUTTON_COLOR = Ae2ModernArt.LINE;
    /** 标题栏按钮 hover 底色（AE2 19.x 高亮面 #9CD3FF）。 */
    public static final int CHROME_BUTTON_HOVER_COLOR = Ae2ModernArt.HOVER;
    /** 标题栏按钮描边（AE2 19.x 最深色）。 */
    public static final int CHROME_BUTTON_BORDER = Ae2ModernArt.OUTLINE;
    /** 标题栏按钮字形颜色。 */
    public static final int CHROME_GLYPH_COLOR = Ae2ModernArt.OUTLINE;

    /** 内容编辑参考线：区段中线。 */
    public static final int CONTENT_GUIDE_SECTION_COLOR = 0x66ACE9FF;
    /** 内容编辑参考线：内容块中线。 */
    public static final int CONTENT_GUIDE_CONTENT_COLOR = 0xBBACE9FF;
    /** 内容编辑参考线：已吸附。 */
    public static final int CONTENT_GUIDE_SNAP_COLOR = 0xFFE8F7FF;

    private ChromeLayout() {
    }
}
