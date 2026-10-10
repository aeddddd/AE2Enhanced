/*
 * Derived from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import java.io.IOException;

import appeng.core.AELog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import appeng.client.gui.widgets.MEGuiTextField;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ChromeLayout;
import com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch;
import com.github.aeddddd.ae2enhanced.client.gui.dock.DockManager;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleLayoutPolicy;
import com.github.aeddddd.ae2enhanced.util.dev.SplicedTermTestHarness;

/**
 * 可拼接终端布局编辑器（阶段 4b）——全屏界面，规格取自 MEST 的
 * {@code MESTLayoutEditorScreen}，按 1.12.2 的 {@link GuiScreen} 设施重写。
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────┐
 * │ 工具栏：撤销 默认 导入 导出 分享 | 预设1 预设2 预设3 | 命名 │ 完成 取消 │
 * ├────────┬────────────────────────────────────┬────────────┤
 * │ 模块   │                                    │ 属性检查器 │
 * │ 清单   │        画布（同一个 DockManager）   │ 可见       │
 * │ (7)    │  拖拽 / 吸附拼接 / 分隔条 / 缩放    │ 可移动     │
 * │        │                                    │ 可缩放     │
 * │        │                                    │ 浮动       │
 * └────────┴────────────────────────────────────┴────────────┘
 * </pre>
 *
 * <p>画布不是复制品：它渲染的就是终端那一个 {@link DockManager} 的投影，因此所有常规手势
 * （拖窗口、拖分隔条、右下握把缩放、Alt 拆分片、Ctrl 移动整窗）在这里原样可用。侧栏/工具栏/属性栏
 * 通过 {@code DockManager.setEditorCanvasInsets} 把窗口钳制在画布内，编辑期间不会滑到 chrome 底下。</p>
 *
 * <p>与 MEST 的差异（详见交付报告）：工具栏用 1.12.2 的 {@code buttonList} 而不是自绘 chrome；
 * 侧栏列出模块而不是"组合窗口分组"；属性只保留 4 项（MEST 的第 5 项"终端按钮"在本终端没有落点）；
 * 剪贴板导入导出由本屏负责（DockManager 只收发字符串）。</p>
 */
public class GuiSplicedLayoutEditor extends GuiScreen {

    // --- 按钮 id ---
    private static final int ID_UNDO = 1;
    private static final int ID_RESET = 2;
    private static final int ID_IMPORT = 3;
    private static final int ID_EXPORT = 4;
    private static final int ID_SHARE = 5;
    private static final int ID_DONE = 6;
    private static final int ID_CANCEL = 7;
    private static final int ID_RENAME = 8;
    private static final int ID_PRESET_BASE = 100;

    private static final int TOOLBAR_ROW_1 = 3;
    private static final int TOOLBAR_ROW_2 = 25;
    private static final int TOOLBAR_HEIGHT = 46;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_WIDTH = 40;
    private static final int BUTTON_GAP = 4;
    /** E3：预设页签宽度 / 页签间距（宽度 46 让 "Preset 1" 两侧都有余量，页签不再相碰）。 */
    private static final int PRESET_TAB_WIDTH = 46;
    private static final int PRESET_TAB_GAP = 4;

    private static final int SIDEBAR_WIDTH = 118;
    private static final int PROPERTY_WIDTH = 132;
    /** 侧栏模块行高。 */
    private static final int ROW_HEIGHT = 18;
    /** 折行文字的行高（8px 字高 + 2px 行距）。 */
    private static final int LINE_HEIGHT = 10;
    private static final int PROPERTY_COUNT = 4;
    /** 编辑器内自绘元素（命名框底）的 z 深度：在画布之上、按钮之下。 */
    private static final float NAME_FIELD_Z = 0.0F;

    // AE2 调色板（与终端 chrome 同一套：ChromeLayout 的 terminal palette）
    /**
     * E1：画布底色。
     *
     * <p>原来用 {@code C8CAD2}，与 19.x 窗口底 {@code CBCCD4}（{@link Ae2ModernArt#PLATE}）只差 3 级，
     * 画布上的窗口框几乎看不出来。现在改用 19.x 的 {@code LINE}（{@code 9A9FB4}）——比窗口底深一档，
     * 窗口的 {@code CBCCD4} 主体 + {@code 413F54} 描边在画布上立刻可辨。</p>
     */
    private static final int COLOR_CANVAS = 0xFF9A9FB4;
    /** 画布网格线（深底上改为浅色细线）。 */
    private static final int COLOR_GRID_MINOR = 0x1AFFFFFF;
    private static final int COLOR_GRID_MAJOR = 0x33FFFFFF;
    private static final int COLOR_SELECTION = 0x66ACE9FF;
    private static final int COLOR_ON = 0xFF6FBF73;
    private static final int COLOR_OFF = 0xFF9A5B5B;
    private static final int COLOR_TEXT = 0xFF413F54;
    private static final int COLOR_MUTED = 0xFF777B8C;
    /** 工具栏 / 侧栏底色（AE2 19.x 面板色）。 */
    private static final int COLOR_CHROME = Ae2ModernArt.PLATE;
    private static final int COLOR_CHROME_RULE = Ae2ModernArt.LINE;
    /** 侧栏行 hover 底（19.x 高光）。 */
    private static final int COLOR_ROW_HOVER = Ae2ModernArt.HIGHLIGHT;
    /** 侧栏行选中底（比面板略亮，配合左侧竖条 + 底部选中线）。 */
    private static final int COLOR_ROW_SELECTED = 0xFFE2E4EC;
    /** 侧栏行底部的中性分隔线。 */
    private static final int COLOR_ROW_RULE = 0x22FFFFFF;
    /** 编辑器内自绘元素（属性开关按钮）的 z 深度。 */
    private static final float EDITOR_WIDGET_Z = 0.0F;
    private static final int GRID_STEP = 16;
    private static final int GRID_MAJOR_EVERY = 128;

    private final GuiSplicedTerm parent;
    private final DockManager dock;
    private final List<ModulePanel> panels;

    private MEGuiTextField nameField;
    /** 命名框外框（AE2 输入框底的绘制矩形；MEGuiTextField 的字段是私有的，这里自己记）。 */
    private int nameFieldX;
    private int nameFieldY;
    private int nameFieldWidth;
    private int nameFieldHeight;
    private ModulePanel selected;
    private ModulePanel pendingPalette;
    private double pendingPaletteX;
    private double pendingPaletteY;
    private boolean paletteDragStarted;
    private String statusText;
    private boolean statusError;
    private long statusUntilMillis;

    public GuiSplicedLayoutEditor(GuiSplicedTerm parent, DockManager dock) {
        this.parent = parent;
        this.dock = dock;
        this.panels = new ArrayList<ModulePanel>(dock.panels());
        // 快照必须在打开子界面之前完成（父界面此时仍持有同一个 DockManager）
        dock.beginLayoutEditing();
    }

    // ==================== 生命周期 ====================

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        int y = TOOLBAR_ROW_1;
        int x = 6;
        addButton(ID_UNDO, x, y, BUTTON_WIDTH, I18n.format("gui.ae2enhanced.spliced_editor.undo"));
        x += BUTTON_WIDTH + BUTTON_GAP;
        addButton(ID_RESET, x, y, BUTTON_WIDTH, I18n.format("gui.ae2enhanced.spliced_editor.reset"));
        x += BUTTON_WIDTH + BUTTON_GAP;
        addButton(ID_IMPORT, x, y, BUTTON_WIDTH, I18n.format("gui.ae2enhanced.spliced_editor.import"));
        x += BUTTON_WIDTH + BUTTON_GAP;
        addButton(ID_EXPORT, x, y, BUTTON_WIDTH, I18n.format("gui.ae2enhanced.spliced_editor.export"));
        x += BUTTON_WIDTH + BUTTON_GAP;
        addButton(ID_SHARE, x, y, BUTTON_WIDTH, I18n.format("gui.ae2enhanced.spliced_editor.share"));

        addButton(ID_DONE, width - 6 - 84, y, 84, I18n.format("gui.ae2enhanced.spliced_editor.done"));
        addButton(ID_CANCEL, width - 6 - 84 - BUTTON_GAP - 84, y, 84,
                I18n.format("gui.ae2enhanced.spliced_editor.cancel"));

        // 第二行：预设页签 + 命名
        int presetX = 6;
        if (dock.hasPresets()) {
            for (int index = 0; index < dock.presetCount(); index++) {
                addButton(ID_PRESET_BASE + index, presetX, TOOLBAR_ROW_2, PRESET_TAB_WIDTH,
                        I18n.format("gui.ae2enhanced.spliced_editor.preset.tab", dock.presetName(index)));
                presetX += PRESET_TAB_WIDTH + PRESET_TAB_GAP;
            }
        }
        // E3：页签与命名框之间留出明显间隔（此前只有 6px，视觉上贴在一起）
        int nameX = presetX + 12;
        addButton(ID_RENAME, nameX + 96 + 4, TOOLBAR_ROW_2, 46,
                I18n.format("gui.ae2enhanced.spliced_editor.rename"));
        this.nameFieldX = nameX;
        this.nameFieldY = TOOLBAR_ROW_2 + 1;
        this.nameFieldWidth = 96;
        this.nameFieldHeight = 14;
        this.nameField = new MEGuiTextField(this.fontRenderer, this.nameFieldX, this.nameFieldY,
                this.nameFieldWidth, this.nameFieldHeight);
        this.nameField.setMaxStringLength(24);
        this.nameField.setTextColor(0xFFFFFF);
        this.nameField.setSelectionColor(-16744448);
        this.nameField.setEnableBackgroundDrawing(false);
        this.nameField.setTextColor(ChromeLayout.COLOR_TITLE_TEXT);
        this.nameField.setText(dock.presetName(dock.activePreset()));

        syncCanvasInsets();
        syncNameFieldVisibility();
    }

    private void addButton(int id, int x, int y, int buttonWidth, String label) {
        // E2/E3：AE2 19.x 按钮底（button.png 九宫格）+ 居中文字。W1 的教训是"浅底 + 浅色描边会整排
        // 不可见"，因此这里用官方按钮贴图（自带 1px 深描边）而不是自绘浅色边框。
        this.buttonList.add(new com.github.aeddddd.ae2enhanced.client.gui.widget.ModernGuiButton(
                id, x, y, buttonWidth, BUTTON_HEIGHT, label));
    }

    /** 让 dock 知道画布范围，并把视口同步成编辑器当前尺寸（窗口缩放时也走这里）。 */
    private void syncCanvasInsets() {
        dock.updateViewport(this.width, this.height);
        dock.setEditorCanvasInsets(SIDEBAR_WIDTH, TOOLBAR_HEIGHT, PROPERTY_WIDTH, 0);
    }

    private void syncNameFieldVisibility() {
        if (this.nameField != null && !dock.hasPresets()) {
            this.nameField.setVisible(false);
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** 完成：提交编辑并回到终端。 */
    private void finish() {
        dock.commitLayoutEditing();
        mc.displayGuiScreen(parent);
    }

    /** 取消：整体还原到进入编辑器的状态并回到终端。 */
    private void cancel() {
        dock.cancelLayoutEditing();
        // dev harness 排障：把"回滚本身"与"回到终端后的收敛"分开——A22-cancel 的 ids 探针要看这一步
        if (SplicedTermTestHarness.enabled()) {
            AE2Enhanced.LOGGER.info(SplicedTermTestHarness.TAG
                    + "NOTE editor.cancel() 回滚后 ids " + parent.harnessRootIds());
        }
        mc.displayGuiScreen(parent);
    }

    // ==================== 渲染 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        syncCanvasInsets();
        drawDefaultBackground();
        renderCanvasBackdrop();

        // 画布：与终端完全相同的 dock 渲染
        dock.render(mouseX, mouseY, partialTicks, 0.0F, null);
        dock.renderForeground(mouseX, mouseY, partialTicks, 0.0F);
        renderSelection(mouseX, mouseY);

        // 工具栏/侧栏/属性栏是"不透明底"的 chrome，必须先画：它们要压住画布溢出的窗口。
        // （W1b 回归根因：此前后画 chrome，而 GuiScreen 的按钮在 super.drawScreen 里绘制，
        //  于是整条工具栏 0..TOOLBAR_HEIGHT 被 COLOR_CHROME 覆盖，撤销/预设/完成/取消全部不可见，
        //  只剩最后绘制的命名框露出来。绘制顺序必须是 chrome → 按钮。）
        renderToolbarChrome();
        renderSidebar(mouseX, mouseY);
        renderProperties(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderStatus();

        if (nameField != null && nameField.getVisible()) {
            // E4：AE2 19.x 输入框底（聚焦态换条带）
            NinePatch.drawTextField(this.nameFieldX, this.nameFieldY, this.nameFieldWidth,
                    this.nameFieldHeight, nameField.isFocused(), NAME_FIELD_Z);
            nameField.drawTextBox();
        }
        renderTooltips(mouseX, mouseY);
    }

    private void renderCanvasBackdrop() {
        Gui.drawRect(SIDEBAR_WIDTH, TOOLBAR_HEIGHT, width - PROPERTY_WIDTH, height, COLOR_CANVAS);
        int right = width - PROPERTY_WIDTH;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        for (int gx = SIDEBAR_WIDTH; gx < right; gx += GRID_STEP) {
            int color = ((gx - SIDEBAR_WIDTH) % GRID_MAJOR_EVERY == 0) ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR;
            Gui.drawRect(gx, TOOLBAR_HEIGHT, gx + 1, height, color);
        }
        for (int gy = TOOLBAR_HEIGHT; gy < height; gy += GRID_STEP) {
            int color = ((gy - TOOLBAR_HEIGHT) % GRID_MAJOR_EVERY == 0) ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR;
            Gui.drawRect(SIDEBAR_WIDTH, gy, right, gy + 1, color);
        }
    }

    /** 选中窗口的描边（MEST 的 selection 提示）。 */
    private void renderSelection(int mouseX, int mouseY) {
        if (selected == null || !selected.visible) {
            return;
        }
        ModulePanel hovered = dock.topLeafAt(mouseX, mouseY);
        if (hovered != null && hovered == selected) {
            return;
        }
        Gui.drawRect(selected.x, selected.y, selected.x + selected.width, selected.y + 1, COLOR_SELECTION);
        Gui.drawRect(selected.x, selected.y + selected.height - 1, selected.x + selected.width,
                selected.y + selected.height, COLOR_SELECTION);
        Gui.drawRect(selected.x, selected.y, selected.x + 1, selected.y + selected.height, COLOR_SELECTION);
        Gui.drawRect(selected.x + selected.width - 1, selected.y, selected.x + selected.width,
                selected.y + selected.height, COLOR_SELECTION);
    }

    private void renderToolbarChrome() {
        Gui.drawRect(0, 0, width, TOOLBAR_HEIGHT, COLOR_CHROME);
        Gui.drawRect(0, TOOLBAR_HEIGHT - 1, width, TOOLBAR_HEIGHT, COLOR_CHROME_RULE);
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.spliced_editor.title"), 6,
                TOOLBAR_HEIGHT + 4, COLOR_MUTED, false);
    }

    private void renderSidebar(int mouseX, int mouseY) {
        Gui.drawRect(0, TOOLBAR_HEIGHT, SIDEBAR_WIDTH, height, COLOR_CHROME);
        Gui.drawRect(SIDEBAR_WIDTH - 1, TOOLBAR_HEIGHT, SIDEBAR_WIDTH, height, COLOR_CHROME_RULE);
        Gui.drawRect(SIDEBAR_WIDTH - 1, TOOLBAR_HEIGHT, SIDEBAR_WIDTH, height, 0xFF777B8C);
        int y = TOOLBAR_HEIGHT + 4;
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.spliced_editor.modules"), 6, y, COLOR_TEXT, false);
        y += 12;

        for (int index = 0; index < panels.size(); index++) {
            ModulePanel panel = panels.get(index);
            boolean hovered = mouseX >= 0 && mouseX < SIDEBAR_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean visible = dock.isModuleVisible(panel.id());
            boolean isSelected = panel == selected;
            // E5：三种状态各有一套明确底色——普通=面板色 / hover=19.x 高光 /
            // 选中=略亮底 + 左侧深色竖条（hover 与选中不再混在一起）。
            int rowBg = isSelected ? COLOR_ROW_SELECTED
                    : (hovered ? COLOR_ROW_HOVER : ChromeLayout.COLOR_PANEL);
            Gui.drawRect(2, y, SIDEBAR_WIDTH - 4, y + ROW_HEIGHT - 1, rowBg);
            Gui.drawRect(2, y + ROW_HEIGHT - 1, SIDEBAR_WIDTH - 4, y + ROW_HEIGHT, COLOR_ROW_RULE);
            if (isSelected) {
                Gui.drawRect(2, y, 4, y + ROW_HEIGHT - 1, ChromeLayout.COLOR_TITLE_TEXT);
            }
            Gui.drawRect(6, y + 3, 11, y + ROW_HEIGHT - 4, visible ? COLOR_ON : COLOR_OFF);
            String title = panel.title();
            int titleWidth = SIDEBAR_WIDTH - 4 - 14 - 2;
            fontRenderer.drawString(fontRenderer.trimStringToWidth(title, titleWidth), 14,
                    y + (ROW_HEIGHT - 8) / 2, visible ? COLOR_TEXT : COLOR_MUTED, false);
            y += ROW_HEIGHT;
        }

        y += 6;
        for (String hintKey : new String[] {
                "gui.ae2enhanced.spliced_editor.hint.palette",
                "gui.ae2enhanced.spliced_editor.hint.drag",
                "gui.ae2enhanced.spliced_editor.hint.splice",
                "gui.ae2enhanced.spliced_editor.hint.resize" }) {
            // E8：帮助文字按侧栏宽度折行，全部显示（不再用 "..." 裁掉尾部）
            y = drawWrappedText(I18n.format(hintKey), 6, y, SIDEBAR_WIDTH - 12, COLOR_MUTED);
            y += 2;
        }
    }

    /**
     * 按宽度折行绘制一段文字（E8）。
     *
     * <p>用 {@code FontRenderer.listFormattedStringToWidth} 做词级折行（它也会保留 {@code §} 颜色码），
     * 超高单词会再按字符硬切，因此任何语言都不会溢出侧栏，也不会被裁剪。</p>
     *
     * @return 下一行的 y
     */
    private int drawWrappedText(String text, int x, int y, int maxWidth, int color) {
        List<String> lines = fontRenderer.listFormattedStringToWidth(text, Math.max(1, maxWidth));
        if (lines.isEmpty()) {
            return y;
        }
        for (String line : lines) {
            fontRenderer.drawString(line, x, y, color, false);
            y += LINE_HEIGHT;
        }
        return y;
    }

    private void renderProperties(int mouseX, int mouseY) {
        int left = width - PROPERTY_WIDTH;
        // W2：Properties 面板与侧栏同一套浅色（原来是深色 0xFF22262F，与浅色画布割裂）
        Gui.drawRect(left, TOOLBAR_HEIGHT, width, height, COLOR_CHROME);
        Gui.drawRect(left, TOOLBAR_HEIGHT, left + 1, height, COLOR_CHROME_RULE);
        int y = TOOLBAR_HEIGHT + 4;
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.spliced_editor.properties"), left + 6, y,
                COLOR_TEXT, false);
        y += 14;
        if (selected == null) {
            fontRenderer.drawString(
                    fontRenderer.trimStringToWidth(
                            I18n.format("gui.ae2enhanced.spliced_editor.properties.none"),
                            PROPERTY_WIDTH - 10),
                    left + 6, y, COLOR_MUTED, false);
            return;
        }
        fontRenderer.drawString(fontRenderer.trimStringToWidth(selected.title(), PROPERTY_WIDTH - 10),
                left + 6, y, COLOR_MUTED, false);
        y += 12;

        for (int index = 0; index < PROPERTY_COUNT; index++) {
            boolean hovered = mouseX >= left + 2 && mouseX < width - 2 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean on = propertyEnabled(index);
            // E6：属性项做成"开关式小按钮"——19.x 按钮底 + 状态图标（VALID/INVALID）+ 右侧标签，
            // 而不是一行纯文字。点击语义不变（整行命中都算点击，见 propertyRowAt）。
            int buttonX = left + 4;
            int buttonWidth = PROPERTY_WIDTH - 10;
            Ae2ModernArt.drawButton(buttonX, y, buttonWidth, ROW_HEIGHT - 2, hovered, true, EDITOR_WIDGET_Z);
            Ae2ModernArt.drawIcon(on ? Ae2ModernArt.Icon.VALID : Ae2ModernArt.Icon.INVALID,
                    buttonX + 3, y + 1, EDITOR_WIDGET_Z);
            fontRenderer.drawString(
                    fontRenderer.trimStringToWidth(propertyLabel(index), PROPERTY_WIDTH - 34),
                    buttonX + 22, y + (ROW_HEIGHT - 2 - 8) / 2,
                    on ? COLOR_TEXT : COLOR_MUTED, false);
            y += ROW_HEIGHT;
        }
    }

    private void renderStatus() {
        if (statusText == null) {
            return;
        }
        if (System.currentTimeMillis() > statusUntilMillis) {
            statusText = null;
            return;
        }
        fontRenderer.drawString(statusText, SIDEBAR_WIDTH + 6, TOOLBAR_HEIGHT + 4,
                statusError ? 0xFFFF6B6B : 0xFF7CFF7C, false);
    }

    private void renderTooltips(int mouseX, int mouseY) {
        int index = sidebarRowAt(mouseX, mouseY);
        if (index >= 0) {
            drawHoveringText(Arrays.asList(
                    panels.get(index).title(),
                    I18n.format(dock.isModuleVisible(panels.get(index).id())
                            ? "gui.ae2enhanced.spliced_editor.module.hide"
                            : "gui.ae2enhanced.spliced_editor.module.show"),
                    I18n.format("gui.ae2enhanced.spliced_editor.module.drag")), mouseX, mouseY);
            return;
        }
        int property = propertyRowAt(mouseX, mouseY);
        if (property >= 0 && selected != null) {
            drawHoveringText(I18n.format(propertyTooltipKey(property)), mouseX, mouseY);
        }
    }

    // ==================== 输入 ====================

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (nameField != null && nameField.getVisible()) {
            nameField.mouseClicked(mouseX, mouseY, mouseButton);
            if (mouseButton == 1 && nameField.isMouseIn(mouseX, mouseY)) {
                nameField.setText("");
                return;
            }
        }
        if (mouseY < TOOLBAR_HEIGHT) {
            // 工具栏全是 GuiButton，交给 GuiScreen 分发（会走 actionPerformed）
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (mouseX < SIDEBAR_WIDTH) {
            int index = sidebarRowAt(mouseX, mouseY);
            if (index >= 0 && mouseButton == 0) {
                ModulePanel panel = panels.get(index);
                selected = panel;
                pendingPalette = panel;
                pendingPaletteX = mouseX;
                pendingPaletteY = mouseY;
                paletteDragStarted = false;
            }
            return;
        }
        if (mouseX >= width - PROPERTY_WIDTH) {
            int property = propertyRowAt(mouseX, mouseY);
            if (property >= 0 && mouseButton == 0) {
                toggleProperty(property);
            }
            return;
        }

        ModulePanel under = dock.topLeafAt(mouseX, mouseY);
        if (under != null) {
            selected = under;
        }
        dock.beginPanelInteraction(mouseX, mouseY);
        boolean consumed = dock.mouseClicked(mouseX, mouseY, mouseButton);
        if (!consumed) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
        dock.commitPanelInteraction();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (pendingPalette != null && !paletteDragStarted && clickedMouseButton == 0) {
            double dx = mouseX - pendingPaletteX;
            double dy = mouseY - pendingPaletteY;
            if (dx * dx + dy * dy >= 16.0D) {
                // 侧栏条目按拖拽起动：隐藏模块会被显形并拆成独立窗口，之后走常规吸附路径
                paletteDragStarted = dock.startExternalDrag(pendingPalette, pendingPaletteX, pendingPaletteY);
            }
        }
        if (!dock.mouseDragged(mouseX, mouseY)) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (pendingPalette != null) {
            ModulePanel panel = pendingPalette;
            boolean dragged = paletteDragStarted;
            pendingPalette = null;
            paletteDragStarted = false;
            if (!dragged) {
                // 只是点了一下：切换显隐
                boolean next = !dock.isModuleVisible(panel.id());
                if (!dock.setModuleVisible(panel.id(), next) && !next) {
                    showStatus(I18n.format("gui.ae2enhanced.spliced_editor.status.cannot_hide"), true);
                } else {
                    selected = panel;
                }
                return;
            }
        }
        dock.mouseReleased(mouseX, mouseY, state);
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int delta = Mouse.getEventDWheel();
        if (delta != 0) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (mouseX >= SIDEBAR_WIDTH && mouseX < width - PROPERTY_WIDTH && mouseY >= TOOLBAR_HEIGHT
                    && dock.mouseScrolled(mouseX, mouseY, delta / 120.0D)) {
                return;
            }
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (dock.keyPressed(keyCode)) {
            return;
        }
        if (nameField != null && nameField.getVisible() && nameField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                nameField.setFocused(false);
                applyTypedName();
                return;
            }
            if (nameField.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancel();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            finish();
            return;
        }
        if (keyCode == Keyboard.KEY_Z && isCtrlKeyDown()) {
            if (dock.canUndoLayout()) {
                dock.undoLayout();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_UNDO) {
            if (dock.canUndoLayout()) {
                dock.undoLayout();
            } else {
                showStatus(I18n.format("gui.ae2enhanced.spliced_editor.status.nothing_to_undo"), true);
            }
            return;
        }
        if (button.id == ID_RESET) {
            dock.resetLayout();
            showStatus(I18n.format("gui.ae2enhanced.spliced_editor.status.reset"), false);
            refreshPresetButtons();
            return;
        }
        if (button.id == ID_IMPORT) {
            String clipboard = GuiScreen.getClipboardString();
            DockManager.LayoutShareResult result = dock.importLayoutJson(clipboard);
            showStatus(I18n.format(result.langKey()), !result.ok());
            refreshPresetButtons();
            return;
        }
        if (button.id == ID_EXPORT) {
            String json = dock.exportLayoutJson();
            if (json == null) {
                showStatus(I18n.format("gui.ae2enhanced.spliced_editor.export.fail"), true);
                return;
            }
            GuiScreen.setClipboardString(json);
            showStatus(I18n.format("gui.ae2enhanced.spliced_editor.export.copied"), false);
            return;
        }
        if (button.id == ID_SHARE) {
            DockManager.LayoutShareResult result = dock.exportToShareFolder();
            Path path = result.path();
            showStatus(I18n.format(result.langKey()) + (path == null ? "" : " " + path), !result.ok());
            return;
        }
        if (button.id == ID_DONE) {
            finish();
            return;
        }
        if (button.id == ID_CANCEL) {
            cancel();
            return;
        }
        if (button.id == ID_RENAME) {
            applyTypedName();
            return;
        }
        int preset = button.id - ID_PRESET_BASE;
        if (preset >= 0 && preset < dock.presetCount()) {
            // 切槽前先把名字应用掉，避免刚输入的名字被覆盖
            applyTypedName();
            dock.selectPreset(preset);
            refreshPresetButtons();
            return;
        }
        super.actionPerformed(button);
    }

    private void applyTypedName() {
        if (nameField == null || !dock.hasPresets()) {
            return;
        }
        dock.setPresetName(dock.activePreset(), nameField.getText());
        refreshPresetButtons();
    }

    /** 预设页签的标签要跟着名字走，按钮文本只能重建（1.12.2 的 GuiButton.displayString）。 */
    private void refreshPresetButtons() {
        for (GuiButton button : this.buttonList) {
            int preset = button.id - ID_PRESET_BASE;
            if (preset >= 0 && preset < dock.presetCount()) {
                button.displayString = I18n.format("gui.ae2enhanced.spliced_editor.preset.tab", dock.presetName(preset));
                if (button instanceof com.github.aeddddd.ae2enhanced.client.gui.widget.ModernGuiButton) {
                    ((com.github.aeddddd.ae2enhanced.client.gui.widget.ModernGuiButton) button)
                            .setSelected(preset == dock.activePreset());
                }
            }
        }
        if (nameField != null && nameField.getVisible()) {
            nameField.setText(dock.presetName(dock.activePreset()));
        }
    }

    // ==================== 属性检查器 ====================

    private int propertyRowAt(int mouseX, int mouseY) {
        if (selected == null || mouseX < width - PROPERTY_WIDTH || mouseY < TOOLBAR_HEIGHT) {
            return -1;
        }
        int y = TOOLBAR_HEIGHT + 4 + 14 + 12;
        for (int index = 0; index < PROPERTY_COUNT; index++) {
            if (mouseY >= y && mouseY < y + ROW_HEIGHT) {
                return index;
            }
            y += ROW_HEIGHT;
        }
        return -1;
    }

    private int sidebarRowAt(int mouseX, int mouseY) {
        if (mouseX < 0 || mouseX >= SIDEBAR_WIDTH || mouseY < TOOLBAR_HEIGHT) {
            return -1;
        }
        int y = TOOLBAR_HEIGHT + 4 + 12;
        for (int index = 0; index < panels.size(); index++) {
            if (mouseY >= y && mouseY < y + ROW_HEIGHT) {
                return index;
            }
            y += ROW_HEIGHT;
        }
        return -1;
    }

    private boolean propertyEnabled(int index) {
        if (selected == null) {
            return false;
        }
        ModuleLayoutPolicy policy = dock.policyFor(selected);
        switch (index) {
            case 0:
                return policy.visible();
            case 1:
                return policy.movable();
            case 2:
                return policy.resizable();
            case 3:
                return dock.isFloatingWindow(selected);
            default:
                return false;
        }
    }

    private static String propertyLabel(int index) {
        switch (index) {
            case 0:
                return I18n.format("gui.ae2enhanced.spliced_editor.property.visible");
            case 1:
                return I18n.format("gui.ae2enhanced.spliced_editor.property.movable");
            case 2:
                return I18n.format("gui.ae2enhanced.spliced_editor.property.resizable");
            case 3:
                return I18n.format("gui.ae2enhanced.spliced_editor.property.floating");
            default:
                return "";
        }
    }

    private static String propertyTooltipKey(int index) {
        switch (index) {
            case 0:
                return "gui.ae2enhanced.spliced_editor.property.visible.tip";
            case 1:
                return "gui.ae2enhanced.spliced_editor.property.movable.tip";
            case 2:
                return "gui.ae2enhanced.spliced_editor.property.resizable.tip";
            default:
                return "gui.ae2enhanced.spliced_editor.property.floating.tip";
        }
    }

    private void toggleProperty(int index) {
        if (selected == null) {
            return;
        }
        boolean next = !propertyEnabled(index);
        if (index == 3) {
            // 浮动是"窗口"属性：改的是窗口本身，不是这个模块
            if (!dock.canToggleFloating(selected) && next) {
                showStatus(I18n.format("gui.ae2enhanced.spliced_editor.status.cannot_float"), true);
                return;
            }
            dock.setWindowFloating(selected, next);
            return;
        }
        ModuleLayoutPolicy policy = dock.policyFor(selected);
        ModuleLayoutPolicy updated;
        if (index == 0) {
            if (!next && !dock.setModuleVisible(selected.id(), false)) {
                showStatus(I18n.format("gui.ae2enhanced.spliced_editor.status.cannot_hide"), true);
                return;
            }
            if (next) {
                dock.setModuleVisible(selected.id(), true);
            }
            return;
        }
        if (index == 1) {
            updated = new ModuleLayoutPolicy(policy.visible(), next, policy.resizable(),
                    policy.floating(), policy.pinned(), policy.showTerminalButton());
        } else {
            updated = new ModuleLayoutPolicy(policy.visible(), policy.movable(), next,
                    policy.floating(), policy.pinned(), policy.showTerminalButton());
        }
        dock.setModulePolicy(selected, updated);
    }

    private void showStatus(String message, boolean error) {
        this.statusText = message;
        this.statusError = error;
        this.statusUntilMillis = System.currentTimeMillis() + 4000L;
    }

    // ==================== dev harness 专用入口 ====================
    // 让验收脚本能用"和真实点击同一入口"的方式驱动编辑器按钮（actionPerformed 是 protected）。

    /** dev harness 专用：触发指定 id 的工具栏按钮（等同于点它）。 */
    public void harnessAction(int buttonId) {
        for (GuiButton button : this.buttonList) {
            if (button.id == buttonId) {
                try {
                    actionPerformed(button);
                } catch (IOException e) {
                    AELog.debug(e);
                }
                return;
            }
        }
    }

    /** dev harness 专用：父终端 GUI（编辑器打开期间 harness 用它做布局断言）。 */
    public GuiSplicedTerm harnessParentTerminal() {
        return this.parent;
    }

    /** dev harness 专用：撤销按钮 id。 */
    public int harnessIdUndo() {
        return ID_UNDO;
    }

    /** dev harness 专用：重置按钮 id。 */
    public int harnessIdReset() {
        return ID_RESET;
    }

    /** dev harness 专用：完成（保存）按钮 id。 */
    public int harnessIdDone() {
        return ID_DONE;
    }

    /** dev harness 专用：取消（回滚）按钮 id。 */
    public int harnessIdCancel() {
        return ID_CANCEL;
    }

    /** dev harness 专用：预设页签的起始 id（第 i 个预设 = 该值 + i）。 */
    public int harnessIdPresetBase() {
        return ID_PRESET_BASE;
    }

    /** 选中窗口的屏幕矩形（供调试/测试观察）。 */
    public DockRect selectedBounds() {
        return selected == null ? null : new DockRect(selected.x, selected.y, selected.width, selected.height);
    }
}
