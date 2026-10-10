/*
 * Derived from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.client.gui.Gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockAxis;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockEdge;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockInsets;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspaceEditor;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutEngine;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutProjection;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutStyle;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutTrees;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleLayoutPolicy;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleMetrics;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.NodeIds;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.SplitNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.WorkspaceValidator;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutCodec;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutDto;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutPersistence;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.LayoutPresetBank;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.LegacyMigrationContext;

/**
 * 停靠引擎运行时：投影缓存、手势状态机、Z 序、吸附拼接、分隔条/握把缩放、undo 与持久化编排。
 *
 * <p>本类是 MEST {@code DockManager}（1.21.1）按 1.12.2 语境的重写：<strong>行为规格</strong>取自
 * 上游（阈值常量、判定顺序、不变量、防抖/revision 语义逐条对应），但去掉了与本阶段无关的部分——
 * 布局编辑器、组合模块 UI、越界 chrome（{@code outsideHit*}/{@code hasJoinableOutsideRail}）、
 * 内容微调（ContentNudge）、预设银行/分享导入导出、偏好文件、快捷栏/工具栏。这些字段或方法在
 * 上游存在，需要在后续阶段补。</p>
 *
 * <p>可测性：手势/几何/持久化路径只依赖 {@link DockHost} 与 model/workspace 包，不触碰 Minecraft；
 * MC 只出现在 {@link #render} / {@link #renderForeground} / 拖拽预览这类渲染方法里。</p>
 *
 * <h3>输入分发（阶段 3a 起）</h3>
 * <p>{@link #mouseClicked} 的判定顺序：窗口抽屉（分隔条 → 右下握把 → 标题栏按钮 → 标题栏拖动）→
 * <strong>内容区路由</strong>。内容区命中时先问面板自己的滚动条（{@code scrollbarPressed}），
 * 再问通用内容点击（{@code ModulePanel.mouseClicked}）；任一方消费即吞掉这次点击，不再穿透到
 * 下层窗口或宿主的 AE2 槽位逻辑。滚轮同理，但只认"最上层且命中内容区"的叶子。
 * 滚动条拖拽一旦开始就由 {@link #scrollbarDragPanel} 独占，直到 {@link #mouseReleased}
 * （宿主关屏时由 {@link #finishPendingGesture} 兜底释放）。</p>
 */
public final class DockManager {
    // --- 常量（MEST 数值逐字保留） ------------------------------------------------

    private static final int DIVIDER_THICKNESS = 0;
    private static final int DIVIDER_HIT_PADDING = 3;
    private static final int DROP_ZONE_MIN = 8;
    private static final int DROP_ZONE_MAX = 16;
    private static final int SNAP_DISTANCE = 8;
    private static final int LEAF_DRAG_THRESHOLD = 4;

    private static final int DROP_FILL_COLOR = 0x66ACE9FF;
    private static final int DROP_BORDER_COLOR = 0xDDACE9FF;
    private static final int DROP_ZONE_FILL_COLOR = 0x33ACE9FF;

    /**
     * 相邻"重叠带"之间的 z 间距；<b>必须大于槽位内容的内部 z 偏移</b>。
     *
     * <p>原版 {@code GuiContainer.drawSlot} 把物品画在
     * {@code itemRender.zLevel(100) + renderItemAndEffectIntoGUI(+50) + setupGuiTransform(100) = 250}
     * 处（AE2-UEL 的 {@code AEBaseGui} 覆写走同一条路），所以"压住某个窗口的那扇窗"必须比
     * 被压窗口的槽位内容再高 250 以上，否则被压窗口的物品会从上层窗口的框里透出来（实机 bug：
     * 抬升/遮挡窗口后物品渲染错乱）。{@code 300} 留出 {@code SLOT_CONTENT_Z} 之外的余量。</p>
     *
     * <p><b>整个 z 预算必须落在 {@code [-1000, 1000]} 内</b>：1.12.2 的 GUI 正交投影是
     * {@code ortho(0, w, h, 0, 1000, 3000)} + 模型视图 {@code translate(0, 0, -2000)}，
     * 于是世界 z 只有 {@code [-1000, 1000]} 这一段在视锥内，再深一点就会被<b>近裁剪面裁掉</b>
     * （不是深度测试失败：连绘制都没有，且不经过 z 平移的字体文字照常显示）。</p>
     *
     * <p>MEST 用的是 1.21.1 的 GUI 区间（可见 z 约 ±10000），它敢用 {@code 400/层}；移植时逐字照搬
     * 会让第 4 个窗口（{@code 3 * 400 = 1200}）整块消失。本实现另外改成<b>按重叠关系分配带</b>
     * （不重叠的窗口共用一条带），所以默认布局只占 1 条带，{@code MAX_Z_BAND = 2} 条带覆盖到
     * {@code 2 * 300 + 250 = 850}，仍在预算内。</p>
     */
    public static final float ROOT_LAYER_Z = 300.0F;
    /** 带号上限：{@code 2 * 300 + 12 + 250 = 862 ≤ 1000}，再多就只能共用最高带（牺牲层序不裁掉画面）。 */
    private static final int MAX_Z_BAND = 2;
    /** 槽位内容相对所在根的 z 偏移（叠在物品自带的 ~250 上）。 */
    public static final float SLOT_CONTENT_Z = 12.0F;
    /** 前景内容相对所在根的 z 偏移；高于槽位内容。 */
    private static final float FOREGROUND_Z = 36.0F;
    /** 槽位高亮等覆盖层相对所在根的 z 偏移；介于槽位与前景之间。 */
    private static final float OVERLAY_Z = 24.0F;

    private static final int UNDO_LIMIT = 32;
    private static final LayoutStyle LAYOUT_STYLE = new LayoutStyle(DockInsets.NONE, DIVIDER_THICKNESS);
    private static final Logger LOGGER = LogManager.getLogger(DockManager.class);

    private final DockHost host;

    // --- 模块注册 ----------------------------------------------------------------

    private final List<ModulePanel> panels = new ArrayList<ModulePanel>();
    private final Map<String, ModulePanel> panelsByModuleId = new LinkedHashMap<String, ModulePanel>();
    private List<ModulePanel> panelsView = Collections.emptyList();

    /** {@code LayoutTrees.leaves(root.content())} 的热路径缓存，投影重建时清空。 */
    private final Map<String, List<LeafNode>> leavesCache = new HashMap<String, List<LeafNode>>();
    private List<FloatingRoot> paintOrderCache;
    /** 每个根的"重叠带"号（与 {@link #paintOrderCache} 同生命周期）。 */
    private Map<String, Integer> paintBandCache;

    // --- 布局核心 ----------------------------------------------------------------

    private ModuleCatalog catalog;
    private ModuleCatalog persistenceCatalog;
    private DockWorkspaceEditor editor;
    private LayoutEngine layoutEngine;
    private DockLayoutPersistence persistence;
    private LegacyMigrationContext migrationContext;

    private DockWorkspace workspace;
    private DockWorkspace viewportWorkspace;
    private LayoutProjection projection;
    private boolean projectionDirty;
    private long workspaceRevision;
    private long persistedRevision = -1;
    private boolean persistenceBlockedAfterLoadFailure;
    private int structureVersion;
    private int screenWidth;

    // --- 布局编辑器（阶段 4b） ---
    /** 处于布局编辑态：视口钳制改用编辑画布内边距（见 {@link #clampRectToViewport}）。 */
    private boolean editingLayout;
    /** 进入编辑态时的 workspace 快照（取消时整体恢复）。 */
    private DockWorkspace editingOriginal;
    /** 进入编辑态时的撤销栈快照（编辑期间的撤销点随取消一起丢弃）。 */
    private List<DockWorkspace> editingUndoMark;
    /** 三槽命名预设银行；配置目录不可用时为 null（预设/分享功能整体降级）。 */
    private LayoutPresetBank.Data presets;
    private LayoutPresetBank.Data editingPresets;
    private LayoutPresetBank.Data editingPresetsOriginal;
    private DockLayoutCodec layoutCodec;
    private Path configDir;
    /** 编辑态下画布四周的 chrome 内边距（左侧栏/顶栏/属性栏），只在编辑态生效。 */
    private int editorInsetLeft;
    private int editorInsetTop;
    private int editorInsetRight;
    private int editorInsetBottom;
    /** 提交编辑后是否把可见窗口重新居中（MEST 的同名偏好；本项目按会话保存）。 */
    /**
     * "返回终端时把可见窗口居中"（MEST {@code centerOnReturn} 语义）。
     *
     * <p>默认关闭以避免影响无头单测的坐标假设；真实终端在 {@code initGui} 里显式打开
     * （见 {@code GuiSplicedTerm.initGui} 的 {@code setCenterOnReturn(true)} + {@code centerVisibleWorkspace()}）。</p>
     */
    private boolean centerOnReturn = false;
    private int screenHeight;
    private final WorkspaceUndoHistory undoHistory = new WorkspaceUndoHistory(UNDO_LIMIT);

    private DockWorkspace gestureStartWorkspace;
    private DockWorkspace gestureCanonicalWorkspace;
    private DockWorkspace gestureCanonicalViewport;
    private boolean gestureStructural;
    private DockWorkspace pendingFocusWorkspace;

    // --- 手势状态 ----------------------------------------------------------------

    private enum Mode {
        NONE,
        PENDING_LEAF_DRAG,
        DRAG_ROOT,
        RESIZE_ROOT,
        RESIZE_DIVIDER
    }

    private Mode mode = Mode.NONE;
    private String activeRootId;
    private String activeLeafNodeId;
    private String activeSplitId;
    private DockRect activeDividerBounds;
    private double resizeLastX;
    private double resizeLastY;
    private double grabOffsetX;
    private double grabOffsetY;
    private double pressX;
    private double pressY;
    private DropCandidate dropCandidate;
    /** Bounds of the leaf under the pointer while dragging a root; drives the splice-zone affordances. */
    private DockRect dragHoverBounds;

    /**
     * 当前正在拖拽自己滚动条的面板。与 root 手势互斥：一旦有面板接管滚动条拖拽，
     * dock 的 root 拖拽/缩放都不得参与，否则会一边滚列表一边搬窗口。
     */
    private ModulePanel scrollbarDragPanel;

    public DockManager(DockHost host) {
        if (host == null) {
            throw new NullPointerException("host");
        }
        this.host = host;
    }

    public boolean isEmpty() {
        return panels.isEmpty();
    }

    /**
     * 注册模块面板、按宿主尺寸构建布局并载入持久化布局。
     *
     * <p>加载语义与 MEST 一致：损坏文档被隔离/标记时（{@code lastLoadWasCorrupt}）或加载抛异常时，
     * 进入"禁止回写"状态（{@code persistenceBlockedAfterLoadFailure}），直到用户做出一次真实布局
     * 编辑才解除——这样恢复出来的默认布局不会覆写用户那份待人工检查的原文件。</p>
     */
    public void init(List<ModulePanel> initialPanels) {
        if (initialPanels == null || initialPanels.isEmpty()) {
            throw new IllegalArgumentException("at least one module panel is required");
        }
        this.screenWidth = positiveViewport(host.screenWidth());
        this.screenHeight = positiveViewport(host.screenHeight());

        panels.clear();
        panelsByModuleId.clear();
        for (ModulePanel panel : initialPanels) {
            if (panel == null) {
                throw new NullPointerException("module panel");
            }
            if (panelsByModuleId.putIfAbsent(panel.id(), panel) != null) {
                throw new IllegalArgumentException("duplicate module panel id: " + panel.id());
            }
            panel.hosted = false;
            panels.add(panel);
        }
        panelsView = Collections.unmodifiableList(new ArrayList<ModulePanel>(panels));

        migrationContext = LegacyMigrationContext.currentDockDefaults(screenWidth, screenHeight);
        catalog = createCatalog(panels, DockInsets.NONE);
        persistenceCatalog = createCatalog(panels, migrationContext.rootInsets());
        editor = new DockWorkspaceEditor(catalog);
        layoutEngine = new LayoutEngine(catalog, LAYOUT_STYLE);
        // codec 用真实 catalog（不含 legacy v1 内边距）：persistenceCatalog 的每个模块尺寸都比面板真实
        // 尺寸小 rootInsets（7,25,7,7），拿它做"多叶窗口按内容尺寸重算"会把窗口缩掉 14/32px，
        // 与默认布局构造（真实尺寸）不一致 → 读回后叶子溢出 root（实机表现为面板背景/按钮跑到窗口框外）。
        // v1 文档的坐标迁移由 migrationContext 负责，与这里的 catalog 口径无关。
        DockLayoutCodec codec = new DockLayoutCodec(catalog, migrationContext, this::fitCompositeRootsToShell);
        layoutCodec = codec;
        configDir = host.configDir();
        persistence = host.createPersistence(codec);

        boolean loadedCurrentLayout = false;
        boolean rewriteMigratedLayout = false;
        persistenceBlockedAfterLoadFailure = false;
        if (persistence != null) {
            try {
                Optional<DockLayoutCodec.DecodedLayout> loaded = persistence.load();
                if (persistence.lastLoadWasCorrupt()) {
                    persistenceBlockedAfterLoadFailure = true;
                    LOGGER.warn("Recovered dock layout from defaults; corrupt source: {}",
                            persistence.lastQuarantinedPath().<Object>map(Object::toString)
                                    .orElse("terminal item NBT (omni_layout)"));
                }
                if (loaded.isPresent()) {
                    DockLayoutCodec.DecodedLayout decoded = loaded.get();
                    workspace = decoded.workspace();
                    rewriteMigratedLayout = decoded.needsRewrite();
                    loadedCurrentLayout = !decoded.needsRewrite();
                } else {
                    workspace = host.createDefaultWorkspace(persistenceCatalog, migrationContext,
                            this::fitCompositeRootsToShell);
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Failed to load dock layout v{}, using defaults", DockLayoutDto.CURRENT_VERSION, e);
                // This also covers a failed quarantine move. In that case the original file may still
                // exist, so it is especially important that a later close hook cannot overwrite it.
                persistenceBlockedAfterLoadFailure = true;
                workspace = host.createDefaultWorkspace(persistenceCatalog, migrationContext,
                        this::fitCompositeRootsToShell);
            }
        } else {
            workspace = host.createDefaultWorkspace(persistenceCatalog, migrationContext,
                    this::fitCompositeRootsToShell);
        }

        loadPresetBank(workspace);
        if (presets != null) {
            if (loadedCurrentLayout && !persistenceBlockedAfterLoadFailure) {
                // 布局文件/物品 NBT 每次都先写，永远不会比活动预设槽更旧：让槽位跟上它
                presets = presets.withWorkspace(presets.active(), workspace);
            } else {
                workspace = presets.workspaces()[presets.active()];
            }
        }

        viewportWorkspace = fitRootsToViewport(clampWorkspaceToViewport(workspace));
        workspaceRevision = 1;
        persistedRevision = loadedCurrentLayout ? workspaceRevision : -1;
        undoHistory.clear();
        gestureStartWorkspace = null;
        pendingFocusWorkspace = null;
        mode = Mode.NONE;
        activeRootId = null;
        activeLeafNodeId = null;
        activeSplitId = null;
        dropCandidate = null;
        projectionDirty = true;
        structureVersion++;
        ensureProjection();
        if (ensureAnchoredVisible()) {
            save();
        } else if (rewriteMigratedLayout && !persistenceBlockedAfterLoadFailure) {
            save();
        }
    }

    // ==================== 布局编辑器（阶段 4b） ====================

    /**
     * 进入布局编辑态。
     *
     * <p>快照三样东西供 {@link #cancelLayoutEditing()} 整体还原：当前 workspace、撤销栈、预设银行。
     * 编辑态下 {@link #clampRectToViewport} 会把视口缩到编辑画布内，画布范围由编辑器每帧通过
     * {@link #setEditorCanvasInsets} 提供。</p>
     */
    public DockWorkspace beginLayoutEditing() {
        if (editingLayout) {
            return workspace;
        }
        editingLayout = true;
        editingOriginal = workspace;
        editingUndoMark = undoHistory.snapshot();
        captureActivePreset();
        editingPresets = presets == null ? null : presets.copy();
        editingPresetsOriginal = editingPresets;
        return workspace;
    }

    /**
     * 取消编辑：丢弃编辑期间的所有改动（workspace + 撤销点 + 预设槽），恢复进入时的状态。
     *
     * <p>与 MEST 的差异：本项目的 {@code hideModule}/{@code setModuleVisible}/{@code setWindowFloating}
     * 等操作会<strong>即时落盘</strong>，所以取消后必须补一次 {@link #save()}，
     * 否则物品 NBT 会停在编辑中途的状态。</p>
     */
    public void cancelLayoutEditing() {
        if (!editingLayout) {
            return;
        }
        // 编辑器可能在手势中途被 Esc 关掉：先把手势/滚动条拖拽收干净，再回滚布局
        finishPendingGesture();
        resetGestureState();
        if (editingPresetsOriginal != null) {
            presets = editingPresetsOriginal.copy();
            replaceWorkspace(presets.workspaces()[presets.active()], true);
        } else if (editingOriginal != null && !editingOriginal.equals(workspace)) {
            replaceWorkspace(editingOriginal, true);
        }
        if (editingUndoMark != null) {
            // 编辑期间产生的撤销点描述的是刚刚被丢弃的改动
            undoHistory.restore(editingUndoMark);
        }
        editingUndoMark = null;
        editingPresets = null;
        editingPresetsOriginal = null;
        editingOriginal = null;
        clearEditorCanvasInsets();
        editingLayout = false;
        resetGestureState();
        save();
    }

    /** 提交编辑：结束编辑态、按偏好居中可见窗口、把活动槽写回预设银行并落盘。 */
    public void commitLayoutEditing() {
        if (!editingLayout) {
            return;
        }
        finishPendingGesture();
        clearEditorCanvasInsets();
        editingLayout = false;
        editingOriginal = null;
        editingPresets = null;
        editingPresetsOriginal = null;
        editingUndoMark = null;
        resetGestureState();
        if (centerOnReturn) {
            // MEST 把居中延后到"回到终端后的第一帧"；本编辑器与终端同一屏幕尺寸，直接居中等价
            centerVisibleWorkspace();
        }
        captureActivePreset();
        savePresetBankQuietly();
        save();
    }

    public boolean isEditingLayout() {
        return editingLayout;
    }

    /**
     * 编辑态画布内边距：窗口钳制到 {@code [left, top, screenWidth-right, screenHeight-bottom]} 之内，
     * 标题条不会滑到编辑器 chrome 底下。只在编辑态生效，且不重写工作区（属于投影层钳制）。
     */
    public void setEditorCanvasInsets(int left, int top, int right, int bottom) {
        if (!editingLayout) {
            return;
        }
        int newLeft = Math.max(0, left);
        int newTop = Math.max(0, top);
        int newRight = Math.max(0, right);
        int newBottom = Math.max(0, bottom);
        if (editorInsetLeft == newLeft && editorInsetTop == newTop
                && editorInsetRight == newRight && editorInsetBottom == newBottom) {
            return;
        }
        editorInsetLeft = newLeft;
        editorInsetTop = newTop;
        editorInsetRight = newRight;
        editorInsetBottom = newBottom;
        viewportWorkspace = fitRootsToViewport(clampWorkspaceToViewport(workspace));
        projectionDirty = true;
    }

    public void clearEditorCanvasInsets() {
        if (editorInsetLeft == 0 && editorInsetTop == 0 && editorInsetRight == 0 && editorInsetBottom == 0) {
            return;
        }
        editorInsetLeft = 0;
        editorInsetTop = 0;
        editorInsetRight = 0;
        editorInsetBottom = 0;
        viewportWorkspace = fitRootsToViewport(clampWorkspaceToViewport(workspace));
        projectionDirty = true;
    }

    /** 当前编辑画布内边距 {@code {left, top, right, bottom}}（测试/调试用）。 */
    public int[] editorCanvasInsets() {
        return new int[] { editorInsetLeft, editorInsetTop, editorInsetRight, editorInsetBottom };
    }

    /**
     * 从侧栏"调色板"拖一个模块进画布。
     *
     * <p>隐藏模块先显形（否则它没有投影几何可拖）；拼接窗口里的模块先拆成独立窗口，
     * 之后走与标题栏拖拽完全相同的吸附/拼接路径。返回 false 表示已有手势在跑或模块无法定位。</p>
     */
    public boolean startExternalDrag(ModulePanel panel, double mouseX, double mouseY) {
        ensureProjection();
        if (panel == null || mode != Mode.NONE) {
            return false;
        }
        LeafNode leaf = leafForPanel(panel);
        DockWorkspace before = workspace;

        ModuleLayoutPolicy policy = workspace.policyFor(leaf.moduleId());
        if (!policy.visible()) {
            replaceWorkspace(editor.setLeafVisible(workspace, leaf.nodeId(), true, null, null, null), false);
        }

        FloatingRoot root = rootContainingNode(viewportWorkspace, leaf.nodeId());
        if (root == null) {
            return false;
        }
        if (!root.content().nodeId().equals(leaf.nodeId())) {
            // 叶子在拼接窗口里：按当前投影矩形拆成独立窗口，指针相对位置才不会跳
            DockRect leafBounds = projection.visibleLeaf(leaf.nodeId())
                    .map(LayoutProjection.LeafPlacement::bounds)
                    .orElse(null);
            if (leafBounds == null) {
                return false;
            }
            String newRootId = "root:" + NodeIds.random();
            DockWorkspace detached = detachAndRestore(workspace, leaf.nodeId(), newRootId);
            detached = editor.raiseRoot(detached, newRootId);
            if (detached.equals(workspace)) {
                return false;
            }
            replaceWorkspace(detached, true);
            root = rootById(newRootId);
        } else {
            List<FloatingRoot> roots = viewportWorkspace.roots();
            if (!roots.isEmpty() && !roots.get(roots.size() - 1).rootId().equals(root.rootId())) {
                replaceWorkspace(editor.raiseRoot(workspace, root.rootId()), false);
                root = rootById(root.rootId());
            }
        }
        if (root == null) {
            return false;
        }

        mode = Mode.DRAG_ROOT;
        activeRootId = root.rootId();
        grabOffsetX = mouseX - root.bounds().x();
        grabOffsetY = mouseY - root.bounds().y();
        pressX = mouseX;
        pressY = mouseY;
        dropCandidate = null;
        dragHoverBounds = null;
        gestureStartWorkspace = before;
        return true;
    }

    // --- 预设银行 ---------------------------------------------------------------

    public int presetCount() {
        return LayoutPresetBank.SLOT_COUNT;
    }

    public int activePreset() {
        return presets == null ? 0 : presets.active();
    }

    /** 槽位显示名：自定义名优先，否则回落到槽位序号（"1"/"2"/"3"）。 */
    public String presetName(int index) {
        int slot = Math.floorMod(index, LayoutPresetBank.SLOT_COUNT);
        String custom = presets == null ? "" : presets.names()[slot];
        if (custom != null && !custom.trim().isEmpty()) {
            return custom;
        }
        return String.valueOf(slot + 1);
    }

    public void setPresetName(int index, String name) {
        if (presets == null) {
            return;
        }
        int slot = Math.floorMod(index, LayoutPresetBank.SLOT_COUNT);
        String sanitized = LayoutPresetBank.sanitizeName(name, String.valueOf(slot + 1));
        if (sanitized.equals(presets.names()[slot])) {
            return;
        }
        presets = presets.withName(slot, sanitized);
        if (!editingLayout) {
            savePresetBankQuietly();
        }
    }

    /** 预设银行是否可用（配置目录不可用时为 false：编辑器隐藏预设页签）。 */
    public boolean hasPresets() {
        return presets != null;
    }

    /** 切到某个预设槽：先把当前活动槽存回，再整体替换工作区。 */
    public void selectPreset(int index) {
        if (presets == null) {
            return;
        }
        int slot = Math.floorMod(index, LayoutPresetBank.SLOT_COUNT);
        captureActivePreset();
        if (slot == presets.active() && presets.workspaces()[slot].equals(workspace)) {
            return;
        }
        presets = presets.withActive(slot);
        undoHistory.clear();
        replaceWorkspace(presets.workspaces()[slot], true);
        ensureAnchoredVisible();
        if (!editingLayout) {
            persistenceBlockedAfterLoadFailure = false;
            savePresetBankQuietly();
            save();
        }
    }

    public void cyclePreset() {
        selectPreset(activePreset() + 1);
    }

    /** 恢复默认布局（用宿主自己的默认拼接布局，而不是 catalog 级联摆放）。 */
    public void resetLayout() {
        if (persistenceCatalog == null || migrationContext == null) {
            return;
        }
        DockWorkspace defaults = host.createDefaultWorkspace(
                persistenceCatalog, migrationContext, this::fitCompositeRootsToShell);
        applyPresetWorkspace(defaults);
    }

    // --- 居中 -------------------------------------------------------------------

    public boolean centerOnReturn() {
        return centerOnReturn;
    }

    public void setCenterOnReturn(boolean value) {
        this.centerOnReturn = value;
    }

    /** 把所有可见窗口整体居中到当前视口（编辑提交时用）。 */
    public void centerVisibleWorkspace() {
        if (workspace == null) {
            return;
        }
        ensureProjection();
        Set<String> movedRootIds = new LinkedHashSet<String>();
        DockRect union = null;
        for (FloatingRoot root : workspace.roots()) {
            if (!hasVisibleLeaf(workspace, root.content())) {
                continue;
            }
            DockRect part = contentChromeBounds(root);
            if (part == null || part.width() <= 0 || part.height() <= 0) {
                continue;
            }
            movedRootIds.add(root.rootId());
            union = union == null ? part : union.union(part);
        }
        if (movedRootIds.isEmpty() || union == null) {
            return;
        }
        DockWorkspace next = editor.centerRoots(workspace, movedRootIds, union, screenWidth, screenHeight);
        if (!next.equals(workspace)) {
            replaceWorkspace(next, true);
        }
    }

    // --- 导入 / 导出 -------------------------------------------------------------

    /** 分享/导入结果的轻量返回值：语言键交给界面层本地化（DockManager 不碰 I18n）。 */
    public static final class LayoutShareResult {
        private final boolean ok;
        private final String langKey;
        private final Path path;

        private LayoutShareResult(boolean ok, String langKey, Path path) {
            this.ok = ok;
            this.langKey = langKey;
            this.path = path;
        }

        static LayoutShareResult ok(String langKey) {
            return new LayoutShareResult(true, langKey, null);
        }

        static LayoutShareResult ok(String langKey, Path path) {
            return new LayoutShareResult(true, langKey, path);
        }

        static LayoutShareResult fail(String langKey) {
            return new LayoutShareResult(false, langKey, null);
        }

        public boolean ok() {
            return ok;
        }

        public String langKey() {
            return langKey;
        }

        /** 需要展示给玩家的路径（通常是分享目录），可能为 null。 */
        public Path path() {
            return path;
        }
    }

    /** 剪贴板导出：活动槽的完整文档（含名字与当前工作区）。 */
    public String exportLayoutJson() {
        if (layoutCodec == null) {
            return null;
        }
        captureActivePreset();
        DockWorkspace current = presets == null ? workspace : presets.workspaces()[presets.active()];
        String name = presets == null ? "" : presets.names()[presets.active()];
        return LayoutPresetBank.encodeSlot(current, name, layoutCodec);
    }

    /**
     * 剪贴板导入：接受"单份布局文档"或"整包预设文档"，写入预设银行的活动槽。
     *
     * <p>只认 {@link LayoutPresetBank#read} 能解析的内容（codec 会做版本迁移 / reconcile /
     * 解码预算校验），解析失败一律拒绝并保留原状态。</p>
     */
    public LayoutShareResult importLayoutJson(String json) {
        if (presets == null || layoutCodec == null) {
            return LayoutShareResult.fail("gui.ae2enhanced.spliced_editor.import.fail");
        }
        if (json == null || json.trim().isEmpty()) {
            return LayoutShareResult.fail("gui.ae2enhanced.spliced_editor.import.empty");
        }
        try {
            presets = LayoutPresetBank.read(json, layoutCodec, presets);
            undoHistory.clear();
            replaceWorkspace(presets.workspaces()[presets.active()], true);
            ensureAnchoredVisible();
            if (!editingLayout) {
                persistenceBlockedAfterLoadFailure = false;
                savePresetBankQuietly();
                save();
            }
            return LayoutShareResult.ok("gui.ae2enhanced.spliced_editor.import.ok");
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to import spliced terminal layout", e);
            return LayoutShareResult.fail("gui.ae2enhanced.spliced_editor.import.invalid");
        }
    }

    /** 分享目录（配置目录不可用时为 null）。 */
    public Path shareFolder() {
        return configDir == null ? null : configDir.resolve("share");
    }

    /** 把三个预设槽写到分享目录（外加一份 zip，便于整包发给别人）。 */
    public LayoutShareResult exportToShareFolder() {
        if (presets == null || layoutCodec == null || configDir == null) {
            return LayoutShareResult.fail("gui.ae2enhanced.spliced_editor.export.fail");
        }
        captureActivePreset();
        try {
            Path share = shareFolder();
            Files.createDirectories(share);
            for (int index = 0; index < LayoutPresetBank.SLOT_COUNT; index++) {
                Path slotDir = share.resolve(LayoutPresetBank.shareSlotFolder(index));
                Files.createDirectories(slotDir);
                LayoutPresetBank.writeAtomic(
                        slotDir.resolve(LayoutPresetBank.SHARE_LAYOUT_FILE),
                        LayoutPresetBank.encodeSlot(
                                presets.workspaces()[index], presets.names()[index], layoutCodec));
            }
            LayoutPresetBank.zipShareSlots(share, share.resolve(LayoutPresetBank.SHARE_ZIP_FILE));
            return LayoutShareResult.ok("gui.ae2enhanced.spliced_editor.export.ok", share);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to export spliced terminal layouts", e);
            return LayoutShareResult.fail("gui.ae2enhanced.spliced_editor.export.fail");
        }
    }

    // --- 内部 -------------------------------------------------------------------

    private void applyPresetWorkspace(DockWorkspace target) {
        if (target == null) {
            return;
        }
        if (!target.equals(workspace)) {
            // W4b 修复：必须在替换之后再记录快照。原来先 remember(workspace) 再替换，
            // 而 rememberUndoPoint 的守卫是 snapshot.equals(workspace) —— 此刻两者是同一个对象，
            // 于是快照被静默丢弃，编辑器里点"恢复默认"后没有任何撤销点（实机表现为"撤销无反应"）。
            DockWorkspace before = workspace;
            replaceWorkspace(target, true);
            rememberUndoPoint(before);
        }
        // 恢复默认是显式修复动作：允许覆盖此前被隔离/损坏的布局
        persistenceBlockedAfterLoadFailure = false;
        captureActivePreset();
        savePresetBankQuietly();
        save();
    }

    private void loadPresetBank(DockWorkspace current) {
        if (configDir == null || layoutCodec == null) {
            return;
        }
        DockWorkspace filler = host.createDefaultWorkspace(persistenceCatalog, migrationContext,
                this::fitCompositeRootsToShell);
        LayoutPresetBank.Data fallback = LayoutPresetBank.create(current, filler, null);
        Path file = configDir.resolve(LayoutPresetBank.PRESETS_FILE);
        presets = LayoutPresetBank.load(file, layoutCodec, fallback);
        if (!Files.isRegularFile(file)) {
            savePresetBankQuietly();
        }
    }

    private void captureActivePreset() {
        if (presets == null || workspace == null) {
            return;
        }
        presets = presets.withWorkspace(presets.active(), workspace);
    }

    private void savePresetBank() throws IOException {
        if (presets == null || layoutCodec == null || configDir == null) {
            return;
        }
        captureActivePreset();
        LayoutPresetBank.save(configDir.resolve(LayoutPresetBank.PRESETS_FILE), presets, layoutCodec);
    }

    private void savePresetBankQuietly() {
        try {
            savePresetBank();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to write spliced terminal layout presets", e);
        }
    }

    /** 视口变化（窗口缩放）时调用：只重算 transient 投影，不动持久化几何。 */
    public void updateViewport(int newScreenWidth, int newScreenHeight) {
        int newWidth = positiveViewport(newScreenWidth);
        int newHeight = positiveViewport(newScreenHeight);
        if (this.screenWidth == newWidth && this.screenHeight == newHeight) {
            return;
        }
        this.screenWidth = newWidth;
        this.screenHeight = newHeight;
        if (workspace == null) {
            return;
        }
        // Viewport clamping is a transient projection concern. Keep the canonical workspace (and
        // its persisted revision) untouched so a temporary small window does not destroy the user's
        // larger-screen geometry.
        // 但只夹位置不够：窗口比视口还大时会被夹成原样（既超出屏幕又拖不动，因为拖动也是"夹进视口"），
        // 所以这里再把**视口副本**里放不下的 root 缩进视口内。用户一旦真的拖动窗口，
        // 那次编辑写回 canonical 的就是他看到的尺寸，符合预期。
        viewportWorkspace = fitRootsToViewport(clampWorkspaceToViewport(workspace));
        projectionDirty = true;
    }

    /** 视口内保留的边距（与默认布局构造时的 {@code VIEWPORT_MARGIN} 一致）。 */
    private static final int VIEWPORT_FIT_MARGIN = 8;

    /**
     * 把放不进视口的 root 收缩到视口内（只缩不放）。
     *
     * <p>加载来的布局可能是在更大的窗口里存下的：视口钳制只能挪位置、不能缩尺寸，
     * 于是窗口超出屏幕且拖不动（拖动 = 把目标矩形夹进视口，超尺寸时结果恒等于原样）。
     * 这里把 canonical workspace 的 root 尺寸收到视口内，让"拖标题栏移动窗口"至少有可移动空间。</p>
     */
    private DockWorkspace fitRootsToViewport(DockWorkspace source) {
        DockWorkspace next = source;
        for (FloatingRoot root : source.roots()) {
            DockRect bounds = root.bounds();
            int maxWidth = Math.max(1, screenWidth - VIEWPORT_FIT_MARGIN);
            int maxHeight = Math.max(1, screenHeight - VIEWPORT_FIT_MARGIN);
            if (bounds.width() <= maxWidth && bounds.height() <= maxHeight) {
                continue;
            }
            // 不能低于树的固有最小尺寸（LayoutEngine 会保住每个模块的最小尺寸，写更小的值没有意义，
            // 只会让 canonical 与投影来回抖动）
            DockSize minimum = minimumOf(layoutEngine.project(next), root.rootId());
            DockRect fitted = clampRectToViewport(new DockRect(
                    bounds.x(), bounds.y(),
                    Math.max(minimum.width(), Math.min(bounds.width(), maxWidth)),
                    Math.max(minimum.height(), Math.min(bounds.height(), maxHeight))), DockSize.ZERO);
            next = editor.setRootBounds(next, root.rootId(), fitted);
        }
        return next;
    }

    public int structureVersion() {
        return structureVersion;
    }

    /** Stable module-registration order, independent of floating-root Z order. */
    public List<ModulePanel> panels() {
        return panelsView;
    }

    public DockWorkspace workspaceSnapshot() {
        return workspace;
    }

    /**
     * 只读的手势状态诊断串（<b>dev 自检 harness 专用</b>，不参与任何逻辑）。
     *
     * <p>形如 {@code "mode=DRAG_ROOT root=r1 leaf=repo drag=(12,3) scrollbar=none"}，
     * 让"按下标题栏后到底进了哪个手势分支"能从日志里直接读出来。</p>
     */
    public String diagnoseGesture() {
        StringBuilder sb = new StringBuilder();
        sb.append("mode=").append(mode);
        sb.append(" root=").append(activeRootId == null ? "-" : activeRootId);
        sb.append(" leaf=").append(activeLeafNodeId == null ? "-" : activeLeafNodeId);
        sb.append(" split=").append(activeSplitId == null ? "-" : activeSplitId);
        sb.append(" drop=").append(dropCandidate == null ? "-" : dropCandidate.targetNodeId() + "/" + dropCandidate.edge());
        sb.append(" scrollbar=").append(scrollbarDragPanel == null ? "-" : scrollbarDragPanel.id());
        sb.append(" pending=").append(pendingFocusWorkspace == null ? "-" : "yes");
        return sb.toString();
    }

    /** 当前投影（必要时重建缓存）。渲染与命中测试之外，测试也用它观察"缓存是否被复用"。 */
    public LayoutProjection projection() {
        ensureProjection();
        return projection;
    }

    // --- 投影缓存 ----------------------------------------------------------------

    private void ensureProjection() {
        if (!projectionDirty || workspace == null || viewportWorkspace == null || layoutEngine == null) {
            return;
        }
        leavesCache.clear();
        paintOrderCache = null;
        paintBandCache = null;
        LayoutProjection next = layoutEngine.project(viewportWorkspace);
        for (FloatingRoot root : viewportWorkspace.roots()) {
            int visibleLeaves = 0;
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                if (next.visibleLeaf(leaf.nodeId()).isPresent()) {
                    visibleLeaves++;
                }
            }
            boolean composite = visibleLeaves > 1;
            DockRect window = composite ? root.bounds() : null;
            LeafNode floatControlLeaf = topRightLeaf(root, next);
            // Anchored windows can float out; floating ones have no combine UI in this stage.
            boolean canFloat = !root.floating() && otherAnchoredVisible(root, next);
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                ModulePanel panel = panelsByModuleId.get(leaf.moduleId());
                if (panel == null) {
                    continue;
                }
                Optional<LayoutProjection.LeafPlacement> placement = next.visibleLeaf(leaf.nodeId());
                boolean visible = placement.isPresent();
                if (placement.isPresent()) {
                    DockRect bounds = placement.get().bounds();
                    panel.x = bounds.x();
                    panel.y = bounds.y();
                    panel.width = bounds.width();
                    panel.height = bounds.height();
                }
                panel.visible = visible;
                panel.hosted = false;
                panel.spliced = composite;
                panel.splicedWindow = window;
                // The pin belongs to the window, not to each section of a spliced window.
                // Keep it next to the single float control on the top-right title bar.
                panel.setPinControl(visible && root.floating() && (!composite || leaf == floatControlLeaf),
                        root.pinned());
                panel.setFloatControl(visible && canFloat && leaf == floatControlLeaf, root.floating(),
                        root.group());
            }
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                ModulePanel panel = panelsByModuleId.get(leaf.moduleId());
                if (panel == null) {
                    continue;
                }
                panel.contentRightInset = panel.preferredContentRightInset();
                panel.contentOffsetX = 0;
                panel.contentOffsetY = 0;
                panel.layoutSlots();
            }
        }
        projection = next;
        projectionDirty = false;
    }

    /** 本轮布局是否有可见的固定（非浮动）窗口；工具栏类 chrome 依赖该不变量。 */
    public boolean hasVisibleAnchored() {
        return hasVisibleAnchored(workspace);
    }

    private boolean hasVisibleAnchored(DockWorkspace candidate) {
        if (catalog == null || candidate == null) {
            return true;
        }
        for (FloatingRoot root : candidate.roots()) {
            if (!root.floating() && hasVisibleModule(candidate, root)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasVisibleModule(DockWorkspace candidate, FloatingRoot root) {
        for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
            if (candidate.policyFor(leaf.moduleId()).visible()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Saved layouts from before the anchored-group rule can leave every visible window floating,
     * which hides the toolbar (and any anchored chrome). Recover by pinning one panel back.
     *
     * @return true 表示本次调用修改了布局（调用方应落盘）
     */
    public boolean ensureAnchoredVisible() {
        if (workspace == null || hasVisibleAnchored()) {
            return false;
        }
        ModulePanel pick = null;
        for (ModulePanel panel : panels) {
            if (workspace.policyFor(panel.id()).visible()) {
                pick = panel;
                break;
            }
        }
        if (pick == null && !panels.isEmpty()) {
            pick = panels.get(0);
        }
        if (pick == null) {
            return false;
        }
        LeafNode leaf = leafForPanel(pick);
        DockWorkspace next = workspace;
        if (!next.policyFor(pick.id()).visible()) {
            next = editor.setLeafVisible(next, leaf.nodeId(), true, null, null, null);
        }
        next = editor.setRootMode(next, rootContainingNode(next, leaf.nodeId()).rootId(), false, false);
        replaceWorkspace(next, true);
        return hasVisibleAnchored();
    }

    // --- 工作区替换与持久化编排 ---------------------------------------------------

    private void replaceWorkspace(DockWorkspace changed, boolean structural) {
        if (gestureCanonicalWorkspace != null && mode != Mode.NONE) {
            replaceGestureWorkspace(changed, structural);
            return;
        }
        if (changed.equals(workspace)) {
            return;
        }
        // Every path into replaceWorkspace is a real user layout edit (move, resize, split,
        // visibility, detach, snap, or undo). It is therefore the point at which it is safe to
        // release the post-load-failure write guard.
        persistenceBlockedAfterLoadFailure = false;
        workspace = changed;
        viewportWorkspace = clampWorkspaceToViewport(changed);
        workspaceRevision++;
        projectionDirty = true;
        if (structural) {
            structureVersion++;
            host.requestRedraw();
        }
        ensureProjection();
    }

    private void replaceGestureWorkspace(DockWorkspace changed, boolean structural) {
        if (changed.equals(workspace)) {
            return;
        }
        workspace = changed;
        DockWorkspace nextViewport = clampGestureViewport(changed, structural);
        if (nextViewport.equals(viewportWorkspace)) {
            // Geometry is identical to what is already projected, so the live drag preview needs no
            // re-projection. This is the common case for a single root-translate gesture where every
            // pointer move stays in-bounds.
            gestureStructural |= structural;
            return;
        }
        viewportWorkspace = nextViewport;
        projectionDirty = true;
        gestureStructural |= structural;
        ensureProjection();
    }

    /**
     * Clamps a live gesture preview to the viewport. During a pure root-translate gesture the
     * recursive layout is unchanged (only one root's absolute position moved, already clamped by the
     * caller), so the full re-projection normally used to measure root minimum sizes is skipped and
     * the cached minimums from the last projection are reused. Any root lacking a cached minimum
     * falls back to the full clamp, as does every other gesture mode (resize, snap, splice).
     */
    private DockWorkspace clampGestureViewport(DockWorkspace changed, boolean structural) {
        boolean translateOnly = mode == Mode.DRAG_ROOT && !structural;
        if (translateOnly) {
            boolean allMeasured = true;
            for (FloatingRoot root : changed.roots()) {
                if (rootMinimum(root.rootId()).equals(DockSize.ZERO)) {
                    allMeasured = false;
                    break;
                }
            }
            if (allMeasured) {
                List<FloatingRoot> clampedRoots = new ArrayList<FloatingRoot>(changed.roots().size());
                for (FloatingRoot root : changed.roots()) {
                    clampedRoots.add(
                            root.withBounds(clampRectToViewport(root.bounds(), rootMinimum(root.rootId()))));
                }
                return changed.withRoots(clampedRoots);
            }
        }
        return clampWorkspaceToViewport(changed);
    }

    private DockWorkspace clampWorkspaceToViewport(DockWorkspace source) {
        // Sanitize persisted coordinates before the layout engine performs any additions.
        List<FloatingRoot> sanitizedRoots = new ArrayList<FloatingRoot>();
        for (FloatingRoot root : source.roots()) {
            sanitizedRoots.add(root.withBounds(clampRectToViewport(root.bounds(), DockSize.ZERO)));
        }
        DockWorkspace sanitized = source.withRoots(sanitizedRoots);
        LayoutProjection measured = layoutEngine.project(sanitized);

        List<FloatingRoot> clampedRoots = new ArrayList<FloatingRoot>();
        for (FloatingRoot root : sanitized.roots()) {
            DockSize minimum = minimumOf(measured, root.rootId());
            clampedRoots.add(root.withBounds(clampRectToViewport(root.bounds(), minimum)));
        }
        return source.withRoots(clampedRoots);
    }

    private static DockSize minimumOf(LayoutProjection measured, String rootId) {
        for (LayoutProjection.RootPlacement placement : measured.roots()) {
            if (placement.rootId().equals(rootId)) {
                return placement.minimumSize();
            }
        }
        return DockSize.ZERO;
    }

    private DockRect clampRectToViewport(DockRect bounds, DockSize minimum) {
        // 编辑态把画布缩到 chrome 之内：窗口标题条不会滑到侧栏/工具栏底下
        int minX = editingLayout ? editorInsetLeft : 0;
        int minY = editingLayout ? editorInsetTop : 0;
        int maxRight = Math.max(minX + 1, screenWidth - (editingLayout ? editorInsetRight : 0));
        int maxBottom = Math.max(minY + 1, screenHeight - (editingLayout ? editorInsetBottom : 0));
        int canvasWidth = Math.max(1, maxRight - minX);
        int canvasHeight = Math.max(1, maxBottom - minY);
        int width = Math.min(canvasWidth, Math.max(bounds.width(), minimum.width()));
        int height = Math.min(canvasHeight, Math.max(bounds.height(), minimum.height()));
        width = Math.max(1, width);
        height = Math.max(1, height);
        int maxX = Math.max(minX, maxRight - width);
        int maxY = Math.max(minY, maxBottom - height);
        int x = (int) Math.max((long) minX, Math.min((long) bounds.x(), (long) maxX));
        int y = (int) Math.max((long) minY, Math.min((long) bounds.y(), (long) maxY));
        return new DockRect(x, y, width, height);
    }

    /**
     * Sizes each spliced window to what its sections want. Layouts saved in the removed stretch or
     * compact modes sized those windows for that mode; a single panel keeps the size it was given.
     */
    private DockWorkspace fitCompositeRootsToShell(DockWorkspace source) {
        DockWorkspace next = source;
        for (FloatingRoot root : source.roots()) {
            int visibleLeaves = 0;
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                if (source.policyFor(leaf.moduleId()).visible()) {
                    visibleLeaves++;
                }
            }
            if (visibleLeaves < 2) {
                continue;
            }
            DockSize fitted = layoutEngine.preferredSize(next, root.content());
            if (fitted.isEmpty()) {
                continue;
            }
            next = editor.setRootBounds(next, root.rootId(), new DockRect(
                    root.bounds().x(),
                    root.bounds().y(),
                    Math.max(1, fitted.width()),
                    Math.max(1, fitted.height())));
        }
        return next;
    }

    /** Persists only a dirty workspace revision; repeated close/store hooks perform no file IO. */
    public void save() {
        if (persistence == null || workspace == null || persistedRevision == workspaceRevision
                || persistenceBlockedAfterLoadFailure || gestureCanonicalWorkspace != null) {
            return;
        }
        try {
            persistence.save(workspace);
            persistedRevision = workspaceRevision;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to write dock layout v{}", DockLayoutDto.CURRENT_VERSION, e);
        }
    }

    private void rememberUndoPoint(DockWorkspace snapshot) {
        if (gestureCanonicalWorkspace != null) {
            // Gesture previews must not enter the undo history; only the committed result does.
            return;
        }
        if (snapshot == null || snapshot.equals(workspace)) {
            return;
        }
        if (undoHistory.remember(snapshot)) {
            structureVersion++;
        }
    }

    private DockWorkspace takeInteractionStartWorkspace() {
        DockWorkspace pending = takePendingFocusWorkspace();
        return pending != null ? pending : workspace;
    }

    private DockWorkspace takePendingFocusWorkspace() {
        DockWorkspace pending = pendingFocusWorkspace;
        pendingFocusWorkspace = null;
        return pending;
    }

    private void commitInteraction(DockWorkspace beforeInteraction) {
        if (beforeInteraction != null && !beforeInteraction.equals(workspace)) {
            rememberUndoPoint(beforeInteraction);
        }
        save();
    }

    public boolean canUndoLayout() {
        return !undoHistory.isEmpty();
    }

    public void undoLayout() {
        DockWorkspace restore = undoHistory.pop();
        if (restore == null) {
            return;
        }
        persistenceBlockedAfterLoadFailure = false;
        structureVersion++;
        replaceWorkspace(restore, true);
        save();
    }

    /**
     * 外部（预设/测试/阶段 2 的加载器）直接替换整个工作区：校验结构、维持固定窗口不变量，
     * 记录一次 undo 并落盘。
     */
    public void applyWorkspace(DockWorkspace edited) {
        if (edited == null) {
            throw new NullPointerException("edited");
        }
        WorkspaceValidator.validateStrict(edited, catalog);
        if (!hasVisibleAnchored(edited)) {
            return;
        }
        // W4b：同 applyPresetWorkspace —— 快照要在替换之后再记（否则被 equals 守卫静默丢弃）
        DockWorkspace before = workspace;
        replaceWorkspace(edited, true);
        rememberUndoPoint(before);
        save();
    }

    // --- 模块可见性与窗口模式 -----------------------------------------------------

    public ModuleLayoutPolicy policyFor(ModulePanel panel) {
        if (workspace == null) {
            return ModuleLayoutPolicy.defaults();
        }
        return workspace.policyFor(panel.id());
    }

    public void setModulePolicy(ModulePanel panel, ModuleLayoutPolicy policy) {
        if (panel == null || policy == null) {
            throw new NullPointerException("panel and policy");
        }
        LeafNode leaf = leafForPanel(panel);
        ModuleLayoutPolicy current = workspace.policyFor(panel.id());
        DockWorkspace next = workspace;
        if (current.visible() != policy.visible()) {
            DockRect previousBounds = current.visible() ? projectionBounds(leaf.nodeId()) : null;
            next = editor.setLeafVisible(
                    next, leaf.nodeId(), policy.visible(), previousBounds, null, previousBounds);
        }
        Map<String, ModuleLayoutPolicy> policies = new LinkedHashMap<String, ModuleLayoutPolicy>(next.policies());
        policies.put(panel.id(), policy);
        next = next.withPolicies(policies);
        if (!hasVisibleAnchored(next)) {
            return;
        }
        replaceWorkspace(next, false);
    }

    public void toggleVisible(ModulePanel panel) {
        LeafNode leaf = leafForPanel(panel);
        boolean currentlyVisible = workspace.policyFor(leaf.moduleId()).visible();
        boolean nextVisible = !currentlyVisible;
        DockRect previousBounds = currentlyVisible ? projectionBounds(leaf.nodeId()) : null;
        rememberUndoPoint(workspace);
        replaceWorkspace(
                editor.setLeafVisible(
                        workspace, leaf.nodeId(), nextVisible, previousBounds, null, previousBounds),
                false);
        save();
        host.onModuleVisibilityChanged();
    }

    /** 隐藏一个模块（若已是隐藏状态则无操作）。 */
    public void hideModule(ModulePanel panel) {
        ensureProjection();
        LeafNode leaf = leafForPanel(panel);
        if (!workspace.policyFor(leaf.moduleId()).visible()) {
            return;
        }
        if (!panel.closable()) {
            return;
        }
        rememberUndoPoint(workspace);
        replaceWorkspace(
                editor.setLeafVisible(workspace, leaf.nodeId(), false, null, null, null),
                false);
        save();
        // 通知宿主同步可见集合（终端据此推送 panelMask）；否则服务端的旧 mask 会把这次隐藏还原
        host.onModuleVisibilityChanged();
    }

    /** 重新显示一个模块（并把它所在窗口提到最上层）。 */
    public void revealModule(ModulePanel panel) {
        ensureProjection();
        LeafNode leaf = leafForPanel(panel);
        DockWorkspace next = workspace;
        FloatingRoot holder = rootContainingNode(next, leaf.nodeId());
        if (holder != null && holder.hidden()) {
            next = editor.setRootHidden(next, holder.rootId(), false);
        }
        if (!workspace.policyFor(leaf.moduleId()).visible()) {
            next = editor.setLeafVisible(next, leaf.nodeId(), true, null, null, null);
        }
        FloatingRoot root = rootContainingNode(next, leaf.nodeId());
        if (root != null) {
            next = editor.raiseRoot(next, root.rootId());
        }
        if (next != workspace) {
            replaceWorkspace(next, false);
            host.onModuleVisibilityChanged();
        }
    }

    // ==================== 模块可见性（panelMask 的本地真相） ====================

    /** 当前可见的模块 id 集合（按注册顺序）。 */
    public Set<String> visibleModuleIds() {
        ensureProjection();
        Set<String> ids = new LinkedHashSet<String>();
        for (ModulePanel panel : panelsView) {
            if (workspace != null && workspace.policyFor(panel.id()).visible()) {
                ids.add(panel.id());
            }
        }
        return ids;
    }

    public boolean isModuleVisible(String moduleId) {
        ensureProjection();
        return workspace != null && panelsByModuleId.containsKey(moduleId)
                && workspace.policyFor(moduleId).visible();
    }

    /**
     * 设置单个模块的可见性。
     *
     * <p>不可关闭的模块（{@link ModulePanel#closable()} 为 false）只能被重新显示，不能被隐藏；
     * 隐藏会破坏"至少一个 anchored 窗口可见"时也按无操作处理。</p>
     *
     * @return 是否真的改动了布局
     */
    public boolean setModuleVisible(String moduleId, boolean visible) {
        ModulePanel panel = panelsByModuleId.get(moduleId);
        if (panel == null) {
            return false;
        }
        ensureProjection();
        LeafNode leaf = leafForPanel(panel);
        boolean currentlyVisible = workspace.policyFor(moduleId).visible();
        if (currentlyVisible == visible) {
            return false;
        }
        if (!visible) {
            if (!panel.closable()) {
                return false;
            }
            DockWorkspace candidate = editor.setLeafVisible(workspace, leaf.nodeId(), false, null, null, null);
            if (!hasVisibleAnchored(candidate)) {
                return false;
            }
        }
        DockRect previousBounds = currentlyVisible ? projectionBounds(leaf.nodeId()) : null;
        rememberUndoPoint(workspace);
        replaceWorkspace(
                editor.setLeafVisible(workspace, leaf.nodeId(), visible, previousBounds, null, previousBounds),
                false);
        save();
        host.onModuleVisibilityChanged();
        return true;
    }

    /**
     * 按给定集合批量对齐可见性（{@link #setModuleVisible} 的循环版本）。
     *
     * @return 是否至少改动了一项
     */
    public boolean applyVisibleModules(Collection<String> visibleModuleIds) {
        Set<String> desired = visibleModuleIds == null
                ? Collections.<String>emptySet() : new LinkedHashSet<String>(visibleModuleIds);
        boolean changed = false;
        for (String moduleId : new ArrayList<String>(panelsByModuleId.keySet())) {
            changed |= setModuleVisible(moduleId, desired.contains(moduleId));
        }
        return changed;
    }

    public void togglePinned(ModulePanel panel) {
        if (panel == null || !panel.pinVisible()) {
            return;
        }
        FloatingRoot root = rootOf(panel);
        if (!root.floating()) {
            return;
        }
        replaceWorkspace(editor.setRootMode(workspace, root.rootId(), true, !root.pinned()), true);
        save();
    }

    public boolean canToggleFloating(ModulePanel panel) {
        if (panel == null || workspace == null) {
            return false;
        }
        FloatingRoot root = rootOf(panel);
        return root.floating() || hasVisibleAnchored(editor.setRootMode(workspace, root.rootId(), true, false));
    }

    public void toggleFloating(ModulePanel panel) {
        setWindowFloating(panel, !rootOf(panel).floating());
    }

    public void setWindowFloating(ModulePanel panel, boolean floating) {
        if (!canToggleFloating(panel)) {
            return;
        }
        FloatingRoot root = rootOf(panel);
        if (root.floating() == floating) {
            return;
        }
        rememberUndoPoint(workspace);
        replaceWorkspace(editor.setRootMode(workspace, root.rootId(), floating, false), true);
        save();
    }

    public boolean isFloatingWindow(ModulePanel panel) {
        return panel != null && workspace != null && rootOf(panel).floating();
    }

    public boolean isJoinedWindow(ModulePanel panel) {
        return panel != null && workspace != null && rootOf(panel).content() instanceof SplitNode;
    }

    public boolean isFloatingUnpinned(ModulePanel panel) {
        FloatingRoot root = rootOf(panel);
        return root.floating() && !root.pinned();
    }

    public boolean isEffectivelyVisible(ModulePanel panel) {
        if (workspace == null || panel == null || panelsByModuleId.get(panel.id()) != panel) {
            return false;
        }
        ensureProjection();
        LeafNode leaf = leafForPanel(panel);
        return projection.isEffectivelyVisible(leaf.nodeId());
    }

    private FloatingRoot rootOf(ModulePanel panel) {
        return rootContainingNode(workspace, leafForPanel(panel).nodeId());
    }

    private LeafNode leafForPanel(ModulePanel panel) {
        if (panel == null || panelsByModuleId.get(panel.id()) != panel) {
            throw new IllegalArgumentException("panel is not registered with this dock");
        }
        for (FloatingRoot root : workspace.roots()) {
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                if (leaf.moduleId().equals(panel.id())) {
                    return leaf;
                }
            }
        }
        throw new IllegalStateException("workspace is missing module " + panel.id());
    }

    private static FloatingRoot rootContainingNode(DockWorkspace source, String nodeId) {
        for (FloatingRoot root : source.roots()) {
            if (LayoutTrees.contains(root.content(), nodeId)) {
                return root;
            }
        }
        throw new IllegalArgumentException("unknown node: " + nodeId);
    }

    // --- 交互：两段式抬升 ----------------------------------------------------------

    /**
     * Raises the visible root under the pointer before the host dispatches the click to controls
     * owned by that panel.
     *
     * <p>The change is deliberately left pending. If a child control consumes the click,
     * {@link #commitPanelInteraction()} records and saves the raise. If dock chrome starts a drag or
     * resize, {@link #mouseClicked(double, double, int)} takes over the same snapshot so the whole
     * gesture remains one undoable edit and one save.</p>
     */
    public void beginPanelInteraction(double mouseX, double mouseY) {
        ensureProjection();
        if (mode != Mode.NONE || pendingFocusWorkspace != null) {
            return;
        }
        FloatingRoot root = topRootAt(mouseX, mouseY, null);
        if (root == null || viewportWorkspace.roots().get(viewportWorkspace.roots().size() - 1)
                .rootId().equals(root.rootId())) {
            return;
        }
        DockWorkspace beforeInteraction = workspace;
        replaceWorkspace(editor.raiseRoot(workspace, root.rootId()), false);
        if (!beforeInteraction.equals(workspace)) {
            pendingFocusWorkspace = beforeInteraction;
        }
    }

    /** Commits a pre-dispatch root raise when no dock gesture took ownership of it. */
    public void commitPanelInteraction() {
        DockWorkspace beforeInteraction = takePendingFocusWorkspace();
        if (beforeInteraction != null) {
            commitInteraction(beforeInteraction);
        }
    }

    // --- 交互：点击 / 拖拽 / 释放 --------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ensureProjection();
        FloatingRoot root = topRootAt(mouseX, mouseY, null);
        if (root == null) {
            return false;
        }
        ModulePanel leaf = leafAt(root, mouseX, mouseY);
        if (button != 0) {
            // 非左键：只做内容路由。面板自持控件的右键语义（SlotFake 的清空/减半）靠这里进入，
            // 而窗口拖动/缩放/分隔条这些 dock 手势只认左键。
            return leaf != null && leaf.containsContent(mouseX, mouseY)
                    && leaf.mouseClicked(mouseX, mouseY, button);
        }

        DockWorkspace beforeInteraction = takeInteractionStartWorkspace();
        boolean rootRaised = !beforeInteraction.equals(workspace);
        if (!viewportWorkspace.roots().get(viewportWorkspace.roots().size() - 1).rootId().equals(root.rootId())) {
            replaceWorkspace(editor.raiseRoot(workspace, root.rootId()), false);
            rootRaised = rootRaised || !beforeInteraction.equals(workspace);
            root = rootById(root.rootId());
        }

        DividerHit divider = dividerAt(root, mouseX, mouseY);
        if (divider != null) {
            mode = Mode.RESIZE_DIVIDER;
            activeRootId = root.rootId();
            activeSplitId = divider.splitNodeId();
            activeDividerBounds = divider.bounds();
            resizeLastX = mouseX;
            resizeLastY = mouseY;
            gestureStartWorkspace = beforeInteraction;
            return true;
        }

        if (inRootResizeHandle(root.bounds(), mouseX, mouseY) && canResizeNode(root.content())) {
            mode = Mode.RESIZE_ROOT;
            activeRootId = root.rootId();
            grabOffsetX = mouseX - root.bounds().right();
            grabOffsetY = mouseY - root.bounds().bottom();
            gestureStartWorkspace = beforeInteraction;
            return true;
        }

        if (leaf != null && leaf.inCloseButton(mouseX, mouseY)) {
            // 常驻关闭按钮：隐藏该模块（repo/playerinv 不可关闭，closable() 已排除）
            hideModule(leaf);
            if (rootRaised) {
                commitInteraction(beforeInteraction);
            }
            return true;
        }
        if (leaf != null && leaf.inPinButton(mouseX, mouseY)) {
            togglePinned(leaf);
            if (rootRaised) {
                commitInteraction(beforeInteraction);
            }
            return true;
        }
        if (leaf != null && leaf.inFloatButton(mouseX, mouseY)) {
            toggleFloating(leaf);
            return true;
        }
        if (leaf != null && leaf.inTitleBar(mouseX, mouseY)) {
            LeafNode leafNode = leafNodeAt(root, mouseX, mouseY);
            if (leafNode == null) {
                if (rootRaised) {
                    commitInteraction(beforeInteraction);
                }
                return false;
            }
            activeLeafNodeId = leafNode.nodeId();
            if (!policyFor(leafNode).movable()) {
                if (rootRaised) {
                    commitInteraction(beforeInteraction);
                }
                return false;
            }
            pressX = mouseX;
            pressY = mouseY;
            boolean controlDown = host.controlDown();
            boolean detachLeaf = shouldDetachJoinedLeaf(host.altDown(), controlDown);
            if (controlDown) {
                // Ctrl+drag moves the whole spliced window instead of taking a section out.
                mode = Mode.DRAG_ROOT;
                activeRootId = root.rootId();
                grabOffsetX = mouseX - root.bounds().x();
                grabOffsetY = mouseY - root.bounds().y();
            } else if (root.content().nodeId().equals(leafNode.nodeId())) {
                // A standalone leaf is itself the floating root, so retain the fast path that drags
                // that root immediately.
                mode = Mode.DRAG_ROOT;
                activeRootId = root.rootId();
                grabOffsetX = mouseX - root.bounds().x();
                grabOffsetY = mouseY - root.bounds().y();
            } else if (!detachLeaf) {
                // Every joined window moves as one unit unless Alt is held.
                mode = Mode.DRAG_ROOT;
                activeRootId = root.rootId();
                grabOffsetX = mouseX - root.bounds().x();
                grabOffsetY = mouseY - root.bounds().y();
            } else {
                // In a composite root, wait for an actual drag before detaching the clicked leaf.
                // A click without movement must not mutate the layout tree.
                mode = Mode.PENDING_LEAF_DRAG;
                activeRootId = root.rootId();
                DockRect leafBounds = projection.boundsFor(leafNode.nodeId()).orElse(root.bounds());
                grabOffsetX = mouseX - leafBounds.x();
                grabOffsetY = mouseY - leafBounds.y();
            }
            dropCandidate = null;
            dragHoverBounds = null;
            gestureStartWorkspace = beforeInteraction;
            return true;
        }
        if (leaf != null && leaf.containsContent(mouseX, mouseY)) {
            // 内容区：先问面板自持控件（滚动条），再问通用内容点击。消费了就吞掉，
            // 既不穿透到下层窗口，也不会落到 AE2 的槽位逻辑上。
            if (leaf.scrollbarPressed(mouseX, mouseY)) {
                scrollbarDragPanel = leaf;
                if (rootRaised) {
                    commitInteraction(beforeInteraction);
                }
                return true;
            }
            if (leaf.mouseClicked(mouseX, mouseY, button)) {
                if (rootRaised) {
                    commitInteraction(beforeInteraction);
                }
                return true;
            }
        }
        if (rootRaised) {
            // A plain content click can finish without entering a dock gesture. Raising the root is
            // still a persistent layout edit and must be independently undoable.
            commitInteraction(beforeInteraction);
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (scrollbarDragPanel != null) {
            // 面板自持滚动条优先：拖滚动条期间不进入任何 root 手势
            return scrollbarDragPanel.scrollbarDragged(mouseX, mouseY);
        }
        if (mode == Mode.NONE) {
            return false;
        }
        if (gestureCanonicalWorkspace == null) {
            gestureCanonicalWorkspace = workspace;
            gestureCanonicalViewport = viewportWorkspace;
            gestureStructural = false;
        }
        ensureProjection();
        if (mode == Mode.PENDING_LEAF_DRAG) {
            double dx = mouseX - pressX;
            double dy = mouseY - pressY;
            if (dx * dx + dy * dy < (double) LEAF_DRAG_THRESHOLD * LEAF_DRAG_THRESHOLD) {
                return true;
            }
            if (!detachPendingLeaf()) {
                // Detach can become invalid if the projected leaf disappears between press and drag.
                // A root may already have been raised at press time, so finish that edit instead of
                // silently leaving it unsaved and without an undo point.
                finishGesture();
                return true;
            }
            mode = Mode.DRAG_ROOT;
        }
        if (mode == Mode.DRAG_ROOT) {
            FloatingRoot root = rootById(activeRootId);
            DockRect bounds = root.bounds();
            DockRect moved = clampRectToViewport(new DockRect(
                    (int) Math.round(mouseX - grabOffsetX),
                    (int) Math.round(mouseY - grabOffsetY),
                    bounds.width(),
                    bounds.height()), DockSize.ZERO);
            replaceWorkspace(editor.setRootBounds(workspace, root.rootId(), moved), false);
            updateDropCandidate(mouseX, mouseY);
            return true;
        }
        if (mode == Mode.RESIZE_ROOT) {
            FloatingRoot root = rootById(activeRootId);
            DockSize minimum = rootMinimum(root.rootId());
            int width = (int) Math.round(mouseX - grabOffsetX - root.bounds().x());
            int height = (int) Math.round(mouseY - grabOffsetY - root.bounds().y());
            DockRect resized = clampRectToViewport(new DockRect(
                    root.bounds().x(),
                    root.bounds().y(),
                    Math.max(1, width),
                    Math.max(1, height)), minimum);
            replaceWorkspace(editor.setRootBounds(workspace, root.rootId(), resized), false);
            return true;
        }
        if (mode == Mode.RESIZE_DIVIDER) {
            resizeDivider(mouseX, mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollbarDragPanel != null) {
            scrollbarDragPanel.scrollbarReleased();
            scrollbarDragPanel = null;
            return true;
        }
        if (button != 0 || mode == Mode.NONE) {
            return false;
        }
        if (mode == Mode.PENDING_LEAF_DRAG) {
            // A title click that never crossed the drag threshold does not detach the leaf, but it
            // may still have raised a lower root. Commit that Z-order change as one undoable edit.
            finishGesture();
            return true;
        }
        if (mode == Mode.DRAG_ROOT) {
            if (dropCandidate != null) {
                executeDrop(dropCandidate);
            } else {
                snapActiveRoot();
            }
        }
        finishGesture();
        return true;
    }

    /**
     * 滚轮：路由给光标下<strong>最上层且命中内容区</strong>的叶子面板（MEST 由屏幕层做同样的事情）。
     *
     * <p>只认内容区：标题栏上的滚轮属于窗口 chrome，不该被列表吞掉。</p>
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        ModulePanel leaf = topLeafAt(mouseX, mouseY);
        if (leaf == null || !leaf.containsContent(mouseX, mouseY)) {
            return false;
        }
        return leaf.mouseScrolled(mouseX, mouseY, scrollY);
    }

    /** 是否有面板正在拖拽自己的滚动条（宿主可据此跳过 AE2 的槽位拖拽逻辑）。 */
    public boolean isScrollbarDragging() {
        return scrollbarDragPanel != null;
    }

    /**
     * 键盘：MEST 此处只处理内容编辑态的 Esc（本阶段不做内容微调），故当前无绑定；宿主仍应转发
     * {@code keyTyped} 的 keyCode 到这里，以便后续阶段加入快捷键。
     */
    public boolean keyPressed(int keyCode) {
        return false;
    }

    private void finishGesture() {
        DockWorkspace beforeInteraction = gestureStartWorkspace;
        DockWorkspace preview = workspace;
        DockWorkspace canonical = gestureCanonicalWorkspace;
        boolean structural = gestureStructural;
        resetGestureState();
        if (canonical != null && !preview.equals(canonical)) {
            persistenceBlockedAfterLoadFailure = false;
            workspace = preview;
            workspaceRevision++;
            projectionDirty = true;
            if (structural) {
                structureVersion++;
            }
            ensureProjection();
        }
        commitInteraction(beforeInteraction);
    }

    /** Commits a live pointer gesture exactly once; safe to call during screen removal. */
    public void finishPendingGesture() {
        if (scrollbarDragPanel != null) {
            scrollbarDragPanel.scrollbarReleased();
            scrollbarDragPanel = null;
        }
        if (mode != Mode.NONE || gestureCanonicalWorkspace != null) {
            finishGesture();
        } else {
            commitPanelInteraction();
        }
    }

    private void resetGestureState() {
        mode = Mode.NONE;
        activeRootId = null;
        activeLeafNodeId = null;
        activeSplitId = null;
        activeDividerBounds = null;
        dropCandidate = null;
        dragHoverBounds = null;
        gestureStartWorkspace = null;
        gestureCanonicalWorkspace = null;
        gestureCanonicalViewport = null;
        gestureStructural = false;
        pendingFocusWorkspace = null;
    }

    /** A joined leaf detaches only with Alt held; Ctrl drags the whole window instead. */
    static boolean shouldDetachJoinedLeaf(boolean altDown, boolean controlDown) {
        return !controlDown && altDown;
    }

    private boolean detachPendingLeaf() {
        if (!host.altDown() || activeRootId == null || activeLeafNodeId == null) {
            return false;
        }
        FloatingRoot sourceRoot = rootById(activeRootId);
        LayoutNode node = LayoutTrees.find(sourceRoot.content(), activeLeafNodeId).orElse(null);
        if (!(node instanceof LeafNode) || sourceRoot.content().nodeId().equals(activeLeafNodeId)) {
            return false;
        }
        DockRect leafBounds = projection.boundsFor(activeLeafNodeId).orElse(null);
        if (leafBounds == null) {
            return false;
        }
        String newRootId = "root:" + NodeIds.random();
        DockWorkspace changed = detachAndRestore(workspace, activeLeafNodeId, newRootId);
        changed = editor.raiseRoot(changed, newRootId);
        replaceWorkspace(changed, true);
        activeRootId = newRootId;
        dragHoverBounds = null;
        return true;
    }

    /**
     * Splits {@code nodeId} into its own floating root and restores both windows to the standalone
     * sizes captured at splice time. Without a stored size, remaining content keeps its projected
     * slice instead of the combined host window.
     */
    private DockWorkspace detachAndRestore(DockWorkspace source, String nodeId, String newRootId) {
        FloatingRoot sourceRoot = rootContainingNode(source, nodeId);
        DockRect detachedProjected = projection.boundsFor(nodeId).orElse(sourceRoot.bounds());
        DockRect detachedBounds = source.restoredBounds(nodeId, detachedProjected);
        DockWorkspace changed = editor.detach(source, nodeId, newRootId, detachedBounds);
        FloatingRoot remainingRoot = null;
        for (FloatingRoot root : changed.roots()) {
            if (root.rootId().equals(sourceRoot.rootId())) {
                remainingRoot = root;
                break;
            }
        }
        if (remainingRoot == null) {
            return changed;
        }
        DockRect remainingBounds = remainingBoundsAfterDetach(
                source, sourceRoot, remainingRoot.content(), detachedProjected);
        if (!remainingBounds.equals(remainingRoot.bounds())) {
            changed = editor.setRootBounds(changed, remainingRoot.rootId(), remainingBounds);
        }
        return changed;
    }

    private DockRect remainingBoundsAfterDetach(
            DockWorkspace source,
            FloatingRoot sourceRoot,
            LayoutNode remaining,
            DockRect detachedProjected) {
        DockRect remainingProjected = projection.boundsFor(remaining.nodeId()).orElse(sourceRoot.bounds());
        if (source.restoreSizes().containsKey(remaining.nodeId())) {
            return source.restoredBounds(remaining.nodeId(), remainingProjected);
        }
        if (remainingProjected.equals(sourceRoot.bounds())) {
            DockRect shrunk = excludeSlice(sourceRoot.bounds(), detachedProjected);
            if (!shrunk.equals(sourceRoot.bounds()) && shrunk.width() > 0 && shrunk.height() > 0) {
                return shrunk;
            }
        }
        return remainingProjected;
    }

    /** Drops a full-width or full-height detached slice from {@code root} along the split axis. */
    private static DockRect excludeSlice(DockRect root, DockRect slice) {
        if (slice == null || slice.width() <= 0 || slice.height() <= 0) {
            return root;
        }
        boolean spansHeight = slice.y() <= root.y() && slice.bottom() >= root.bottom();
        boolean spansWidth = slice.x() <= root.x() && slice.right() >= root.right();
        if (spansHeight && slice.width() < root.width()) {
            if (slice.x() <= root.x()) {
                int width = root.right() - slice.right();
                if (width > 0) {
                    return new DockRect(slice.right(), root.y(), width, root.height());
                }
            } else if (slice.right() >= root.right()) {
                int width = slice.x() - root.x();
                if (width > 0) {
                    return new DockRect(root.x(), root.y(), width, root.height());
                }
            }
        }
        if (spansWidth && slice.height() < root.height()) {
            if (slice.y() <= root.y()) {
                int height = root.bottom() - slice.bottom();
                if (height > 0) {
                    return new DockRect(root.x(), slice.bottom(), root.width(), height);
                }
            } else if (slice.bottom() >= root.bottom()) {
                int height = slice.y() - root.y();
                if (height > 0) {
                    return new DockRect(root.x(), root.y(), root.width(), height);
                }
            }
        }
        return root;
    }

    // --- 吸附拼接 ----------------------------------------------------------------

    private void updateDropCandidate(double mouseX, double mouseY) {
        FloatingRoot targetRoot = topRootAt(mouseX, mouseY, activeRootId);
        if (targetRoot == null) {
            dropCandidate = null;
            dragHoverBounds = null;
            return;
        }
        LeafNode targetLeaf = leafNodeAt(targetRoot, mouseX, mouseY);
        if (targetLeaf == null) {
            dropCandidate = null;
            dragHoverBounds = null;
            return;
        }
        LayoutProjection.LeafPlacement targetPlacement =
                projection.visibleLeaf(targetLeaf.nodeId()).orElse(null);
        if (targetPlacement == null) {
            dropCandidate = null;
            dragHoverBounds = null;
            return;
        }
        DockRect bounds = targetPlacement.bounds();
        dragHoverBounds = bounds;
        if (!draggedCanSplice() || !moduleCanSplice(targetLeaf.moduleId())) {
            dropCandidate = null;
            return;
        }
        DockEdge edge = dropEdge(bounds, mouseX, mouseY);
        dropCandidate = edge == null ? null : new DropCandidate(targetLeaf.nodeId(), edge, dropBounds(bounds, edge));
    }

    private boolean moduleCanSplice(String moduleId) {
        ModulePanel panel = panelsByModuleId.get(moduleId);
        return panel == null || panel.canSplice();
    }

    private boolean draggedCanSplice() {
        FloatingRoot dragged = rootById(activeRootId);
        if (dragged == null) {
            return true;
        }
        for (LeafNode leaf : LayoutTrees.leaves(dragged.content())) {
            if (!moduleCanSplice(leaf.moduleId())) {
                return false;
            }
        }
        return true;
    }

    private void executeDrop(DropCandidate candidate) {
        FloatingRoot draggedRoot = rootById(activeRootId);
        // The candidate and its highlight were computed from the transient viewport projection. Use
        // the matching viewport root for geometry as well; mixing it with canonical (possibly
        // larger-screen) bounds makes a drop jump when the window is temporarily smaller.
        FloatingRoot targetViewportRoot = rootContainingNode(viewportWorkspace, candidate.targetNodeId());
        LayoutNode dragged = draggedRoot.content();
        DockRect targetBounds = projection.boundsFor(candidate.targetNodeId()).orElseThrow(
                () -> new IllegalStateException("dropped target is not projected: " + candidate.targetNodeId()));
        int draggedExtent = candidate.edge().axis().extent(draggedRoot.bounds());
        int targetExtent = candidate.edge().axis().extent(targetBounds);
        double draggedFraction = (double) draggedExtent / Math.max(1, draggedExtent + targetExtent);
        double ratio = candidate.edge().draggedFirst() ? draggedFraction : 1.0 - draggedFraction;

        String splitNodeId = "split:" + NodeIds.random();
        Map<String, DockSize> restore = new LinkedHashMap<String, DockSize>(workspace.restoreSizes());
        for (LeafNode leaf : LayoutTrees.leaves(targetViewportRoot.content())) {
            LayoutProjection.LeafPlacement visible = projection.visibleLeaf(leaf.nodeId()).orElse(null);
            if (visible != null) {
                restore.put(leaf.nodeId(), visible.bounds().size());
            }
        }
        for (LeafNode leaf : LayoutTrees.leaves(draggedRoot.content())) {
            LayoutProjection.LeafPlacement visible = projection.visibleLeaf(leaf.nodeId()).orElse(null);
            if (visible != null) {
                restore.put(leaf.nodeId(), visible.bounds().size());
            }
        }
        DockWorkspace joined = editor.insertSplit(
                workspace.withRestoreSizes(restore),
                dragged.nodeId(),
                candidate.targetNodeId(),
                candidate.edge(),
                splitNodeId,
                ratio);
        String joinedRootId = rootContainingNode(joined, splitNodeId).rootId();
        DockRect expandedBounds = expandedDockBounds(
                targetViewportRoot.bounds(), draggedRoot.bounds(), candidate.edge());
        LayoutNode joinedContent = rootContainingNode(joined, splitNodeId).content();
        DockSize fitted = layoutEngine.preferredSize(joined, joinedContent);
        expandedBounds = clampRectToViewport(new DockRect(
                expandedBounds.x(), expandedBounds.y(),
                Math.max(1, fitted.width()), Math.max(1, fitted.height())), DockSize.ZERO);
        joined = editor.setRootBounds(joined, joinedRootId, expandedBounds);
        joined = editor.raiseRoot(joined, joinedRootId);
        replaceWorkspace(joined, true);
    }

    private DockRect expandedDockBounds(DockRect target, DockRect dragged, DockEdge edge) {
        int width = target.width();
        int height = target.height();
        int x = target.x();
        int y = target.y();
        if (edge.axis() == DockAxis.HORIZONTAL) {
            width = (int) Math.min(
                    (long) screenWidth,
                    (long) target.width() + dragged.width() + DIVIDER_THICKNESS);
            height = Math.min(screenHeight, Math.max(target.height(), dragged.height()));
            if (edge == DockEdge.LEFT) {
                x = Math.max(0, target.right() - width);
            }
        } else {
            width = Math.min(screenWidth, Math.max(target.width(), dragged.width()));
            height = (int) Math.min(
                    (long) screenHeight,
                    (long) target.height() + dragged.height() + DIVIDER_THICKNESS);
            if (edge == DockEdge.TOP) {
                y = Math.max(0, target.bottom() - height);
            }
        }
        return clampRectToViewport(new DockRect(x, y, width, height), DockSize.ZERO);
    }

    private void snapActiveRoot() {
        FloatingRoot active = rootById(activeRootId);
        DockRect bounds = active.bounds();
        List<Integer> xCandidates = new ArrayList<Integer>();
        List<Integer> yCandidates = new ArrayList<Integer>();
        xCandidates.add(0);
        xCandidates.add(screenWidth - bounds.width());
        yCandidates.add(0);
        yCandidates.add(screenHeight - bounds.height());

        for (FloatingRoot target : viewportWorkspace.roots()) {
            if (target.rootId().equals(active.rootId()) || !rootEffectivelyVisible(target.rootId())) {
                continue;
            }
            xCandidates.add(target.bounds().x());
            xCandidates.add(target.bounds().right());
            xCandidates.add(target.bounds().x() - bounds.width());
            xCandidates.add(target.bounds().right() - bounds.width());
            yCandidates.add(target.bounds().y());
            yCandidates.add(target.bounds().bottom());
            yCandidates.add(target.bounds().y() - bounds.height());
            yCandidates.add(target.bounds().bottom() - bounds.height());
        }
        int snappedX = closestSnap(bounds.x(), xCandidates);
        int snappedY = closestSnap(bounds.y(), yCandidates);
        DockRect snapped = clampRectToViewport(
                new DockRect(snappedX, snappedY, bounds.width(), bounds.height()),
                rootMinimum(active.rootId()));
        replaceWorkspace(editor.setRootBounds(workspace, active.rootId(), snapped), false);
    }

    private static int closestSnap(int current, List<Integer> candidates) {
        int result = current;
        int distance = SNAP_DISTANCE + 1;
        for (int candidate : candidates) {
            int candidateDistance = Math.abs(candidate - current);
            if (candidateDistance < distance) {
                result = candidate;
                distance = candidateDistance;
            }
        }
        return result;
    }

    // --- 分隔条与根缩放 -----------------------------------------------------------

    private void resizeDivider(double mouseX, double mouseY) {
        LayoutNode node = findNode(activeSplitId);
        if (!(node instanceof SplitNode)) {
            return;
        }
        resizeShellDivider((SplitNode) node, mouseX, mouseY);
    }

    private void resizeShellDivider(SplitNode split, double mouseX, double mouseY) {
        DockRect seam = activeDividerBounds;
        if (seam == null) {
            return;
        }
        int delta = split.axis() == DockAxis.VERTICAL
                ? (int) Math.round(mouseY - resizeLastY)
                : (int) Math.round(mouseX - resizeLastX);
        List<LeafNode> firstTouch = leavesOnSeam(split.first(), seam, split.axis(), true);
        List<LeafNode> secondTouch = leavesOnSeam(split.second(), seam, split.axis(), false);
        if (firstTouch.isEmpty() && secondTouch.isEmpty()) {
            return;
        }
        for (LeafNode leaf : firstTouch) {
            delta = Math.max(delta, minExtent(leaf, split.axis()) - axisExtent(leaf, split.axis()));
        }
        for (LeafNode leaf : secondTouch) {
            delta = Math.min(delta, axisExtent(leaf, split.axis()) - minExtent(leaf, split.axis()));
        }
        if (delta == 0) {
            return;
        }
        FloatingRoot root = rootById(activeRootId);
        Map<String, DockSize> restore = new LinkedHashMap<String, DockSize>(workspace.restoreSizes());
        if (root != null) {
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                LayoutProjection.LeafPlacement visible = projection.visibleLeaf(leaf.nodeId()).orElse(null);
                if (visible != null) {
                    restore.put(leaf.nodeId(), visible.bounds().size());
                }
            }
        }
        applySeamDelta(restore, firstTouch, split.axis(), delta);
        applySeamDelta(restore, secondTouch, split.axis(), -delta);
        DockWorkspace next = workspace.withRestoreSizes(restore);

        int firstMain = subtreeMainAfterDelta(split.first(), firstTouch, split.axis(), delta);
        int secondMain = subtreeMainAfterDelta(split.second(), secondTouch, split.axis(), -delta);
        int available = Math.max(1, firstMain + secondMain);
        next = editor.setSplitRatio(next, split.nodeId(),
                clampStoredRatio(split, available, (double) firstMain / available));

        if (root != null) {
            DockSize fitted = layoutEngine.preferredSize(next, root.content());
            next = editor.setRootBounds(next, root.rootId(), clampRectToViewport(
                    new DockRect(
                            root.bounds().x(), root.bounds().y(),
                            Math.max(1, fitted.width()), Math.max(1, fitted.height())),
                    rootMinimum(root.rootId())));
        }
        if (!next.equals(workspace)) {
            replaceWorkspace(next, false);
            resizeLastX = mouseX;
            resizeLastY = mouseY;
            activeDividerBounds = closestDividerBounds(split.nodeId(), mouseX, mouseY);
        }
    }

    private DockRect closestDividerBounds(String splitNodeId, double mouseX, double mouseY) {
        DockRect closest = null;
        double best = Double.MAX_VALUE;
        for (LayoutProjection.DividerPlacement divider : projection.dividers()) {
            if (!divider.splitNodeId().equals(splitNodeId)) {
                continue;
            }
            DockRect bounds = divider.bounds();
            double dx = mouseX - (bounds.x() + bounds.width() / 2.0);
            double dy = mouseY - (bounds.y() + bounds.height() / 2.0);
            double distance = dx * dx + dy * dy;
            if (distance < best) {
                closest = bounds;
                best = distance;
            }
        }
        return closest;
    }

    private void applySeamDelta(
            Map<String, DockSize> restore, List<LeafNode> leaves, DockAxis axis, int delta) {
        for (LeafNode leaf : leaves) {
            DockRect bounds = projection.boundsFor(leaf.nodeId()).orElse(null);
            if (bounds == null) {
                continue;
            }
            int width = axis == DockAxis.HORIZONTAL ? Math.max(1, bounds.width() + delta) : bounds.width();
            int height = axis == DockAxis.VERTICAL ? Math.max(1, bounds.height() + delta) : bounds.height();
            restore.put(leaf.nodeId(), new DockSize(width, height));
        }
    }

    private int subtreeMainAfterDelta(LayoutNode side, List<LeafNode> touching, DockAxis axis, int delta) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        boolean any = false;
        for (LeafNode leaf : LayoutTrees.leaves(side)) {
            DockRect bounds = projection.boundsFor(leaf.nodeId()).orElse(null);
            if (bounds == null) {
                continue;
            }
            any = true;
            int start;
            int end;
            if (axis == DockAxis.VERTICAL) {
                start = bounds.y();
                end = bounds.bottom() + (containsLeaf(touching, leaf) ? delta : 0);
            } else {
                start = bounds.x();
                end = bounds.right() + (containsLeaf(touching, leaf) ? delta : 0);
            }
            min = Math.min(min, start);
            max = Math.max(max, end);
        }
        return any ? Math.max(1, max - min) : 1;
    }

    private static boolean containsLeaf(List<LeafNode> leaves, LeafNode leaf) {
        for (LeafNode candidate : leaves) {
            if (candidate.nodeId().equals(leaf.nodeId())) {
                return true;
            }
        }
        return false;
    }

    private int axisExtent(LeafNode leaf, DockAxis axis) {
        DockRect bounds = projection.boundsFor(leaf.nodeId()).orElse(null);
        if (bounds == null) {
            return 1;
        }
        return axis == DockAxis.VERTICAL ? bounds.height() : bounds.width();
    }

    private int minExtent(LeafNode leaf, DockAxis axis) {
        ModulePanel panel = panelsByModuleId.get(leaf.moduleId());
        if (panel == null) {
            return 1;
        }
        return axis == DockAxis.VERTICAL ? panel.minHeight() : panel.minWidth();
    }

    private List<LeafNode> leavesOnSeam(LayoutNode side, DockRect seam, DockAxis axis, boolean firstSide) {
        List<LeafNode> touching = new ArrayList<LeafNode>();
        for (LeafNode leaf : LayoutTrees.leaves(side)) {
            DockRect bounds = projection.boundsFor(leaf.nodeId()).orElse(null);
            if (bounds == null) {
                continue;
            }
            boolean onEdge = axis == DockAxis.VERTICAL
                    ? (firstSide
                            ? Math.abs(bounds.bottom() - seam.y()) <= 1
                            : Math.abs(bounds.y() - seam.y()) <= 1)
                        && bounds.x() < seam.right() && bounds.right() > seam.x()
                    : (firstSide
                            ? Math.abs(bounds.right() - seam.x()) <= 1
                            : Math.abs(bounds.x() - seam.x()) <= 1)
                        && bounds.y() < seam.bottom() && bounds.bottom() > seam.y();
            if (onEdge) {
                touching.add(leaf);
            }
        }
        return touching;
    }

    private double clampStoredRatio(SplitNode split, int available, double ratio) {
        DockSize firstMinimum = projection.minimumSizes().getOrDefault(split.first().nodeId(), DockSize.ZERO);
        DockSize secondMinimum = projection.minimumSizes().getOrDefault(split.second().nodeId(), DockSize.ZERO);
        int firstExtent = split.axis().extent(firstMinimum);
        int secondExtent = split.axis().extent(secondMinimum);
        double minimumRatio = 0.05;
        double maximumRatio = 0.95;
        if ((long) firstExtent + secondExtent <= available) {
            minimumRatio = Math.max(minimumRatio, (double) firstExtent / available);
            maximumRatio = Math.min(maximumRatio, 1.0 - (double) secondExtent / available);
        }
        return Math.max(minimumRatio, Math.min(maximumRatio, ratio));
    }

    // --- 命中测试与查询 -----------------------------------------------------------

    /** 分隔条命中（供宿主画 hover 高亮/改光标）；无命中返回 null。 */
    public static final class DividerHit {
        private final String splitNodeId;
        private final DockAxis axis;
        private final DockRect bounds;

        DividerHit(String splitNodeId, DockAxis axis, DockRect bounds) {
            this.splitNodeId = splitNodeId;
            this.axis = axis;
            this.bounds = bounds;
        }

        public String splitNodeId() {
            return splitNodeId;
        }

        public DockAxis axis() {
            return axis;
        }

        public DockRect bounds() {
            return bounds;
        }

        @Override
        public String toString() {
            return "DividerHit[splitNodeId=" + splitNodeId + ", axis=" + axis + ", bounds=" + bounds + "]";
        }
    }

    /** 命中光标下的分隔条（越界 chrome 不做，因此不需要额外命中区）。 */
    public DividerHit dividerAt(double mouseX, double mouseY) {
        ensureProjection();
        FloatingRoot root = topRootAt(mouseX, mouseY, null);
        return root == null ? null : dividerAt(root, mouseX, mouseY);
    }

    private DividerHit dividerAt(FloatingRoot root, double mouseX, double mouseY) {
        for (LayoutProjection.DividerPlacement divider : projection.dividers()) {
            if (!LayoutTrees.contains(root.content(), divider.splitNodeId())) {
                continue;
            }
            DockRect bounds = divider.bounds();
            if (bounds == null || bounds.width() <= 0 && bounds.height() <= 0) {
                continue;
            }
            DockRect hit = new DockRect(
                    bounds.x() - DIVIDER_HIT_PADDING,
                    bounds.y() - DIVIDER_HIT_PADDING,
                    Math.max(1, bounds.width()) + 2 * DIVIDER_HIT_PADDING,
                    Math.max(1, bounds.height()) + 2 * DIVIDER_HIT_PADDING);
            if (contains(hit, mouseX, mouseY)) {
                return new DividerHit(divider.splitNodeId(), divider.axis(), bounds);
            }
        }
        return null;
    }

    /**
     * Returns a leaf in the topmost floating root at the point. On a divider, returns a representative
     * leaf from that root so the host still blocks click-through.
     */
    public ModulePanel topPanelAt(double mouseX, double mouseY) {
        ensureProjection();
        FloatingRoot root = topRootAt(mouseX, mouseY, null);
        if (root == null) {
            return null;
        }
        ModulePanel exact = leafAt(root, mouseX, mouseY);
        if (exact != null) {
            return exact;
        }
        for (LeafNode leaf : leavesOf(root)) {
            if (projection.visibleLeaf(leaf.nodeId()).isPresent()) {
                return panelsByModuleId.get(leaf.moduleId());
            }
        }
        return null;
    }

    public ModulePanel topLeafAt(double mouseX, double mouseY) {
        ensureProjection();
        FloatingRoot root = topRootAt(mouseX, mouseY, null);
        return root == null ? null : leafAt(root, mouseX, mouseY);
    }

    /** 光标下最上层叶子的标题栏 tooltip（宿主用 {@code drawHoveringText} 画出来）。 */
    public String titleBarTooltipAt(double mouseX, double mouseY) {
        ModulePanel leaf = topLeafAt(mouseX, mouseY);
        return leaf == null ? null : leaf.titleBarTooltip(mouseX, mouseY);
    }

    /** Pose-stack Z for the vanilla slot highlight overlay in the same root as the slot. */
    public float slotHighlightZ(ModulePanel panel) {
        return paintZOf(panel) + OVERLAY_Z;
    }

    /**
     * 面板所在根的绘制 z。
     *
     * <p>宿主在 {@code dock.render} 之外自绘某个面板的内容（例如 repo 的搜索框）时，必须把这一层
     * 平移过去：1.12.2 的 GUI 开着深度测试，z 比本窗口小的绘制会被本窗口背景挡掉。</p>
     */
    public float paintZOf(ModulePanel panel) {
        if (panel == null) {
            return 0.0F;
        }
        ensureProjection();
        LeafNode leaf = leafForPanel(panel);
        FloatingRoot host = rootContainingNode(viewportWorkspace, leaf.nodeId());
        return host == null ? 0.0F : paintZOf(host);
    }

    /** 根的 chrome z（= 重叠带号 × {@link #ROOT_LAYER_Z}）。 */
    public float paintZOf(FloatingRoot root) {
        return zBandOf(root) * ROOT_LAYER_Z;
    }

    /**
     * 根所在的"重叠带"：不重叠的窗口共用一条带，互相重叠的窗口按绘制顺序逐带递增。
     *
     * <p>带号 = 绘制顺序里"与它重叠且画得更早"的根的最大带号 + 1，因此对任意一对重叠的根，
     * 后画的带号一定更大 —— 与 {@link #paintOrderRoots()} 的 z 序（以及点击命中的最上层判定）
     * 完全一致，不会出现"看着在上面、点着却在下面"。</p>
     *
     * <p>用重叠关系而不是"绘制序号"分配带，是为了不浪费 z 预算：1.12.2 的 GUI 只有 ±1000，
     * 而槽位内容自带 ~250 的内部偏移（见 {@link #ROOT_LAYER_Z}），按序号分配的话 4 个窗口就超界；
     * 按重叠关系分配时，平铺的默认布局只占 1 条带。</p>
     */
    private int zBandOf(FloatingRoot target) {
        Map<String, Integer> bands = paintBandCache;
        if (bands == null) {
            bands = new HashMap<String, Integer>();
            List<FloatingRoot> order = paintOrderRoots();
            for (int i = 0; i < order.size(); i++) {
                FloatingRoot root = order.get(i);
                int band = 0;
                for (int j = 0; j < i; j++) {
                    FloatingRoot earlier = order.get(j);
                    Integer earlierBand = bands.get(earlier.rootId());
                    if (earlierBand == null || !intersects(earlier.bounds(), root.bounds())) {
                        continue;
                    }
                    band = Math.max(band, earlierBand + 1);
                }
                bands.put(root.rootId(), Math.min(MAX_Z_BAND, band));
            }
            paintBandCache = bands;
        }
        Integer band = bands.get(target.rootId());
        return band == null ? 0 : band;
    }

    /**
     * Whether an area owned by a panel is fully inside its visible leaf and not covered by any higher
     * floating root.
     *
     * <p>阶段 2 接入原生控件（物品格、滚动条等）时，凡是渲染在 dock 之后的控件都要先问这里，
     * 否则它会穿透到上一层窗口之上。</p>
     */
    public boolean isAreaUnobscured(ModulePanel panel, DockRect area) {
        ensureProjection();
        LeafNode leaf = leafForPanel(panel);
        LayoutProjection.LeafPlacement placement = projection.visibleLeaf(leaf.nodeId()).orElse(null);
        if (placement == null || !contains(placement.bounds(), area)) {
            return false;
        }
        String ownerRootId = rootContainingNode(viewportWorkspace, leaf.nodeId()).rootId();
        return isAreaUnobscured(viewportWorkspace, ownerRootId, area);
    }

    /**
     * Whether a press at the point may reach {@code panel}: no root at all covers the point, or the
     * topmost one is the panel's own.
     */
    public boolean isPointReachable(ModulePanel panel, double mouseX, double mouseY) {
        ensureProjection();
        FloatingRoot top = topRootAt(mouseX, mouseY, null);
        if (top == null) {
            return true;
        }
        LeafNode leaf = leafForPanel(panel);
        return leaf != null && top.rootId().equals(rootContainingNode(viewportWorkspace, leaf.nodeId()).rootId());
    }

    static boolean isAreaUnobscured(DockWorkspace workspace, String ownerRootId, DockRect area) {
        List<FloatingRoot> roots = workspace.roots();
        int ownerIndex = -1;
        for (int i = 0; i < roots.size(); i++) {
            FloatingRoot root = roots.get(i);
            if (root.rootId().equals(ownerRootId)) {
                if (!hasVisibleLeaf(workspace, root.content()) || !contains(root.bounds(), area)) {
                    return false;
                }
                ownerIndex = i;
                break;
            }
        }
        if (ownerIndex < 0) {
            return false;
        }
        for (int i = ownerIndex + 1; i < roots.size(); i++) {
            FloatingRoot higher = roots.get(i);
            if (hasVisibleLeaf(workspace, higher.content()) && intersects(higher.bounds(), area)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasVisibleLeaf(DockWorkspace workspace, LayoutNode node) {
        if (node instanceof LeafNode) {
            return workspace.policyFor(((LeafNode) node).moduleId()).visible();
        }
        SplitNode split = (SplitNode) node;
        return hasVisibleLeaf(workspace, split.first()) || hasVisibleLeaf(workspace, split.second());
    }

    private static boolean intersects(DockRect first, DockRect second) {
        return first.x() < second.right()
                && first.right() > second.x()
                && first.y() < second.bottom()
                && first.bottom() > second.y();
    }

    // --- 渲染 --------------------------------------------------------------------

    /**
     * 背景层：按绘制顺序画每个根的 chrome（拼接根只画一次 9-slice）、区段标题、内容与槽位，
     * 最后画拖拽预览高亮。
     *
     * <p>Z 序：每个根占一条"重叠带"（{@link #paintZOf(FloatingRoot)} = 带号 × {@link #ROOT_LAYER_Z}），
     * 同一根内部再按固定偏移分层；带内则靠调用顺序（后画的在上）。{@link #paintOrderRoots()} 的
     * 绘制顺序与带号对重叠的窗口是一致的。</p>
     *
     * <p><b>槽位内容必须在这里画</b>（{@code slotRenderer} 非空时）：原版的"压平通道"
     * （{@code GuiContainer.drawScreen} 里对每个 slot 调一次 {@code drawSlot}）会把所有窗口的物品
     * 按槽位顺序画在<b>所有</b>窗口 chrome 之上，抬升/遮挡窗口后层级就错了。宿主因此改为
     * 按 root 顺序、在 chrome 之后立刻画本窗口的槽位内容，并把压平通道对终端槽位静音。</p>
     *
     * @param slotRenderer 宿主传入的槽位内容绘制回调（按 root 顺序调用）；无槽位传 {@code null}
     */
    public void render(int mouseX, int mouseY, float partialTicks, float zLevel,
            ModulePanel.PanelSlotRenderer slotRenderer) {
        ensureProjection();
        ModulePanel hoveredLeaf = topLeafAt(mouseX, mouseY);
        List<FloatingRoot> paintOrder = paintOrderRoots();
        float dropHighlightZ = zLevel;
        for (FloatingRoot root : paintOrder) {
            float rootZ = zLevel + paintZOf(root);
            dropHighlightZ = Math.max(dropHighlightZ, rootZ);
            boolean composite = visibleLeafCount(root) > 1;
            if (composite) {
                for (LeafNode leaf : leavesOf(root)) {
                    ModulePanel panel = panelsByModuleId.get(leaf.moduleId());
                    if (panel == null || !projection.visibleLeaf(leaf.nodeId()).isPresent()) {
                        continue;
                    }
                    panel.renderUnderlay(routedMouse(panel, hoveredLeaf, mouseX), routedMouse(panel, hoveredLeaf, mouseY),
                            partialTicks, rootZ);
                }
            }
            // 单叶窗口也要画整窗 chrome：它的叶子只画标题条（走 renderSectionHeader）。
            // 之前单叶窗口依赖 ModulePanel.renderFrame，而该入口对 hosted 叶子会早退，
            // 于是"只有一个面板的窗口"（例如默认布局里的编码窗口）没有背景，只剩标题文字。
            ModulePanel.renderRootChrome(contentChromeBounds(root), false, rootZ);
            for (LeafNode leaf : paintLeaves(root)) {
                if (!projection.visibleLeaf(leaf.nodeId()).isPresent()) {
                    continue;
                }
                ModulePanel panel = panelsByModuleId.get(leaf.moduleId());
                if (panel == null) {
                    continue;
                }
                int routedX = routedMouse(panel, hoveredLeaf, mouseX);
                int routedY = routedMouse(panel, hoveredLeaf, mouseY);
                if (!composite) {
                    panel.renderUnderlay(routedX, routedY, partialTicks, rootZ);
                }
                panel.renderSectionHeader(routedX, routedY, rootZ);
                panel.renderBackgroundContent(routedX, routedY, partialTicks, rootZ);
            }
            if (slotRenderer != null) {
                for (LeafNode leaf : leavesOf(root)) {
                    if (!projection.visibleLeaf(leaf.nodeId()).isPresent()) {
                        continue;
                    }
                    ModulePanel panel = panelsByModuleId.get(leaf.moduleId());
                    if (panel != null) {
                        panel.renderSlots(mouseX, mouseY, partialTicks, slotRenderer, rootZ + SLOT_CONTENT_Z);
                    }
                }
            }
            ModulePanel.renderResizeGrip(root.bounds(), rootZ);
        }
        renderDropHighlight(mouseX, mouseY, dropHighlightZ + ROOT_LAYER_Z);
    }

    /**
     * 前景层：叶子的前景内容，Z 高于同根背景 {@link #FOREGROUND_Z}；只有光标下的叶子收到真实坐标。
     */
    public void renderForeground(int mouseX, int mouseY, float partialTicks, float zLevel) {
        ensureProjection();
        FloatingRoot hoveredRoot = topRootAt(mouseX, mouseY, null);
        ModulePanel hoveredLeaf = topLeafAt(mouseX, mouseY);
        for (FloatingRoot root : paintOrderRoots()) {
            float rootZ = zLevel + paintZOf(root) + FOREGROUND_Z;
            for (LeafNode leaf : leavesOf(root)) {
                if (!projection.visibleLeaf(leaf.nodeId()).isPresent()) {
                    continue;
                }
                ModulePanel panel = panelsByModuleId.get(leaf.moduleId());
                if (panel == null) {
                    continue;
                }
                boolean hover = root == hoveredRoot && panel == hoveredLeaf;
                panel.renderForegroundContent(
                        hover ? mouseX : Integer.MIN_VALUE,
                        hover ? mouseY : Integer.MIN_VALUE,
                        partialTicks,
                        rootZ);
            }
        }
    }

    /** 只有光标下最上层的叶子收到真实坐标，避免下层窗口也画 hover 高亮。 */
    private static int routedMouse(ModulePanel panel, ModulePanel hoveredLeaf, int coordinate) {
        return panel == hoveredLeaf ? coordinate : Integer.MIN_VALUE;
    }

    private void renderDropHighlight(double mouseX, double mouseY, float zLevel) {
        if (mode != Mode.DRAG_ROOT || dragHoverBounds == null) {
            return;
        }
        boolean pushed = NinePatch.pushZLevel(zLevel);
        // While a root is dragged, outline the thin splice bands of the leaf under the pointer.
        // The band that would actually receive the drop is emphasized below.
        for (DockEdge edge : DockEdge.values()) {
            DockRect zone = dropBounds(dragHoverBounds, edge);
            Gui.drawRect(zone.x(), zone.y(), zone.right(), zone.bottom(), DROP_ZONE_FILL_COLOR);
        }
        if (dropCandidate != null) {
            DockRect bounds = dropCandidate.highlightBounds();
            Gui.drawRect(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), DROP_FILL_COLOR);
            Gui.drawRect(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, DROP_BORDER_COLOR);
            Gui.drawRect(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), DROP_BORDER_COLOR);
            Gui.drawRect(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), DROP_BORDER_COLOR);
            Gui.drawRect(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), DROP_BORDER_COLOR);
        }
        NinePatch.popZLevel(pushed);
    }

    /** 宿主可用它把 hover 的分隔条画成 1px 规则线（MEST 只在编辑器里画）。 */
    public List<LayoutProjection.DividerPlacement> dividersOf(String rootId) {
        ensureProjection();
        List<LayoutProjection.DividerPlacement> rules = new ArrayList<LayoutProjection.DividerPlacement>();
        FloatingRoot root = rootById(rootId);
        for (LayoutProjection.DividerPlacement divider : projection.dividers()) {
            if (LayoutTrees.contains(root.content(), divider.splitNodeId())) {
                rules.add(divider);
            }
        }
        return rules;
    }

    // --- 内部查询 ----------------------------------------------------------------

    /** Cached equivalent of {@code LayoutTrees.leaves(root.content())} for hot paths. */
    private List<LeafNode> leavesOf(FloatingRoot root) {
        List<LeafNode> cached = leavesCache.get(root.rootId());
        if (cached == null) {
            cached = LayoutTrees.leaves(root.content());
            leavesCache.put(root.rootId(), cached);
        }
        return cached;
    }

    /**
     * Top-to-bottom paint order so a joined lower section is committed before siblings draw their
     * title strips on top of it.
     */
    private List<LeafNode> paintLeaves(FloatingRoot root) {
        List<LeafNode> leaves = leavesOf(root);
        if (leaves.size() < 2) {
            return leaves;
        }
        List<LeafNode> ordered = new ArrayList<LeafNode>(leaves);
        ordered.sort(Comparator.comparingInt(leaf -> {
            ModulePanel panel = panelsByModuleId.get(leaf.moduleId());
            return panel == null ? 0 : panel.y;
        }));
        return ordered;
    }

    private FloatingRoot topRootAt(double mouseX, double mouseY, String excludedRootId) {
        List<FloatingRoot> order = paintOrderRoots();
        for (int i = order.size() - 1; i >= 0; i--) {
            FloatingRoot root = order.get(i);
            if (root.rootId().equals(excludedRootId) || !rootEffectivelyVisible(root.rootId())) {
                continue;
            }
            if (contains(root.bounds(), mouseX, mouseY)) {
                return root;
            }
        }
        return null;
    }

    private List<FloatingRoot> paintOrderRoots() {
        if (paintOrderCache != null) {
            return paintOrderCache;
        }
        List<FloatingRoot> unpinned = new ArrayList<FloatingRoot>();
        List<FloatingRoot> pinned = new ArrayList<FloatingRoot>();
        for (FloatingRoot root : viewportWorkspace.roots()) {
            if (!rootEffectivelyVisible(root.rootId())) {
                continue;
            }
            if (root.pinned()) {
                pinned.add(root);
            } else {
                unpinned.add(root);
            }
        }
        List<FloatingRoot> order = new ArrayList<FloatingRoot>(unpinned.size() + pinned.size());
        order.addAll(unpinned);
        order.addAll(pinned);
        paintOrderCache = Collections.unmodifiableList(order);
        return paintOrderCache;
    }

    /** The section whose title bar sits in the window's top-right corner carries the float button. */
    private static LeafNode topRightLeaf(FloatingRoot root, LayoutProjection source) {
        LeafNode best = null;
        DockRect bestBounds = null;
        for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
            LayoutProjection.LeafPlacement placement = source.visibleLeaf(leaf.nodeId()).orElse(null);
            if (placement == null) {
                continue;
            }
            DockRect bounds = placement.bounds();
            if (best == null || bounds.y() < bestBounds.y()
                    || (bounds.y() == bestBounds.y() && bounds.right() > bestBounds.right())) {
                best = leaf;
                bestBounds = bounds;
            }
        }
        return best;
    }

    private boolean otherAnchoredVisible(FloatingRoot self, LayoutProjection source) {
        for (FloatingRoot other : viewportWorkspace.roots()) {
            if (other == self || other.floating()) {
                continue;
            }
            for (LeafNode leaf : LayoutTrees.leaves(other.content())) {
                if (source.visibleLeaf(leaf.nodeId()).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private ModulePanel leafAt(FloatingRoot root, double mouseX, double mouseY) {
        LeafNode leaf = leafNodeAt(root, mouseX, mouseY);
        return leaf == null ? null : panelsByModuleId.get(leaf.moduleId());
    }

    private LeafNode leafNodeAt(FloatingRoot root, double mouseX, double mouseY) {
        List<LeafNode> leaves = leavesOf(root);
        for (int i = leaves.size() - 1; i >= 0; i--) {
            LeafNode leaf = leaves.get(i);
            LayoutProjection.LeafPlacement placement = projection.visibleLeaf(leaf.nodeId()).orElse(null);
            if (placement == null) {
                continue;
            }
            if (contains(placement.bounds(), mouseX, mouseY)) {
                return leaf;
            }
        }
        return null;
    }

    private int visibleLeafCount(FloatingRoot root) {
        int count = 0;
        for (LeafNode leaf : leavesOf(root)) {
            if (projection.visibleLeaf(leaf.nodeId()).isPresent()) {
                count++;
            }
        }
        return count;
    }

    private DockRect contentChromeBounds(FloatingRoot root) {
        DockRect union = null;
        for (LeafNode leaf : leavesOf(root)) {
            LayoutProjection.LeafPlacement placement = projection.visibleLeaf(leaf.nodeId()).orElse(null);
            if (placement == null) {
                continue;
            }
            // Leaf placement is the generated window only; decoration hanging outside the leaf must
            // not widen the return-centering union.
            DockRect bounds = placement.bounds();
            union = union == null ? bounds : union.union(bounds);
        }
        return union;
    }

    private boolean rootEffectivelyVisible(String rootId) {
        for (LayoutProjection.RootPlacement root : projection.roots()) {
            if (root.rootId().equals(rootId)) {
                return root.effectivelyVisible();
            }
        }
        return false;
    }

    private DockRect projectionBounds(String nodeId) {
        ensureProjection();
        return projection.boundsFor(nodeId).orElse(null);
    }

    private DockSize rootMinimum(String rootId) {
        for (LayoutProjection.RootPlacement root : projection.roots()) {
            if (root.rootId().equals(rootId)) {
                return root.minimumSize();
            }
        }
        return DockSize.ZERO;
    }

    private FloatingRoot rootById(String rootId) {
        for (FloatingRoot root : viewportWorkspace.roots()) {
            if (root.rootId().equals(rootId)) {
                return root;
            }
        }
        throw new IllegalArgumentException("unknown root: " + rootId);
    }

    private LayoutNode findNode(String nodeId) {
        for (FloatingRoot root : workspace.roots()) {
            Optional<LayoutNode> node = LayoutTrees.find(root.content(), nodeId);
            if (node.isPresent()) {
                return node.get();
            }
        }
        throw new IllegalArgumentException("unknown node: " + nodeId);
    }

    private ModuleLayoutPolicy policyFor(LeafNode leaf) {
        return workspace.policyFor(leaf.moduleId());
    }

    private boolean canResizeNode(LayoutNode node) {
        if (node instanceof LeafNode) {
            return policyFor((LeafNode) node).resizable();
        }
        SplitNode split = (SplitNode) node;
        return canResizeNode(split.first()) && canResizeNode(split.second());
    }

    private static boolean inRootResizeHandle(DockRect bounds, double mouseX, double mouseY) {
        return mouseX >= bounds.right() - ModulePanel.RESIZE_HANDLE
                && mouseX < bounds.right()
                && mouseY >= bounds.bottom() - ModulePanel.RESIZE_HANDLE
                && mouseY < bounds.bottom();
    }

    private static int dropZoneThickness(DockRect bounds) {
        return Math.max(
                DROP_ZONE_MIN,
                Math.min(DROP_ZONE_MAX, Math.min(bounds.width(), bounds.height()) / 8));
    }

    private static DockEdge dropEdge(DockRect bounds, double mouseX, double mouseY) {
        int zone = dropZoneThickness(bounds);
        double left = mouseX - bounds.x();
        double right = bounds.right() - mouseX;
        double top = mouseY - bounds.y();
        double bottom = bounds.bottom() - mouseY;
        double minimum = Math.min(Math.min(left, right), Math.min(top, bottom));
        if (minimum > zone) {
            return null;
        }
        if (minimum == left) {
            return DockEdge.LEFT;
        }
        if (minimum == right) {
            return DockEdge.RIGHT;
        }
        if (minimum == top) {
            return DockEdge.TOP;
        }
        return DockEdge.BOTTOM;
    }

    private static DockRect dropBounds(DockRect target, DockEdge edge) {
        int zone = dropZoneThickness(target);
        int width = edge.axis() == DockAxis.HORIZONTAL ? zone : target.width();
        int height = edge.axis() == DockAxis.VERTICAL ? zone : target.height();
        int x = edge == DockEdge.RIGHT ? target.right() - width : target.x();
        int y = edge == DockEdge.BOTTOM ? target.bottom() - height : target.y();
        return new DockRect(x, y, width, height);
    }

    private static boolean contains(DockRect bounds, double mouseX, double mouseY) {
        return mouseX >= bounds.x()
                && mouseX < bounds.x() + (double) bounds.width()
                && mouseY >= bounds.y()
                && mouseY < bounds.y() + (double) bounds.height();
    }

    private static boolean contains(DockRect outer, DockRect inner) {
        return inner.x() >= outer.x()
                && inner.y() >= outer.y()
                && inner.right() <= outer.right()
                && inner.bottom() <= outer.bottom();
    }

    private static int positiveViewport(int extent) {
        return Math.max(1, extent);
    }

    private static ModuleCatalog createCatalog(List<ModulePanel> source, DockInsets excludedInsets) {
        Map<String, ModuleMetrics> entries = new LinkedHashMap<String, ModuleMetrics>();
        for (ModulePanel panel : source) {
            int minimumWidth = Math.max(1, panel.minWidth() - excludedInsets.horizontal());
            int minimumHeight = Math.max(1, panel.minHeight() - excludedInsets.vertical());
            int defaultWidth = Math.max(minimumWidth, panel.defaultWidth() - excludedInsets.horizontal());
            int defaultHeight = Math.max(minimumHeight, panel.defaultHeight() - excludedInsets.vertical());
            entries.put(
                    panel.id(),
                    new ModuleMetrics(
                            new DockSize(minimumWidth, minimumHeight),
                            new DockSize(defaultWidth, defaultHeight),
                            panel.expandsVertically(),
                            panel.defaults()));
        }
        return new ModuleCatalog(entries);
    }

    /** 供阶段 2/调试使用：当前激活的手势名（无手势时 {@code NONE}）。 */
    public String activeModeName() {
        return mode.name();
    }

    /** 供阶段 2/宿主使用：正在被拖动的根 id（无则为 null）。 */
    public String activeRootId() {
        return activeRootId;
    }

    /** 供宿主使用：拖拽预览高亮的根叶子 bounds（无则为 null）。 */
    public DockRect dragHoverBounds() {
        return dragHoverBounds;
    }

    /** 供宿主使用：当前是否会执行拼接（拖拽预览高亮用）。 */
    public DropCandidate dropCandidate() {
        return dropCandidate;
    }
}
