package com.github.aeddddd.ae2enhanced.client.gui.panel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;

import com.github.aeddddd.ae2enhanced.client.gui.GuiOmniAmount;
import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;
import com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotPatternOutputs;
import com.github.aeddddd.ae2enhanced.client.gui.util.GuiResourceCache;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.container.SplicedTermSlotPolicy;

/**
 * 样板编码模块：承接 {@code ContainerOmniTerm.setupPatternArea} 的编码区槽位——
 * 81 个 {@code RCSlotFakeCraftingMatrix} 输入（9 组 × 9）、27 个 {@code RCSlotPatternOutputs}
 * 输出（9 组 × 3）、1 个 craft 模式产出槽（{@code SlotPatternTerm}），以及 2 个样板 in/out 槽。
 *
 * <h3>槽位登记策略（模式 × 滚动页）</h3>
 * <p>编码区一次只显示一组：craft 模式显示第 0 组的 3×3 + 产出槽，process 模式显示第
 * {@code scrollOffset} 组的 3×3 + 3 个输出槽。因此 {@link #layoutSlots()} 会：</p>
 * <ol>
 *   <li>把<strong>所有</strong>非当前组的槽位 {@code hideSlot} 且 {@code visible = false}；</li>
 *   <li>把当前组摆到面板内的绝对坐标；</li>
 *   <li>只把"当前可见的这批槽位"登记进 {@code MODULE_PATTERN}（
 *       {@link SplicedTermSlotPolicy#setModuleSlots}）。</li>
 * </ol>
 * <p>第 3 步是关键：登记集合必须严格等于实际可见的槽位，否则屏幕外的 72 个输入槽会被判成
 * "可被 shift-click 填充"，物品就进了看不见的地方（阶段 2a 明确禁止的那类 bug）。</p>
 *
 * <h3>与容器状态的配合</h3>
 * <p>{@code ContainerOmniTerm.setRCSlot}/{@code applyPatternCraftMode} 会在模式切换或服务端
 * Scroll 动作时把槽位坐标写回旧 GUI 的绝对值。面板因此在每帧 {@link #tick()} 里比对
 * "模式 + 页号"，一旦被外部改动就重新摆位（比拦截容器调用可靠得多）。</p>
 *
 * <h3>shift-click 语义</h3>
 * <p>编码区主体（SlotFake 家族 + {@code SlotPatternTerm}）声明为 {@code NONE}：AE2 客户端
 * {@code AEBaseGui.func_184098_a} 对 {@code SlotFake} 与 {@code SlotPatternTerm} 有专门分支，
 * 它们的点击不会被送进 vanilla 槽位转移，因此 {@code NONE} 与"不可达"等价，同时挡住
 * craft 产出槽那条意外的传递路径。样板 in/out（{@code SlotRestrictedInput}）是 {@code BOTH}，
 * 与旧终端一致（空白样板可以 shift-click 进去，两者都可以 shift-click 回背包）。</p>
 */
public final class PatternEncodingPanel extends SlotHostPanel {
    public static final String MODULE_ID = SplicedTermSlotPolicy.MODULE_PATTERN;

    // ---- 编码块（pattern_modes.png 两块模式图各 124×66）----
    public static final int BLOCK_WIDTH = 124;
    public static final int BLOCK_HEIGHT = 66;
    /** 滚动条（旧 GUI 摆在块左缘的 12px 凹槽里；craft 模式不显示）。 */
    private static final int SCROLLBAR_X = 0;
    private static final int SCROLLBAR_Y = 2;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HEIGHT = 62;
    /** craft 模式 3×3 输入的块内偏移（旧 GUI 187-180 / 93-86）。 */
    private static final int CRAFT_INPUT_X = 7;
    /** process 模式 3×3 输入的块内偏移（旧 GUI 196-180）。 */
    private static final int PROCESS_INPUT_X = 16;
    private static final int INPUT_Y = 7;
    /** 输出列 / 产出槽的块内偏移（旧 GUI 281-180）。 */
    private static final int OUTPUT_X = 101;
    private static final int OUTPUT_Y = 7;
    private static final int CRAFT_OUTPUT_Y = 25;
    /** 样板 in/out 槽在块右侧的偏移（旧 GUI 319-180，y 0 / 47）。 */
    private static final int FEED_X = 139;
    private static final int FEED_IN_Y = 0;
    private static final int FEED_OUT_Y = 47;

    // ---- 面板内控件行 ----
    /** 倍率按钮行（16×16 按钮，间距 18，与动作行同规格）。 */
    private static final int MULTIPLIER_ROW_Y = 68;
    public static final int MULTIPLIER_COUNT = 6;
    private static final int MULTIPLIER_PITCH = 18;
    /** 模式 tab + 动作按钮行（tab 20×22，其余 16×16）。 */
    private static final int ACTION_ROW_Y = 86;
    private static final int ACTION_BASE_X = 24;
    private static final int ACTION_PITCH = 18;
    public static final int ACTION_BUTTON_COUNT = 3;

    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLUMNS = 3;
    private static final int GRID_ROWS = 3;
    private static final int GROUP_COUNT = 9;

    /** 内容区尺寸：块宽 + 右侧 feed 列；高 = 块高 + 两行按钮。 */
    public static final int CONTENT_WIDTH = FEED_X + SLOT_SIZE;
    public static final int CONTENT_HEIGHT = ACTION_ROW_Y + 22;

    private final GuiScrollbar scrollBar;
    /** 只在绘制滚动条时用到（{@code GuiScrollbar.draw} 需要宿主参数）。 */
    private final AEBaseGui host;

    private final List<Slot> feedSlots = new ArrayList<Slot>();
    /** 当前登记的可见编码槽（每次 layoutSlots 重建）。 */
    private final List<Slot> visiblePatternSlots = new ArrayList<Slot>();

    private boolean lastCraftMode = true;
    private int lastGroup = -1;
    private long layoutVersion;
    private boolean draggingScrollBar;
    private IntConsumer scrollListener;

    public PatternEncodingPanel(ContainerSplicedTerm container, GuiScrollbar scrollBar, AEBaseGui host) {
        super(container);
        this.scrollBar = scrollBar;
        this.host = host;
        if (container.getPatternSlotIn() != null) {
            feedSlots.add(container.getPatternSlotIn());
        }
        if (container.getPatternSlotOut() != null) {
            feedSlots.add(container.getPatternSlotOut());
        }
        // 编码槽（含 in/out）不通过 addSlotGroup 登记：登记集合是"按模式 × 页"动态计算的，
        // 见 layoutSlots()；这里只登记归属（dock 的命中/遮挡判定依赖 ownsSlot）。
        for (Slot slot : feedSlots) {
            registerSlot(slot);
        }
        for (int group = 0; group < container.getPatternGroupCount(); group++) {
            for (RCSlotFakeCraftingMatrix slot : container.getPatternInputGroup(group)) {
                registerSlot(slot);
            }
            for (RCSlotPatternOutputs slot : container.getPatternOutputGroup(group)) {
                registerSlot(slot);
            }
        }
        if (container.getPatternCraftSlot() != null) {
            registerSlot(container.getPatternCraftSlot());
        }
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String title() {
        return I18n.format("gui.ae2enhanced.spliced_term.pattern");
    }

    @Override
    public int minWidth() {
        return panelWidthForContent();
    }

    @Override
    public int defaultWidth() {
        return minWidth();
    }

    @Override
    public int minHeight() {
        return panelHeightForContent();
    }

    @Override
    public int defaultHeight() {
        return minHeight();
    }

    public static int panelWidthForContent() {
        return CONTENT_WIDTH + 2 * CONTENT_PADDING;
    }

    public static int panelHeightForContent() {
        return TITLE_BAR_HEIGHT + CONTENT_HEIGHT + CONTENT_PADDING;
    }

    // ==================== 控件几何（宿主据此摆放 GuiImgButton / GuiTabButton） ====================

    /** 模式 tab（craft/processing，两者只有其一可见）的位置。 */
    public int modeTabX() {
        return contentLeft();
    }

    public int modeTabY() {
        return contentTop() + ACTION_ROW_Y;
    }

    /** 第 {@code index} 个倍率按钮的位置（0=×2 1=×3 2=÷2 3=÷3 4=+1 5=-1，顺序与旧 GUI 一致）。 */
    public int multiplierX(int index) {
        return contentLeft() + index * MULTIPLIER_PITCH;
    }

    public int multiplierY() {
        return contentTop() + MULTIPLIER_ROW_Y;
    }

    /** 第 {@code index} 个动作按钮的位置（0=Substitute 1=ClearPattern 2=Encode）。 */
    public int actionX(int index) {
        return contentLeft() + ACTION_BASE_X + index * ACTION_PITCH;
    }

    public int actionY() {
        return contentTop() + ACTION_ROW_Y;
    }

    // ==================== 输入 ====================

    public void setScrollListener(IntConsumer listener) {
        this.scrollListener = listener;
    }

    public long layoutVersion() {
        return layoutVersion;
    }

    /** 供宿主每帧调用：模式或页号被容器改过就重新摆位。 */
    public void tick() {
        if (container.isPatternCraftMode() != lastCraftMode
                || currentGroup() != lastGroup) {
            layoutSlots();
        }
    }

    private int currentGroup() {
        if (container.isPatternCraftMode()) {
            return 0;
        }
        return Math.max(0, Math.min(container.getScrollOffset(), GROUP_COUNT - 1));
    }

    // ==================== 布局 ====================

    @Override
    public void layoutSlots() {
        this.layoutVersion++;
        this.lastCraftMode = container.isPatternCraftMode();
        this.lastGroup = currentGroup();
        boolean craft = lastCraftMode;
        int active = lastGroup;

        if (!visible) {
            hideAllPatternSlots();
            container.slotPolicy().setModuleSlots(MODULE_ID, Collections.<Slot>emptyList());
            container.slotPolicy().setModuleSlots(SplicedTermSlotPolicy.MODULE_PATTERN_FEED,
                    Collections.<Slot>emptyList());
            return;
        }
        // 让容器状态（visible / scrollOffset 钳制）与面板一致；坐标随后由面板覆盖
        container.setRCSlot(active);

        visiblePatternSlots.clear();
        for (int group = 0; group < GROUP_COUNT; group++) {
            boolean groupVisible = (group == active);
            int inputX = craft ? CRAFT_INPUT_X : PROCESS_INPUT_X;
            RCSlotFakeCraftingMatrix[] inputs = container.getPatternInputGroup(group);
            for (int i = 0; i < inputs.length; i++) {
                RCSlotFakeCraftingMatrix slot = inputs[i];
                slot.visible = groupVisible;
                if (groupVisible) {
                    placeSlot(slot,
                            contentLeft() + inputX + (i % GRID_COLUMNS) * SLOT_SIZE + 1,
                            contentTop() + INPUT_Y + (i / GRID_COLUMNS) * SLOT_SIZE + 1);
                    visiblePatternSlots.add(slot);
                } else {
                    hideSlot(slot);
                }
            }
            RCSlotPatternOutputs[] outputs = container.getPatternOutputGroup(group);
            for (int i = 0; i < outputs.length; i++) {
                RCSlotPatternOutputs slot = outputs[i];
                // 输出槽只在 process 模式可见（craft 模式的产出走 craftSlot）
                slot.visible = groupVisible && !craft;
                if (groupVisible && !craft) {
                    placeSlot(slot,
                            contentLeft() + OUTPUT_X + 1,
                            contentTop() + OUTPUT_Y + i * SLOT_SIZE + 1);
                    visiblePatternSlots.add(slot);
                } else {
                    hideSlot(slot);
                }
            }
        }

        Slot craftSlot = container.getPatternCraftSlot();
        if (craftSlot != null) {
            if (craft) {
                placeSlot(craftSlot, contentLeft() + OUTPUT_X + 1, contentTop() + CRAFT_OUTPUT_Y + 1);
                visiblePatternSlots.add(craftSlot);
            } else {
                hideSlot(craftSlot);
            }
        }

        for (int i = 0; i < feedSlots.size(); i++) {
            placeSlot(feedSlots.get(i),
                    contentLeft() + FEED_X + 1,
                    contentTop() + (i == 0 ? FEED_IN_Y : FEED_OUT_Y) + 1);
        }

        container.slotPolicy().setModuleSlots(MODULE_ID, visiblePatternSlots);
        container.slotPolicy().setModuleSlots(SplicedTermSlotPolicy.MODULE_PATTERN_FEED, feedSlots);
        applyScrollBarGeometry();
    }

    private void hideAllPatternSlots() {
        for (int group = 0; group < GROUP_COUNT; group++) {
            for (RCSlotFakeCraftingMatrix slot : container.getPatternInputGroup(group)) {
                slot.visible = false;
                hideSlot(slot);
            }
            for (RCSlotPatternOutputs slot : container.getPatternOutputGroup(group)) {
                slot.visible = false;
                hideSlot(slot);
            }
        }
        Slot craftSlot = container.getPatternCraftSlot();
        if (craftSlot != null) {
            hideSlot(craftSlot);
        }
        for (Slot slot : feedSlots) {
            hideSlot(slot);
        }
        visiblePatternSlots.clear();
    }

    private void applyScrollBarGeometry() {
        if (scrollBar == null) {
            return;
        }
        scrollBar.setLeft(contentLeft() + SCROLLBAR_X)
                .setTop(contentTop() + SCROLLBAR_Y)
                .setWidth(SCROLLBAR_WIDTH)
                .setHeight(SCROLLBAR_HEIGHT);
        scrollBar.setRange(0, Math.max(0, container.getMaxScrollOffset()), 1);
    }

    // ==================== 渲染 ====================

    @Override
    public void renderBackgroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
        if (!visible) {
            return;
        }
        boolean pushed = NinePatch.pushZLevel(zLevel);
        // 编码块：AE2 19.x pattern_modes.png 的 craft(0,0) / process(0,70) 两块之一
        NinePatch.drawPatternModeBlock(contentLeft(), contentTop(), container.isPatternCraftMode(), zLevel);
        // 样板 in/out 两格（块外右侧）
        PlayerInvPanel.drawSlotBand(contentLeft() + FEED_X, contentTop() + FEED_IN_Y, 1, 1, zLevel);
        PlayerInvPanel.drawSlotBand(contentLeft() + FEED_X, contentTop() + FEED_OUT_Y, 1, 1, zLevel);
        // S16：空槽的画 19.x 槽位图案——in 槽收空白样板、out 槽出已编码样板
        // （对应 19.x `PlacableItemType`：BLANK_PATTERN→BACKGROUND_BLANK_PATTERN、
        //   ENCODED_PATTERN→BACKGROUND_ENCODED_PATTERN，有物品时被物品盖住）
        if (feedSlotEmpty(0)) {
            NinePatch.drawSlotPattern(Ae2ModernArt.Icon.BACKGROUND_BLANK_PATTERN,
                    contentLeft() + FEED_X, contentTop() + FEED_IN_Y, zLevel);
        }
        if (feedSlotEmpty(1)) {
            NinePatch.drawSlotPattern(Ae2ModernArt.Icon.BACKGROUND_ENCODED_PATTERN,
                    contentLeft() + FEED_X, contentTop() + FEED_OUT_Y, zLevel);
        }
        // 自绘滚动条（craft 模式范围为空，AE2 的 GuiScrollbar 会画成禁用态）
        if (scrollBar != null && !container.isPatternCraftMode()) {
            NinePatch.drawScrollbar(contentLeft() + SCROLLBAR_X, contentTop() + SCROLLBAR_Y,
                    SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT, scrollBar.getCurrentScroll(),
                    Math.max(0, container.getMaxScrollOffset()), true, zLevel);
        }
        NinePatch.popZLevel(pushed);
    }

    /** 样板 in/out 槽是否为空（越界视为空，面板可以先于容器建好）。 */
    private boolean feedSlotEmpty(int index) {
        return index >= feedSlots.size() || feedSlots.get(index).getStack().isEmpty();
    }

    // ==================== 滚动条输入（面板自持，经 DockManager 内容路由进来） ====================

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!visible || scrollBar == null || scrollY == 0 || container.isPatternCraftMode()) {
            return false;
        }
        scrollBar.wheel(scrollY > 0 ? 1 : -1);
        applyScrollOffset();
        return true;
    }

    @Override
    public boolean scrollbarPressed(double mx, double my) {
        if (!visible || scrollBar == null || container.isPatternCraftMode() || !inScrollBar(mx, my)) {
            return false;
        }
        this.draggingScrollBar = true;
        scrollBar.click(host, (int) mx, (int) my);
        applyScrollOffset();
        return true;
    }

    @Override
    public boolean scrollbarDragged(double mx, double my) {
        if (!draggingScrollBar || scrollBar == null) {
            return false;
        }
        scrollBar.click(host, (int) mx, (int) my);
        applyScrollOffset();
        return true;
    }

    @Override
    public void scrollbarReleased() {
        this.draggingScrollBar = false;
    }

    @Override
    public boolean scrollbarDragging() {
        return this.draggingScrollBar;
    }

    /**
     * 中键点编码槽 → 打开数量弹窗（与旧终端 {@code GuiOmniTerm:585-589} 同款调用）。
     *
     * <p>非左键的内容路由由 {@code DockManager.mouseClicked} 交给本方法，因此这里能同时处理
     * 右键（AE2 自己的 SlotFake 分支）之前的中间键语义。</p>
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 2 || !visible) {
            return false;
        }
        Slot slot = visibleSlotAt(mx, my);
        if (!(slot instanceof RCSlotFakeCraftingMatrix) && !(slot instanceof RCSlotPatternOutputs)) {
            return false;
        }
        ItemStack stack = slot.getStack();
        int amount = stack.isEmpty() ? 1 : stack.getCount();
        int invType = slot instanceof RCSlotFakeCraftingMatrix ? 0 : 1;
        Minecraft.getMinecraft().displayGuiScreen(
                new GuiOmniAmount((GuiScreen) host, invType, slot.getSlotIndex(), amount));
        return true;
    }

    /** 光标下的当前可见编码槽（只在登记过的可见槽位里找，屏幕外的槽位不响应）。 */
    private Slot visibleSlotAt(double mx, double my) {
        for (Slot slot : visiblePatternSlots) {
            if (mx >= slot.xPos && mx < slot.xPos + 16 && my >= slot.yPos && my < slot.yPos + 16) {
                return slot;
            }
        }
        return null;
    }

    private boolean inScrollBar(double mx, double my) {
        int left = scrollBar.getLeft();
        int top = scrollBar.getTop();
        return mx >= left && mx < left + scrollBar.getWidth()
                && my >= top && my < top + scrollBar.getHeight();
    }

    /** 页号变化 → 容器本地状态 + 通知宿主（宿主负责发 Scroll 动作包给服务端）。 */
    private void applyScrollOffset() {
        int offset = scrollBar.getCurrentScroll();
        if (offset == container.getScrollOffset()) {
            return;
        }
        container.setRCSlot(offset);
        layoutSlots();
        if (scrollListener != null) {
            scrollListener.accept(offset);
        }
    }
}
