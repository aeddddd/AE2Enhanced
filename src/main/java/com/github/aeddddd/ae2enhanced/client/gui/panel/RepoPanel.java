package com.github.aeddddd.ae2enhanced.client.gui.panel;

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

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.me.InternalSlotME;
import appeng.client.me.SlotME;

import com.github.aeddddd.ae2enhanced.client.gui.dock.ChromeLayout;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;
import com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch;
import com.github.aeddddd.ae2enhanced.client.gui.util.CraftingHighlight;
import com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;

/**
 * 物品仓库模块：承接 {@code OmniItemRepo} 的 R3 分页物品列表（ME 展示槽位 + 搜索框 + 自持滚动条 +
 * 5 个标题栏控件）。
 *
 * <h3>可变列</h3>
 * <p>列数不是常量：{@code columns = contentWidth / 18}（钳制到 [9, maxRepoColumns]），行数同理。
 * 面板被拖动/缩放时列行会变，{@code InternalSlotME} 的坐标是构造期固化的，因此</p>
 * <ul>
 *   <li>形状（列行数）变化 → 重建整批 {@link InternalSlotME}，宿主下一个 tick 重建尾部 {@code SlotME}
 *       （与 {@code AEBaseGui.initGui} 的做法一致，保证 ME 槽永远在 {@code inventorySlots} 尾部）；</li>
 *   <li>仅位置变化 → 直接改写已存在 {@code SlotME} 的 {@code xPos/yPos}（{@code Slot} 的坐标是 public 的）。</li>
 * </ul>
 * <p>缓存容量随页容量变化：{@link OmniItemRepo#setSlotsPerPage(int)} 在容量变化时会立刻重新拉页，
 * 避免"缓存数组长度"与"请求 limit"不一致导致服务端按旧 limit 回包被截断。</p>
 *
 * <h3>面板自持的滚动条</h3>
 * <p>阶段 3a 起滚动条完全归本面板：在背景层自绘（因此会被上层窗口的 9-slice 正常覆盖），
 * 点击/拖拽经 {@code DockManager} 的内容路由进入 {@link #scrollbarPressed}/{@link #scrollbarDragged}，
 * 滚轮走 {@link #mouseScrolled}。不再注册到 {@code AEBaseGui.setScrollBar}，
 * "窗口遮挡时滚动条仍画在上层且可点"的副作用随之消失。</p>
 *
 * <h3>标题栏控件</h3>
 * <p>右侧控件条（左→右）：合成状态入口、SORT_BY、VIEW_MODE、SORT_DIRECTION、SEARCH_MODE。
 * {@link #titleRightInset()} 为它们预留宽度，{@link #inTitleBarControls(double, double)} 把它们
 * 从"标题栏拖动"里排除（宿主 GUI 会优先把这类点击交给原生按钮分发）。</p>
 */
public final class RepoPanel extends ModulePanel {
    public static final String MODULE_ID = "repo";

    /** 单个物品格边长（16×16 图标 + 1px 分隔）。 */
    public static final int SLOT_SIZE = 18;
    /** 内容区首行的搜索框占用的高度。 */
    public static final int SEARCH_ROW_HEIGHT = 18;
    public static final int MIN_COLUMNS = 9;
    public static final int MIN_ROWS = 3;
    /** 滚动条宽度（AE2 的 {@code GuiScrollbar} 默认值）。 */
    public static final int SCROLLBAR_WIDTH = 12;
    /** 物品格带与滚动条之间的间隔。 */
    public static final int SCROLLBAR_GAP = 2;
    public static final int DEFAULT_COLUMNS = 18;
    public static final int DEFAULT_ROWS = 5;
    /** 设置按钮边长（AE2 的 {@code GuiImgButton} 是 16×16）。 */
    public static final int TITLE_CONTROL_SIZE = 16;
    public static final int TITLE_CONTROL_GAP = 1;
    /** 标题栏右侧的设置按钮个数：SORT_BY / VIEW_MODE / SORT_DIRECTION / SEARCH_MODE。 */
    public static final int TITLE_SETTING_COUNT = 4;
    /** 合成状态入口（{@code GuiTabButton}）的绘制宽高。 */
    public static final int CRAFT_STATUS_WIDTH = 25;
    public static final int CRAFT_STATUS_HEIGHT = 22;
    /** 合成状态按钮相对面板顶边向上突出的像素（AE2 的 tab 观感）。 */
    private static final int CRAFT_STATUS_RAISE = 3;
    /** 配置缺失时的兜底列数上限（与 {@code AE2EnhancedConfig.Terminal.REPO_MAX_COLUMNS} 一致）。 */
    private static final int FALLBACK_MAX_COLUMNS = 26;

    private final ContainerSplicedTerm container;
    private final OmniItemRepo repo;
    private final GuiScrollbar scrollBar;
    /** 只在绘制滚动条时用到（{@code GuiScrollbar.draw} 需要宿主参数）。 */
    private final AEBaseGui host;

    /** 期望的 ME 槽位网格（每次形状变化整批重建）；宿主的 {@code getMeSlots()} 直接返回它。 */
    private final List<InternalSlotME> internalSlots = new ArrayList<InternalSlotME>();
    /** 当前挂在 {@code inventorySlots} 尾部的 ME 槽（AE2 在 initGui 里会重建，宿主需要重新接管）。 */
    private final List<SlotME> slotObjects = new ArrayList<SlotME>();
    private final Set<Slot> slotObjectSet = Collections.newSetFromMap(new IdentityHashMap<Slot, Boolean>());

    private int columns = DEFAULT_COLUMNS;
    private int rows = DEFAULT_ROWS;
    /** 形状与已登记的 {@code inventorySlots} 不一致（宿主下一个 tick 重建）。 */
    private boolean slotsStale;
    private long layoutVersion;

    private Runnable scrollListener;
    /** 本面板正在拖拽自己的滚动条（DockManager 内容路由据此把后续拖动留给本面板）。 */
    private boolean draggingScrollBar;

    public RepoPanel(ContainerSplicedTerm container, OmniItemRepo repo, GuiScrollbar scrollBar,
            AEBaseGui host) {
        this.container = container;
        this.repo = repo;
        this.scrollBar = scrollBar;
        this.host = host;
    }

    // ==================== 标识 / 尺寸 ====================

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String title() {
        return I18n.format("gui.ae2enhanced.spliced_term.repo");
    }

    /** 仓库是终端主体功能，不可关闭（关掉终端就没用了）。 */
    @Override
    public boolean closable() {
        return false;
    }

    @Override
    public int minWidth() {
        return widthFor(MIN_COLUMNS);
    }

    @Override
    public int defaultWidth() {
        return widthFor(DEFAULT_COLUMNS);
    }

    @Override
    public int minHeight() {
        return heightFor(MIN_ROWS);
    }

    @Override
    public int defaultHeight() {
        return heightFor(DEFAULT_ROWS);
    }

    private static int widthFor(int columns) {
        return columns * SLOT_SIZE + SCROLLBAR_GAP + SCROLLBAR_WIDTH + 2 * CONTENT_PADDING;
    }

    private static int heightFor(int rows) {
        return TITLE_BAR_HEIGHT + SEARCH_ROW_HEIGHT + rows * SLOT_SIZE + CONTENT_PADDING;
    }

    // ==================== 静态几何/推导（纯函数，可单测） ====================

    /** 配置里的列数上限（配置尚未初始化时回退到兜底值）。 */
    public static int maxRepoColumns() {
        AE2EnhancedConfig.Terminal terminal = AE2EnhancedConfig.terminal;
        int configured = terminal != null ? terminal.maxRepoColumns : FALLBACK_MAX_COLUMNS;
        return Math.max(MIN_COLUMNS, Math.min(FALLBACK_MAX_COLUMNS, configured));
    }

    /** 把请求的列数钳制到 [minColumns, maxColumns]（maxColumns 本身也会被抬到下限）。 */
    public static int clampColumns(int requested, int maxColumns) {
        int upper = Math.max(MIN_COLUMNS, maxColumns);
        return Math.max(MIN_COLUMNS, Math.min(upper, requested));
    }

    /** 内容宽度能放下多少列（扣掉滚动条与间隔）。 */
    public static int columnsFor(int contentWidth, int maxColumns) {
        int usable = Math.max(0, contentWidth - SCROLLBAR_GAP - SCROLLBAR_WIDTH);
        return clampColumns(usable / SLOT_SIZE, maxColumns);
    }

    /** 内容高度能放下多少行（首行留给搜索框）。 */
    public static int rowsFor(int contentHeight) {
        int usable = Math.max(0, contentHeight - SEARCH_ROW_HEIGHT);
        return Math.max(MIN_ROWS, usable / SLOT_SIZE);
    }

    /**
     * 滚动条能滚多少"行"。
     *
     * @param craftingPinned 合成置顶行是否占用第一行（占用时本屏正常物品少一行）
     */
    public static int maxScrollRows(int totalCount, int columns, int rows, boolean craftingPinned) {
        int cols = Math.max(1, columns);
        int effectiveRows = Math.max(1, rows - (craftingPinned ? 1 : 0));
        int totalRows = (Math.max(0, totalCount) + cols - 1) / cols;
        return Math.max(0, totalRows - effectiveRows);
    }

    // ==================== 内容区几何 ====================

    /** 物品格带的左上角（单元格原点，槽位图标在 +1）。 */
    public int gridLeft() {
        return contentLeft();
    }

    public int gridTop() {
        return contentTop() + SEARCH_ROW_HEIGHT;
    }

    /** 搜索框的包围盒：{@code {x, y, width, height}}（右侧让出模块开关那一排）。 */
    public int[] searchFieldBounds() {
        int width = Math.min(contentWidth(), columns * SLOT_SIZE + SCROLLBAR_GAP + SCROLLBAR_WIDTH);
        width -= moduleSwitchRowReserve();
        return new int[] { contentLeft(), contentTop() + 3, Math.max(40, width), 11 };
    }

    public int scrollBarLeft() {
        return contentLeft() + columns * SLOT_SIZE + SCROLLBAR_GAP;
    }

    public int scrollBarTop() {
        return gridTop();
    }

    public int scrollBarHeight() {
        return rows * SLOT_SIZE;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public long layoutVersion() {
        return layoutVersion;
    }

    /** 面板自己的滚动条被消费后立刻通知宿主同步落库（避免等下一个 tick）。 */
    public void setScrollListener(Runnable listener) {
        this.scrollListener = listener;
    }

    // ==================== 模块开关（阶段 3b 的最小显隐入口） ====================

    /** 模块开关小按钮的边长与间距（与搜索框同一行，右对齐）。 */
    public static final int MODULE_SWITCH_SIZE = 22;
    public static final int MODULE_SWITCH_GAP = 2;

    /**
     * 模块开关行的数据来源。
     *
     * <p>面板只负责几何、绘制与命中测试；"哪些模块、当前开没开、点了做什么"由宿主（持有
     * {@code DockManager} 与 {@code panelMask} 的一方）提供。</p>
     */
    public interface ModuleSwitchHost {
        int switchCount();

        /** 按钮上的 1~2 字短标签。 */
        String switchLabel(int index);

        /** 悬浮提示（完整模块名）。 */
        String switchTooltip(int index);

        boolean isSwitchOn(int index);

        /** 切换某个模块的可见性。 */
        void toggleSwitch(int index);

        /** 开关行最左侧是否附带"编辑布局"入口（阶段 4b）。 */
        default boolean hasEditorButton() {
            return false;
        }

        default String editorButtonLabel() {
            return "";
        }

        default String editorButtonTooltip() {
            return "";
        }

        default void onEditorButton() {
        }
    }

    private ModuleSwitchHost moduleSwitchHost;

    public void setModuleSwitchHost(ModuleSwitchHost host) {
        this.moduleSwitchHost = host;
    }

    // --- dev harness 专用：开关行的可枚举视图（正常运行路径走 moduleSwitchAt 命中测试） ---

    /** dev harness 专用：开关行里有多少个开关。 */
    public int moduleSwitchCount() {
        return moduleSwitchHost == null ? 0 : moduleSwitchHost.switchCount();
    }

    /** dev harness 专用：第 index 个开关对应哪个模块（与 {@link #moduleSwitchX(int)} 同序）。 */
    public String moduleSwitchModuleId(int index) {
        return com.github.aeddddd.ae2enhanced.container.SplicedTermModules.moduleIdAt(index);
    }

    /** 开关行最左侧"编辑布局"入口的边长（与开关同尺寸）。 */
    public static final int MODULE_SWITCH_EDITOR_SIZE = MODULE_SWITCH_SIZE;

    /** 开关按钮上的两字符缩写（AE2 面板名首两字母，可辨认且不溢出）。 */
    public int moduleSwitchLabelWidth() {
        return MODULE_SWITCH_SIZE - 4;
    }

    /** 入口按钮是否显示。 */
    private boolean hasEditorButton() {
        return moduleSwitchHost != null && moduleSwitchHost.hasEditorButton();
    }

    private int editorButtonReserve() {
        return hasEditorButton() ? MODULE_SWITCH_EDITOR_SIZE + MODULE_SWITCH_GAP : 0;
    }

    /** 开关行占用的宽度（含与搜索框之间的间隔）。 */
    private int moduleSwitchRowReserve() {
        if (moduleSwitchHost == null || moduleSwitchHost.switchCount() <= 0) {
            return editorButtonReserve() == 0 ? 0 : editorButtonReserve() + 4;
        }
        int count = moduleSwitchHost.switchCount();
        return count * MODULE_SWITCH_SIZE + (count - 1) * MODULE_SWITCH_GAP + 4 + editorButtonReserve();
    }

    private int moduleSwitchRowWidth() {
        int width = editorButtonReserve();
        if (moduleSwitchHost == null || moduleSwitchHost.switchCount() <= 0) {
            return width;
        }
        int count = moduleSwitchHost.switchCount();
        return width + count * MODULE_SWITCH_SIZE + (count - 1) * MODULE_SWITCH_GAP;
    }

    /** "编辑布局"入口按钮的位置（开关行最左侧）。 */
    public int editorButtonX() {
        return contentLeft() + Math.min(contentWidth(), columns * SLOT_SIZE + SCROLLBAR_GAP + SCROLLBAR_WIDTH)
                - moduleSwitchRowWidth();
    }

    public int editorButtonY() {
        return moduleSwitchY();
    }

    /** 命中测试：是否点在"编辑布局"入口上。 */
    public boolean editorButtonAt(double mx, double my) {
        if (!hasEditorButton() || !visible) {
            return false;
        }
        int left = editorButtonX();
        int top = editorButtonY();
        return mx >= left && mx < left + MODULE_SWITCH_EDITOR_SIZE
                && my >= top && my < top + MODULE_SWITCH_EDITOR_SIZE;
    }

    public int moduleSwitchX(int index) {
        int right = contentLeft() + Math.min(contentWidth(), columns * SLOT_SIZE + SCROLLBAR_GAP + SCROLLBAR_WIDTH);
        // 开关行左端留给"编辑布局"入口（editorButtonReserve），否则 E 按钮会被第 0 个开关盖住
        return right - moduleSwitchRowWidth() + editorButtonReserve()
                + index * (MODULE_SWITCH_SIZE + MODULE_SWITCH_GAP);
    }

    public int moduleSwitchY() {
        return contentTop() + 1;
    }

    /** 命中测试：返回开关下标，未命中返回 -1。 */
    public int moduleSwitchAt(double mx, double my) {
        if (moduleSwitchHost == null || !visible) {
            return -1;
        }
        int top = moduleSwitchY();
        if (my < top || my >= top + MODULE_SWITCH_SIZE) {
            return -1;
        }
        for (int i = 0; i < moduleSwitchHost.switchCount(); i++) {
            int left = moduleSwitchX(i);
            if (mx >= left && mx < left + MODULE_SWITCH_SIZE) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !visible) {
            return false;
        }
        if (editorButtonAt(mx, my)) {
            moduleSwitchHost.onEditorButton();
            return true;
        }
        int index = moduleSwitchAt(mx, my);
        if (index < 0 || moduleSwitchHost == null) {
            return false;
        }
        moduleSwitchHost.toggleSwitch(index);
        return true;
    }

    // ==================== 标题栏控件 ====================

    @Override
    protected int titleRightInset() {
        return super.titleRightInset() + titleControlsWidth();
    }

    /** 标题栏右侧控件条的总宽：合成状态 tab + 4 个设置按钮。 */
    public static int titleControlsWidth() {
        return CRAFT_STATUS_WIDTH + TITLE_CONTROL_GAP
                + TITLE_SETTING_COUNT * TITLE_CONTROL_SIZE
                + (TITLE_SETTING_COUNT - 1) * TITLE_CONTROL_GAP
                + 2;
    }

    /**
     * 标题栏右侧控件条的左边界。
     *
     * <p>基类是 {@code chromeControlsRight()}：dock 自己的钉住/浮动按钮、以及阶段 3b 起的
     * 常驻关闭按钮都在它右边，控件条不得压过去。</p>
     */
    public int titleControlsLeft() {
        return chromeControlsRight() - titleControlsWidth();
    }

    /** 设置按钮的纵向位置（16px 高，居中于 17px 标题栏）。 */
    public int titleControlsTop() {
        return y + (TITLE_BAR_HEIGHT - TITLE_CONTROL_SIZE) / 2;
    }

    /**
     * 合成状态入口的纵向位置：{@code GuiTabButton} 绘制 22px 高，比标题栏高，
     * 因此按 AE2 的 tab 观感上提 3px（下沿恰好落在标题栏下方、不压到搜索框那一行）。
     */
    public int craftStatusControlTop() {
        return y - CRAFT_STATUS_RAISE;
    }

    public int craftStatusControlX() {
        return titleControlsLeft();
    }

    /** 第 {@code index} 个设置按钮的屏幕 X（0 = SORT_BY，依次往右）。 */
    public int settingControlX(int index) {
        return titleControlsLeft() + CRAFT_STATUS_WIDTH + TITLE_CONTROL_GAP
                + index * (TITLE_CONTROL_SIZE + TITLE_CONTROL_GAP);
    }

    @Override
    public boolean inTitleBarControls(double mx, double my) {
        if (super.inTitleBarControls(mx, my)) {
            return true;
        }
        int left = titleControlsLeft();
        if (mx < left || mx >= left + titleControlsWidth()) {
            return false;
        }
        // 合成状态 tab 比标题栏高，纵向区间取两者的并集
        int top = Math.min(craftStatusControlTop(), titleControlsTop());
        int bottom = Math.max(craftStatusControlTop() + CRAFT_STATUS_HEIGHT,
                titleControlsTop() + TITLE_CONTROL_SIZE);
        return my >= top && my < bottom;
    }

    // ==================== 槽位布局 ====================

    @Override
    public void layoutSlots() {
        this.layoutVersion++;
        if (!visible) {
            for (SlotME slot : slotObjects) {
                hideSlot(slot);
            }
            return;
        }

        int nextColumns = columnsFor(contentWidth(), maxRepoColumns());
        int nextRows = rowsFor(contentHeight());
        int nextCount = nextColumns * nextRows;
        if (nextColumns != this.columns || nextRows != this.rows
                || internalSlots.size() != nextCount) {
            this.columns = nextColumns;
            this.rows = nextRows;
            rebuildInternalSlots();
            // 列数决定每屏物品数，直接决定缓存容量与分页请求 limit
            repo.setRowSize(this.columns);
            repo.setSlotsPerPage(nextCount);
            this.slotsStale = true;
        }
        positionSlots();
        applyScrollBarGeometry();
    }

    private void rebuildInternalSlots() {
        internalSlots.clear();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                internalSlots.add(new InternalSlotME(repo, column + row * columns,
                        gridLeft() + column * SLOT_SIZE + 1,
                        gridTop() + row * SLOT_SIZE + 1));
            }
        }
    }

    private void positionSlots() {
        if (slotObjects.size() != columns * rows) {
            return;
        }
        for (int index = 0; index < slotObjects.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            placeSlot(slotObjects.get(index),
                    gridLeft() + column * SLOT_SIZE + 1,
                    gridTop() + row * SLOT_SIZE + 1);
        }
    }

    private void applyScrollBarGeometry() {
        if (scrollBar == null) {
            return;
        }
        scrollBar.setLeft(scrollBarLeft()).setTop(scrollBarTop())
                .setWidth(SCROLLBAR_WIDTH).setHeight(Math.max(15, scrollBarHeight()));
    }

    // ==================== 宿主协作：槽位登记 ====================

    /** 期望的 ME 槽位网格，直接交给 {@code AEBaseGui.getMeSlots()} 使用。 */
    public List<InternalSlotME> meSlots() {
        return internalSlots;
    }

    /** 当前挂在 {@code inventorySlots} 尾部的 ME 槽（宿主用来登记"有面板承接"）。 */
    public List<SlotME> slotObjects() {
        return Collections.unmodifiableList(slotObjects);
    }

    /** 宿主可见时才算承接（不可见时 ME 槽被 {@code hideSlot} 挪走，允许清扫）。 */
    public List<SlotME> hostedSlots() {
        return visible ? slotObjects() : Collections.<SlotME>emptyList();
    }

    @Override
    public boolean ownsSlot(Slot slot) {
        return slotObjectSet.contains(slot);
    }

    /**
     * ME 槽由本面板动态创建并追加到 {@code inventorySlots} 尾部，不在基类的登记列表里，
     * 因此这里显式交给 dock 的 per-root 通道绘制（否则压平通道跳过 → 谁也不画 → 仓库整片不渲染）。
     */
    @Override
    protected java.util.List<? extends Slot> slotsToRender() {
        return visible ? slotObjects : java.util.Collections.<SlotME>emptyList();
    }

    /**
     * 把当前网格同步到 {@code inventorySlots}。
     *
     * <p>形状变化（或 AE2 在 {@code initGui} 里刚重建过这批槽位而引用失效）时整批重建，
     * 并<strong>追加到列表尾部</strong>——{@code inventorySlots.size()} 是 AE2 给 ME 操作使用的
     * 哨兵 slotId，ME 槽必须始终在最后。其余情况只改写坐标（拖动/缩放不需要换对象）。</p>
     */
    public void applySlots(List<Slot> inventorySlots) {
        if (inventorySlots == null) {
            return;
        }
        if (slotsStale || !adoptExisting(inventorySlots)) {
            rebuildSlotObjects(inventorySlots);
        }
        if (visible) {
            positionSlots();
        } else {
            for (SlotME slot : slotObjects) {
                hideSlot(slot);
            }
        }
    }

    /** 形状没变时重新接管 AE2 在 {@code initGui} 里新建的那些 {@code SlotME}；数量不符则返回 false。 */
    private boolean adoptExisting(List<Slot> inventorySlots) {
        ArrayList<SlotME> found = new ArrayList<SlotME>();
        for (int i = inventorySlots.size() - 1; i >= 0; i--) {
            Slot slot = inventorySlots.get(i);
            if (!(slot instanceof SlotME)) {
                break;
            }
            found.add(0, (SlotME) slot);
        }
        if (found.size() != internalSlots.size()) {
            return false;
        }
        replaceSlotObjects(found);
        slotsStale = false;
        return true;
    }

    private void rebuildSlotObjects(List<Slot> inventorySlots) {
        inventorySlots.removeIf(slot -> slot instanceof SlotME);
        ArrayList<SlotME> rebuilt = new ArrayList<SlotME>(internalSlots.size());
        for (InternalSlotME me : internalSlots) {
            SlotME slot = new SlotME(me);
            inventorySlots.add(slot);
            rebuilt.add(slot);
        }
        replaceSlotObjects(rebuilt);
        slotsStale = false;
    }

    private void replaceSlotObjects(List<SlotME> slots) {
        slotObjects.clear();
        slotObjectSet.clear();
        slotObjects.addAll(slots);
        slotObjectSet.addAll(slots);
    }

    // ==================== 渲染 ====================

    @Override
    public void renderBackgroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
        if (!visible) {
            return;
        }
        PlayerInvPanel.drawSlotBand(gridLeft(), gridTop(), columns, rows, zLevel);
        CraftingHighlight.drawPinnedRow(gridLeft(), gridTop(), columns,
                container.getClientActiveCrafting(), zLevel);
        renderModuleSwitches(zLevel);
        // 滚动条自绘在背景层：被上层窗口的 9-slice 正常覆盖（不再有穿透的滚动条）
        if (scrollBar != null) {
            boolean pinnedRow = !container.getClientActiveCrafting().isEmpty();
            NinePatch.drawScrollbar(scrollBarLeft(), scrollBarTop(), SCROLLBAR_WIDTH, scrollBarHeight(),
                    scrollBar.getCurrentScroll(),
                    maxScrollRows(repo.size(), columns, rows, pinnedRow), true, zLevel);
        }
    }

    /** 模块开关：开着的画浅色底 + 深色字，关掉的画深色底 + 浅色字（一眼可辨）。 */
    private void renderModuleSwitches(float zLevel) {
        if (moduleSwitchHost == null || moduleSwitchHost.switchCount() <= 0) {
            return;
        }
        boolean pushed = NinePatch.pushZLevel(zLevel);
        net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int top = moduleSwitchY();
        if (hasEditorButton()) {
            int left = editorButtonX();
            // 入口按钮：AE2 风格凸起底 + 暖色字，与"模块开关"区分开
            NinePatch.drawButtonBase(left, top, MODULE_SWITCH_EDITOR_SIZE, MODULE_SWITCH_EDITOR_SIZE,
                    false, zLevel);
            String label = moduleSwitchHost.editorButtonLabel();
            if (font != null && label != null && !label.isEmpty()) {
                String clipped = font.trimStringToWidth(label, MODULE_SWITCH_EDITOR_SIZE - 4);
                font.drawString(clipped,
                        left + (MODULE_SWITCH_EDITOR_SIZE - font.getStringWidth(clipped)) / 2,
                        top + 5, 0xFF8A6A1E, false);
            }
        }
        for (int i = 0; i < moduleSwitchHost.switchCount(); i++) {
            int left = moduleSwitchX(i);
            boolean on = moduleSwitchHost.isSwitchOn(i);
            // AE2 风格：凸起按钮底（开 = 亮底 + 深字，关 = 灰底 + 温和字），1px 深描边
            NinePatch.drawButtonBase(left, top, MODULE_SWITCH_SIZE, MODULE_SWITCH_SIZE, on, zLevel);
            if (!on) {
                Gui.drawRect(left + 1, top + 1, left + MODULE_SWITCH_SIZE - 1,
                        top + MODULE_SWITCH_SIZE - 1, ChromeLayout.COLOR_MUTED);
            }
            String label = moduleSwitchHost.switchLabel(i);
            if (font != null && label != null && !label.isEmpty()) {
                String clipped = font.trimStringToWidth(label, MODULE_SWITCH_SIZE - 4);
                font.drawString(clipped,
                        left + (MODULE_SWITCH_SIZE - font.getStringWidth(clipped)) / 2,
                        top + 5, on ? 0xFF413F54 : 0xFFE5E7ED, false);
            }
        }
        NinePatch.popZLevel(pushed);
    }

    // ==================== 输入 ====================

    /**
     * 光标落在本面板内容区时消费滚轮。
     *
     * <p>{@code GuiScrollbar.wheel} 只看符号（内部把增量 clamp 到 ±1 页），因此这里把已经换算成
     * "格数"的 {@code scrollY} 重新压成 ±1 再交给它。DockManager 已保证只有命中内容区才会进来。</p>
     */
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!visible || scrollBar == null || scrollY == 0) {
            return false;
        }
        scrollBar.wheel(scrollY > 0 ? 1 : -1);
        notifyScrollChanged();
        return true;
    }

    /** DockManager 内容路由第一步：点在滚动条轨道上则开始拖拽（同时完成一次跳转）。 */
    @Override
    public boolean scrollbarPressed(double mx, double my) {
        if (!visible || scrollBar == null || !inScrollBar(mx, my)) {
            return false;
        }
        this.draggingScrollBar = true;
        scrollBar.click(host, (int) mx, (int) my);
        notifyScrollChanged();
        return true;
    }

    @Override
    public boolean scrollbarDragged(double mx, double my) {
        if (!draggingScrollBar || scrollBar == null) {
            return false;
        }
        scrollBar.click(host, (int) mx, (int) my);
        notifyScrollChanged();
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

    private boolean inScrollBar(double mx, double my) {
        int left = scrollBar.getLeft();
        int top = scrollBar.getTop();
        return mx >= left && mx < left + scrollBar.getWidth()
                && my >= top && my < top + scrollBar.getHeight();
    }

    private void notifyScrollChanged() {
        if (scrollListener != null) {
            scrollListener.run();
        }
    }
}
