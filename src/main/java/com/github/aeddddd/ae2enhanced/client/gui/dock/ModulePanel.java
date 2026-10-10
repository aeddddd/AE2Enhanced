/*
 * Derived from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutProjection;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleDefaults;

/**
 * Base class for a floating, draggable, resizable module panel.
 *
 * <p>A panel owns a rectangle on screen ({@link #x},{@link #y},{@link #width},{@link #height}) made of
 * a title bar (drag handle) and a content area. It also owns a set of menu {@link Slot}s; the panel
 * lays them out in <em>content-local</em> coordinates and the managing screen translates those into
 * absolute screen coordinates.
 *
 * <p>The docking engine is intentionally decoupled from concrete modules: subclasses only implement
 * content rendering, slot layout and a stable {@link #id()} used for layout persistence.
 *
 * <h3>1.12.2 重写说明</h3>
 * <ul>
 *   <li>MEST 的 {@code GuiGraphics}/{@code Component}/{@code Font} 参数换成 1.12.2 的
 *       {@code Gui}/{@code String}/{@code FontRenderer}：文字用
 *       {@code Minecraft.getMinecraft().fontRenderer}，{@code title()} 直接返回已本地化的
 *       {@code String}（实现方用 {@code I18n.format(...)}）。</li>
 *   <li>槽位原语面向 1.12.2 的 {@link Slot#xPos}/{@link Slot#yPos}（对应 1.21 的
 *       {@code Slot.x}/{@code Slot.y}）。本阶段没有 Container，因此只提供机制、不注册任何槽位。</li>
 *   <li>渲染分层用 {@code zLevel} + 绘制顺序表达（{@link NinePatch} 内做 pushMatrix/translate/popMatrix）。</li>
 *   <li>越界 chrome（{@code isOutsideChrome}/{@code hasJoinableOutsideRail}/{@code outsideHitWidth*}/
 *       合并滚动轨）与 {@code DockChromeLayout} 的最右侧判定属于停靠引擎，本阶段<strong>不做</strong>；
 *       {@code renderDropShadow} 因而总是画右边那笔投影。</li>
 *   <li>图标相关（{@code icon()} 返回 AE2 {@code Icon}、{@code drawButton}/{@code drawTextField}/
 *       {@code drawCraftingArrow}/{@code drawSlot} 依赖 AE2 1.20+ 的 sprite）只保留可覆写钩子，
 *       默认空实现，等具体面板阶段接入 AE2-UEL 的贴图。</li>
 * </ul>
 */
public abstract class ModulePanel {
    public static final int TITLE_BAR_HEIGHT = ChromeLayout.TITLE_BAR_HEIGHT;
    public static final int CONTENT_PADDING = ChromeLayout.CONTENT_PADDING;
    public static final int RESIZE_HANDLE = ChromeLayout.RESIZE_HANDLE;

    /** {@link #hideSlot(Slot)} 写入的坐标；{@link #renderSlots} 以 {@code <= -1000} 判定隐藏。 */
    public static final int HIDDEN_SLOT_POSITION = -9999;

    // Panel bounds in absolute screen coordinates.
    public int x;
    public int y;
    public int width;
    public int height;
    public boolean visible = true;
    // Compatibility flag for content-only hosts. The recursive dock keeps leaves unhosted so every
    // module retains its own title bar.
    public boolean hosted;
    /** True when this leaf shares a window with at least one other visible module. */
    public boolean spliced;
    /** Shared window bounds while {@link #spliced}; otherwise null. */
    public DockRect splicedWindow;
    /** Extra right padding so a sibling does not draw into the ME scroller gutter. */
    public int contentRightInset;
    /** Non-negative nudge of the content block inside this leaf. */
    public int contentOffsetX;
    public int contentOffsetY;
    /** True while the layout editor is nudging this leaf inside a shell section. */
    public boolean contentEditing;
    /** False when another visible leaf in the same window sits to the right. */
    public boolean rightmostInWindow = true;

    /** Reserved inner width on the right of this leaf (ME scroller when a sibling sits to the right). */
    public int preferredContentRightInset() {
        return 0;
    }

    private final List<Slot> ownedSlots = new ArrayList<Slot>();
    private final Set<Slot> ownedSlotSet = Collections.newSetFromMap(new IdentityHashMap<Slot, Boolean>());

    /** Stable identifier, used as the key for layout persistence and module recreation. */
    public abstract String id();

    /** Human-readable title shown in the title bar (already localized by the implementation). */
    public abstract String title();

    /** Minimum panel size; resizing cannot go below this. */
    public int minWidth() {
        return 60;
    }

    public int minHeight() {
        return TITLE_BAR_HEIGHT + CONTENT_PADDING + 18;
    }

    /** Initial/default size when no saved layout exists. */
    public abstract int defaultWidth();

    public abstract int defaultHeight();

    protected List<Slot> ownedSlots() {
        return ownedSlots;
    }

    protected void registerSlot(Slot slot) {
        ownedSlots.add(slot);
        ownedSlotSet.add(slot);
    }

    protected static void placeSlot(Slot slot, int screenX, int screenY) {
        slot.xPos = screenX;
        slot.yPos = screenY;
    }

    protected static void hideSlot(Slot slot) {
        slot.xPos = HIDDEN_SLOT_POSITION;
        slot.yPos = HIDDEN_SLOT_POSITION;
    }

    public static int slotScreenX(Slot slot) {
        return slot.xPos;
    }

    public static int slotScreenY(Slot slot) {
        return slot.yPos;
    }

    // --- Geometry helpers -------------------------------------------------

    public int contentLeft() {
        return x + CONTENT_PADDING + contentOffsetX;
    }

    public int contentTop() {
        return y + (drawsTitleBar() ? TITLE_BAR_HEIGHT : CONTENT_PADDING) + contentOffsetY;
    }

    public int contentWidth() {
        return Math.max(0, width - 2 * CONTENT_PADDING - contentRightInset);
    }

    public int contentHeight() {
        int top = drawsTitleBar() ? TITLE_BAR_HEIGHT : CONTENT_PADDING;
        return height - top - contentBottomPad();
    }

    /**
     * Intrinsic content size used when nudging inside a stretched shell section. Expanding
     * modules report the current content size, so they have no leftover slack to drag through.
     */
    public int preferredContentWidth() {
        if (fillsContentArea()) {
            return contentWidth();
        }
        return Math.max(0, defaultWidth() - 2 * CONTENT_PADDING - preferredContentRightInset());
    }

    public int preferredContentHeight() {
        if (fillsContentArea()) {
            return contentHeight();
        }
        int top = drawsTitleBar() ? TITLE_BAR_HEIGHT : CONTENT_PADDING;
        return Math.max(0, defaultHeight() - top - CONTENT_PADDING);
    }

    public int contentSlackX() {
        return Math.max(0, contentWidth() - preferredContentWidth());
    }

    public int contentSlackY() {
        return Math.max(0, contentHeight() - preferredContentHeight());
    }

    /** True when this panel grows its widgets to fill the section, leaving no nudge slack. */
    public boolean fillsContentArea() {
        return false;
    }

    /**
     * Spliced sections keep vanilla-tight spacing: only the bottom-most leaf in a window
     * still reserves the AE2 7px bottom inset.
     */
    private int contentBottomPad() {
        if (splicedWindow != null && y + height < splicedWindow.bottom()) {
            return 0;
        }
        return CONTENT_PADDING;
    }

    /** True when leftover vertical space should go to this leaf instead of a sibling. */
    public boolean expandsVertically() {
        return false;
    }

    /** How this module first appears in a layout that has never seen it. */
    public ModuleDefaults defaults() {
        return ModuleDefaults.STANDARD;
    }

    /**
     * 模块图标。1.12.2 没有 AE2 1.20+ 的 {@code Icon} 枚举，因此这里只留可覆写钩子；具体面板在自己的
     * 阶段接入 AE2-UEL 的 {@code states.png} 或自带贴图。
     */
    public void renderIcon(int iconX, int iconY, int iconWidth, int iconHeight, float opacity, float zLevel) {
    }

    /** Standalone windows and composite sections keep a title strip; compact rails do not. */
    protected boolean drawsTitleBar() {
        return !hosted;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    /**
     * 点是否落在本面板的<strong>内容区</strong>（排除标题栏）。
     *
     * <p>DockManager 用它判定"这次点击/滚轮该不该路由给面板自己的控件"：标题栏上的点击是窗口
     * 拖动/按钮的地盘，内容区才是面板自持控件（滚动条等）的地盘。</p>
     */
    public boolean containsContent(double mx, double my) {
        int left = contentLeft();
        int top = contentTop();
        return mx >= left && mx < left + contentWidth() && my >= top && my < top + contentHeight();
    }

    /** False when this module must stay a standalone window and cannot join a spliced split. */
    public boolean canSplice() {
        return true;
    }

    /** Whether this panel may be part of a combined module. */
    public boolean canCombine() {
        return true;
    }

    // --- Title-bar chrome buttons -----------------------------------------

    private boolean pinVisible;
    private boolean pinned;
    private boolean floatVisible;
    private boolean windowFloating;
    private int windowGroup;

    public void setPinControl(boolean visible, boolean pinned) {
        this.pinVisible = visible;
        this.pinned = pinned;
    }

    /**
     * Shown on one section per window. Anchored: floats the whole window. Floating (layout editor
     * only): cycles the window through combined modules 1 to 3.
     */
    public void setFloatControl(boolean visible, boolean floating, int group) {
        this.floatVisible = visible;
        this.windowFloating = floating;
        this.windowGroup = group;
    }

    public boolean floatVisible() {
        return floatVisible && drawsTitleBar() && !contentEditing;
    }

    public int floatButtonX() {
        return pinVisible() ? pinButtonX() - ChromeLayout.PIN_SIZE - ChromeLayout.CHROME_BUTTON_GAP
                : pinButtonX();
    }

    public boolean inFloatButton(double mx, double my) {
        return floatVisible() && inChromeButton(mx, my, floatButtonX(), pinButtonY());
    }

    private int chromeButtonCount() {
        return (pinVisible() ? 1 : 0) + (floatVisible() ? 1 : 0);
    }

    /** Title-bar width taken by the dock's own buttons, for panels that put controls beside them. */
    protected int chromeButtonsReserve() {
        int count = chromeButtonCount();
        return count == 0 ? 0 : count * ChromeLayout.PIN_SIZE
                + (count - 1) * ChromeLayout.CHROME_BUTTON_GAP + 4;
    }

    /** Left edge of the dock's title-bar buttons, or the title bar's right padding without any. */
    protected int chromeButtonsLeft() {
        return x + width - 4 - chromeButtonsReserve() + (chromeButtonCount() == 0 ? 0 : 4);
    }

    /**
     * 面板自有标题栏控件（设置按钮等）可用的右边界。
     *
     * <p>在 {@link #chromeButtonsLeft()} 基础上再让开关闭按钮的位置；面板把控件摆在
     * {@code chromeControlsRight()} 左边就不会被关闭按钮压住。</p>
     */
    protected int chromeControlsRight() {
        return chromeButtonsLeft()
                - (closeVisible() ? ChromeLayout.PIN_SIZE + ChromeLayout.CHROME_BUTTON_GAP : 0);
    }

    public boolean pinVisible() {
        return pinVisible && drawsTitleBar() && !contentEditing;
    }

    public boolean contentChromeVisible() {
        return contentEditing && drawsTitleBar();
    }

    /** 模块是否可以关闭。仓库与玩家背包覆盖为 {@code false}（关掉会让终端不可用）。 */
    public boolean closable() {
        return true;
    }

    /** 标题栏是否显示常驻关闭按钮（内容编辑态由 reset/close 那对按钮接管）。 */
    public boolean closeVisible() {
        return closable() && drawsTitleBar() && !contentEditing;
    }

    public boolean pinned() {
        return pinned;
    }

    public int pinButtonX() {
        return x + width - ChromeLayout.PIN_SIZE - 4;
    }

    public int pinButtonY() {
        return y + 3;
    }

    public int closeButtonX() {
        if (contentChromeVisible()) {
            return pinButtonX();
        }
        return chromeButtonsLeft() - ChromeLayout.PIN_SIZE - ChromeLayout.CHROME_BUTTON_GAP;
    }

    public int closeButtonY() {
        return pinButtonY();
    }

    public int resetButtonX() {
        return closeButtonX() - ChromeLayout.PIN_SIZE - ChromeLayout.CHROME_BUTTON_GAP;
    }

    public int resetButtonY() {
        return pinButtonY();
    }

    public boolean inPinButton(double mx, double my) {
        return pinVisible() && inChromeButton(mx, my, pinButtonX(), pinButtonY());
    }

    public boolean inCloseButton(double mx, double my) {
        return (contentChromeVisible() || closeVisible())
                && inChromeButton(mx, my, closeButtonX(), closeButtonY());
    }

    public boolean inResetButton(double mx, double my) {
        return contentChromeVisible() && inChromeButton(mx, my, resetButtonX(), resetButtonY());
    }

    /** 标题栏按钮的悬浮提示；无命中时返回 {@code null}（MEST {@code titleBarTooltip} 的等价实现）。 */
    public String titleBarTooltip(double mx, double my) {
        if (inFloatButton(mx, my)) {
            if (!windowFloating) {
                return I18n.format("gui.ae2enhanced.dock.float_window");
            }
            return windowGroup == 0
                    ? I18n.format("gui.ae2enhanced.dock.combine_window")
                    : I18n.format("gui.ae2enhanced.dock.combine_window.set", windowGroup);
        }
        if (inPinButton(mx, my)) {
            return I18n.format(pinned()
                    ? "gui.ae2enhanced.dock.unpin_panel"
                    : "gui.ae2enhanced.dock.pin_panel");
        }
        if (inCloseButton(mx, my)) {
            return I18n.format("gui.ae2enhanced.dock.content_edit.close");
        }
        if (inResetButton(mx, my)) {
            return I18n.format("gui.ae2enhanced.dock.content_edit.reset");
        }
        return null;
    }

    private static boolean inChromeButton(double mx, double my, int px, int py) {
        return mx >= px && mx < px + ChromeLayout.PIN_SIZE
                && my >= py && my < py + ChromeLayout.PIN_SIZE;
    }

    public boolean inTitleBar(double mx, double my) {
        if (!drawsTitleBar()) {
            return false;
        }
        return mx >= x && mx < x + width && my >= y && my < y + TITLE_BAR_HEIGHT
                && !inTitleBarControls(mx, my);
    }

    /**
     * Title-bar widgets (search field, etc.) that must not start a window drag.
     */
    public boolean inTitleBarControls(double mx, double my) {
        return inPinButton(mx, my) || inFloatButton(mx, my) || inCloseButton(mx, my) || inResetButton(mx, my);
    }

    public boolean inResizeHandle(double mx, double my) {
        return mx >= x + width - RESIZE_HANDLE && mx < x + width
                && my >= y + height - RESIZE_HANDLE && my < y + height;
    }

    /**
     * Width reserved on the right side of the title bar for controls supplied by a concrete panel.
     *
     * <p>The dock draws the resize affordance at floating-root level, so this inset only concerns
     * title-bar controls (for example the three ME sorting buttons). Subclasses can increase it when
     * they place controls over the title bar.
     */
    protected int titleRightInset() {
        if (contentChromeVisible()) {
            return 8 + 2 * ChromeLayout.PIN_SIZE + ChromeLayout.CHROME_BUTTON_GAP;
        }
        int count = chromeButtonCount();
        int closeReserve = closeVisible() ? ChromeLayout.PIN_SIZE + ChromeLayout.CHROME_BUTTON_GAP : 0;
        return 28 + closeReserve + count * ChromeLayout.PIN_SIZE
                + Math.max(0, count - 1) * ChromeLayout.CHROME_BUTTON_GAP;
    }

    protected int titleTextOffsetX() {
        return 0;
    }

    // --- Lifecycle hooks --------------------------------------------------

    /**
     * Lay out this panel's slots in absolute screen coordinates based on the current content area.
     * Called once on init and again whenever the panel moves or resizes. Subclasses position their
     * owned slots by writing {@code slot.xPos}/{@code slot.yPos} directly.
     */
    public abstract void layoutSlots();

    public void clampSize() {
        if (width < minWidth()) {
            width = minWidth();
        }
        if (height < minHeight()) {
            height = minHeight();
        }
    }

    // --- Render layers (drawn in this order by the dock) -------------------

    /**
     * Drawn before the panel frame so overlapping chrome (encoding tabs) tucks under the window.
     */
    public void renderUnderlay(int mouseX, int mouseY, float partialTicks, float zLevel) {
    }

    /**
     * 面板外框：投影 + 整窗 9-slice 背景 + 标题条。
     *
     * <p>托管（{@code hosted}）的叶子不画外框，因为它们共用宿主窗口的背景。</p>
     */
    public void renderFrame(int mouseX, int mouseY, float partialTicks, float zLevel) {
        if (hosted || !drawsTitleBar()) {
            return;
        }
        renderDropShadow(zLevel);
        NinePatch.draw(x, y, width, height, zLevel);
        renderTitleStrip(x, y, width, mouseX, mouseY, zLevel);
    }

    /**
     * Drop-shadow.
     *
     * <p>MEST 在面板右侧有越界 chrome 时跳过右边那笔；1.12.2 阶段不做越界 chrome，故两笔都画。</p>
     */
    protected void renderDropShadow(float zLevel) {
        NinePatch.drawDropShadow(x, y, width, height, false, zLevel);
    }

    /**
     * Inner title used when this leaf is painted as a section of a spliced shell window.
     */
    public void renderSectionHeader(int mouseX, int mouseY, float zLevel) {
        if (!drawsTitleBar()) {
            return;
        }
        renderTitleStrip(x, y, width, mouseX, mouseY, zLevel);
    }

    /** Render panel-owned content that must appear below slot item contents. */
    public void renderBackgroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
    }

    /** Render panel-owned content that must appear above slot item contents. */
    public void renderForegroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
    }

    /**
     * Render this panel's owned slot icons immediately after the panel's own frame + background
     * content, so that a top panel's frame paints over a lower panel's slot icons (z-order correct).
     * Slots moved off-screen (hidden panel / inactive encoding mode) are skipped.
     *
     * <p>The hover highlight is <em>not</em> drawn here: 1.12.2 由宿主 {@code GuiContainer} 的
     * {@code isHovering} 决定高亮，只有最上层面板光标下的槽位会亮，避免跨面板高亮串色。</p>
     *
     * <p>每个槽位的绘制委托给 {@code renderer}（宿主 GUI），这样可以直接复用 AE2-UEL 现有的
     * {@code RepoSlot}/{@code AppEngSlot} 渲染，不在此处重复实现。</p>
     */
    public void renderSlots(int mouseX, int mouseY, float partialTicks, PanelSlotRenderer renderer,
            float zLevel) {
        if (renderer == null) {
            return;
        }
        for (Slot slot : slotsToRender()) {
            if (slot.xPos <= -1000 || slot.yPos <= -1000) {
                continue;
            }
            renderer.drawPanelSlot(slot, mouseX, mouseY, partialTicks, zLevel);
        }
    }

    /**
     * 交给 dock 按 root 顺序绘制的槽位集合。
     *
     * <p>默认就是通过 {@code addSlotGroup}/{@code registerSlot} 登记的那些。{@code RepoPanel} 的 ME 槽
     * 是它自己在 {@code applySlots} 里动态创建并追加到 {@code inventorySlots} 尾部的（不在基类列表里），
     * 这类面板必须覆写本方法返回自己的槽位：压平通道是按 {@link #ownsSlot(Slot)} 判断"归 dock 管"的，
     * 两边一旦不一致（压平通道跳过、per-root 通道又不画）这些槽位就会彻底不渲染。</p>
     */
    protected List<? extends Slot> slotsToRender() {
        return ownedSlots;
    }

    /** 交给 dock 绘制的槽位数量（harness/测试用）。 */
    public int renderedSlotCount() {
        return slotsToRender().size();
    }

    /**
     * 标题条：裁剪后的标题 + 右侧 chrome 按钮。
     *
     * <p>{@code plainSubstrByWidth} → 1.12.2 {@code FontRenderer.trimStringToWidth}；
     * {@code drawString(..., false)} 的 {@code false} 表示不加阴影，两边语义一致。</p>
     *
     * <p><b>整条（含文字）必须画在本根自己的 z 上</b>：文字走的是 {@code FontRenderer.drawString}
     * 的"环境 z"（它不 translate），而本窗口背景已经画在同根的 {@code zLevel} 上。1.12.2 的 GUI
     * 是开深度测试的（{@code setupOverlayRendering} 只 clear 深度缓冲、不禁用它），且世界 z 越大
     * 深度值越小，于是"后画的文字（z 更小）"会被先画的本窗口背景挡掉。实机后果：多窗口布局里
     * <b>只有第一个窗口能看见标题</b>，其余窗口只剩按钮（按钮本来就带各自的 z）。</p>
     */
    private void renderTitleStrip(int left, int top, int barWidth, int mouseX, int mouseY, float zLevel) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (font == null) {
            return;
        }
        boolean pushed = NinePatch.pushZLevel(zLevel);
        try {
            int maxWidth = Math.max(0, barWidth - ChromeLayout.TITLE_LEFT_INSET - titleRightInset());
            String clippedTitle = font.trimStringToWidth(title(), maxWidth);
            font.drawString(clippedTitle, left + ChromeLayout.TITLE_LEFT_INSET + titleTextOffsetX(), top + 6,
                    ChromeLayout.COLOR_TITLE_TEXT, false);
            // 按钮的 z 传 0：环境矩阵已经平移过 zLevel，再传一次会叠成两倍。
            if (contentChromeVisible()) {
                renderChromeButton(resetButtonX(), resetButtonY(), inResetButton(mouseX, mouseY),
                        ChromeGlyph.RESET, 0.0F);
                renderChromeButton(closeButtonX(), closeButtonY(), inCloseButton(mouseX, mouseY),
                        ChromeGlyph.CLOSE, 0.0F);
            } else {
                if (closeVisible()) {
                    renderChromeButton(closeButtonX(), closeButtonY(), inCloseButton(mouseX, mouseY),
                            ChromeGlyph.CLOSE, 0.0F);
                }
                if (pinVisible()) {
                    renderChromeButton(pinButtonX(), pinButtonY(), inPinButton(mouseX, mouseY),
                            ChromeGlyph.PIN, 0.0F);
                }
                if (floatVisible()) {
                    if (windowFloating) {
                        renderCombineButton(floatButtonX(), pinButtonY(), inFloatButton(mouseX, mouseY), 0.0F);
                    } else {
                        renderChromeButton(floatButtonX(), pinButtonY(), inFloatButton(mouseX, mouseY),
                                ChromeGlyph.FLOAT, 0.0F);
                    }
                }
            }
        } finally {
            NinePatch.popZLevel(pushed);
        }
    }

    private void renderChromeButton(int px, int py, boolean hovered, ChromeGlyph glyph, float zLevel) {
        int yOffset = hovered ? 1 : 0;
        drawChromeBackground(px, py + yOffset, hovered, zLevel);
        renderChromeGlyph(px, py + yOffset, glyph, hovered, zLevel);
    }

    /**
     * 标题栏按钮底色。
     *
     * <p>MEST 装了 ExtendedAE 时用它的按钮贴图，否则退回与标题栏相称的深色填充；1.12.2 阶段只做后者
     * （数值与 MEST 的兜底分支一致）。</p>
     */
    private void drawChromeBackground(int px, int py, boolean hovered, float zLevel) {
        // V3：AE2 风格凸起小按钮（银灰底 + 深色描边 + hover 提亮）
        NinePatch.drawButtonBase(px, py, ChromeLayout.PIN_SIZE, ChromeLayout.PIN_SIZE, hovered, zLevel);
    }

    /** Unassigned: a faded module-1 icon. Assigned: the combined module's own 1/2/3 icon. */
    private void renderCombineButton(int px, int py, boolean hovered, float zLevel) {
        int yOffset = hovered ? 1 : 0;
        drawChromeBackground(px, py + yOffset, hovered, zLevel);
        renderCombineGlyph(px, py + yOffset, windowGroup, windowGroup == 0 ? 0.45F : 1.0F, zLevel);
    }

    /**
     * 标题栏按钮的图形。MEST 用 ExtendedAE/{@code MestGuiIcons}/AE2 {@code states.png} 的精灵，
     * 1.12.2 阶段留空，具体面板接入贴图时覆写。
     */
    protected void renderChromeGlyph(int px, int py, ChromeGlyph glyph, boolean hovered, float zLevel) {
        // V3：用图元画小图标（CLOSE=×，PIN=针，FLOAT=方框，RESET=回环）
        int color = ChromeLayout.CHROME_GLYPH_COLOR;
        boolean pushed = NinePatch.pushZLevel(zLevel);
        int cx = px + ChromeLayout.PIN_SIZE / 2;
        int cy = py + ChromeLayout.PIN_SIZE / 2;
        switch (glyph) {
            case CLOSE:
                for (int i = -3; i <= 2; i++) {
                    Gui.drawRect(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
                    Gui.drawRect(cx + i, cy - i - 1, cx + i + 1, cy - i, color);
                }
                break;
            case PIN:
                Gui.drawRect(cx - 2, cy - 3, cx + 2, cy - 1, color);
                Gui.drawRect(cx, cy - 1, cx + 1, cy + 3, color);
                break;
            case FLOAT:
                Gui.drawRect(cx - 3, cy - 3, cx + 3, cy - 2, color);
                Gui.drawRect(cx - 3, cy + 2, cx + 3, cy + 3, color);
                Gui.drawRect(cx - 3, cy - 3, cx - 2, cy + 3, color);
                Gui.drawRect(cx + 2, cy - 3, cx + 3, cy + 3, color);
                break;
            case RESET:
                Gui.drawRect(cx - 3, cy - 2, cx + 3, cy - 1, color);
                Gui.drawRect(cx - 3, cy - 1, cx - 2, cy + 2, color);
                Gui.drawRect(cx + 2, cy, cx + 3, cy + 2, color);
                Gui.drawRect(cx - 1, cy + 1, cx + 3, cy + 2, color);
                break;
            default:
                break;
        }
        NinePatch.popZLevel(pushed);
    }

    /**
     * 组合模块按钮的图形。{@code group} 为 0 表示未指派（应用半透明），1..3 表示已指派。
     */
    protected void renderCombineGlyph(int px, int py, int group, float opacity, float zLevel) {
    }

    /** Title-bar button glyphs, matching MEST's {@code ChromeGlyph} enum. */
    public enum ChromeGlyph {
        CLOSE,
        RESET,
        PIN,
        FLOAT
    }

    /**
     * Dashed centre guides used while nudging a content block inside a shell section.
     * Guides use the same inner content rectangle as {@link #contentSlackX()} / {@link #contentSlackY()},
     * otherwise the section midline sits in the reserved padding and never meets the content block.
     */
    public void renderContentGuides(boolean snapX, boolean snapY, float zLevel) {
        int innerLeft = x + CONTENT_PADDING;
        int innerTop = y + (drawsTitleBar() ? TITLE_BAR_HEIGHT : CONTENT_PADDING);
        int innerRight = innerLeft + contentWidth();
        int innerBottom = innerTop + contentHeight();
        int innerW = Math.max(0, innerRight - innerLeft);
        int innerH = Math.max(0, innerBottom - innerTop);
        int sectionMidX = innerLeft + innerW / 2;
        int sectionMidY = innerTop + innerH / 2;
        int contentW = Math.min(preferredContentWidth(), contentWidth());
        int contentH = Math.min(preferredContentHeight(), contentHeight());
        int contentMidX = contentLeft() + contentW / 2;
        int contentMidY = contentTop() + contentH / 2;
        boolean pushed = NinePatch.pushZLevel(zLevel);
        drawDashedVLine(sectionMidX, innerTop, innerBottom,
                snapX ? ChromeLayout.CONTENT_GUIDE_SNAP_COLOR : ChromeLayout.CONTENT_GUIDE_SECTION_COLOR);
        drawDashedHLine(sectionMidY, innerLeft, innerRight,
                snapY ? ChromeLayout.CONTENT_GUIDE_SNAP_COLOR : ChromeLayout.CONTENT_GUIDE_SECTION_COLOR);
        if (!snapX) {
            drawDashedVLine(contentMidX, innerTop, innerBottom, ChromeLayout.CONTENT_GUIDE_CONTENT_COLOR);
        }
        if (!snapY) {
            drawDashedHLine(contentMidY, innerLeft, innerRight, ChromeLayout.CONTENT_GUIDE_CONTENT_COLOR);
        }
        NinePatch.popZLevel(pushed);
    }

    private static void drawDashedHLine(int lineY, int x0, int x1, int color) {
        if (x1 <= x0) {
            return;
        }
        for (int lineX = x0; lineX < x1; lineX += 4) {
            Gui.drawRect(lineX, lineY, Math.min(lineX + 2, x1), lineY + 1, color);
        }
    }

    private static void drawDashedVLine(int lineX, int y0, int y1, int color) {
        if (y1 <= y0) {
            return;
        }
        for (int lineY = y0; lineY < y1; lineY += 4) {
            Gui.drawRect(lineX, lineY, lineX + 1, Math.min(lineY + 2, y1), color);
        }
    }

    // --- Input defaults (all "not consumed"; the dock owns gestures) --------

    /**
     * Handle a click on panel-owned widgets (the dock routes content-area clicks here, topmost leaf first).
     * Default: not consumed.
     */
    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    /**
     * Handle a scroll-wheel event routed to this panel (the topmost leaf whose <em>content area</em>
     * contains the cursor). Default: not consumed.
     */
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        return false;
    }

    /**
     * Begin a scrollbar drag (or page-jump). Returns true if consumed (the dock then owns the drag
     * until release). Default: no scrollbar.
     */
    public boolean scrollbarPressed(double mx, double my) {
        return false;
    }

    /** Continue a scrollbar drag started by {@link #scrollbarPressed}. Returns true if consumed. */
    public boolean scrollbarDragged(double mx, double my) {
        return false;
    }

    /** End any scrollbar drag. */
    public void scrollbarReleased() {
    }

    /** True while this panel's scrollbar thumb is being dragged. */
    public boolean scrollbarDragging() {
        return false;
    }

    /** True if this panel owns the given slot (identity check). */
    public boolean ownsSlot(Slot slot) {
        return ownedSlotSet.contains(slot);
    }

    // --- Shared chrome drawing (static, callable without a panel instance) --

    /**
     * One generated window background. Leaves then only draw section titles + content, and a single
     * background over the whole shell is what removes any possibility of a seam.
     */
    public static void drawGeneratedBackground(int x, int y, int width, int height, float zLevel) {
        NinePatch.draw(x, y, width, height, zLevel);
    }

    /**
     * One generated AE2 window for a spliced root: drop shadow + background.
     */
    public static void renderRootChrome(DockRect bounds, boolean skipRightShadow, float zLevel) {
        NinePatch.drawRootChrome(bounds, skipRightShadow, zLevel);
    }

    /**
     * Draws the 1px rules that separate sections inside an outer shell.
     *
     * <p>The shell mode covers the whole composite with one background, so these rules - not a
     * per-leaf bevel - are what convey section boundaries. A rounding error can only shift a rule
     * by a pixel; it can never expose a doubled border the way hidden-edge splicing can.
     */
    public static void drawSectionRules(List<LayoutProjection.DividerPlacement> dividers,
            String hoveredSplitNodeId, float zLevel) {
        NinePatch.drawSectionRules(dividers, hoveredSplitNodeId, zLevel);
    }

    /**
     * Draw the one resize affordance owned by a floating root.
     *
     * <p>Individual leaves still expose {@link #inResizeHandle(double, double)} for compatibility,
     * but a composite root must never draw one grip per leaf. The dock therefore invokes this helper
     * once with the root rectangle after all leaf frames have been rendered.
     */
    public static void renderResizeGrip(DockRect bounds, float zLevel) {
        if (bounds == null || bounds.width() < 2 || bounds.height() < 2) {
            return;
        }
        int right = bounds.right();
        int bottom = bounds.bottom();
        boolean pushed = NinePatch.pushZLevel(zLevel);
        for (int row = 0; row < 3; row++) {
            int dots = 3 - row;
            for (int col = 0; col < dots; col++) {
                int gripX = right - 3 - col * 3;
                int gripY = bottom - 3 - row * 3;
                Gui.drawRect(gripX, gripY, gripX + 2, gripY + 2, ChromeLayout.COLOR_DARK);
            }
        }
        NinePatch.popZLevel(pushed);
    }

    /**
     * Functional callback used by {@link #renderSlots} to delegate per-slot icon drawing back to the
     * hosting screen, which reuses AE2-UEL's existing {@code RepoSlot} / {@code AppEngSlot} rendering.
     */
    @FunctionalInterface
    public interface PanelSlotRenderer {
        void drawPanelSlot(Slot slot, int mouseX, int mouseY, float partialTicks, float zLevel);
    }
}
