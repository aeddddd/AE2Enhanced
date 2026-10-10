/*
 * Derived from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockAxis;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockEdge;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutProjection;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutTrees;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleLayoutPolicy;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.SplitNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutCodec;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.AtomicFileDockLayoutStore;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutDto;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutPersistence;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockWorkspaceDefaults;

/**
 * DockManager 的行为测试：手势、拼接、Z 序、不变量、undo 与持久化防抖。
 *
 * <p>渲染方法一概不调用（需要 GL）；被驱动的路径只经过 {@link DockHost} 与 model/workspace 包。</p>
 */
class DockManagerTest {

    private static final int SCREEN_WIDTH = 800;
    private static final int SCREEN_HEIGHT = 600;

    @TempDir
    Path temporaryDirectory;

    // --- 测试夹具 ----------------------------------------------------------------

    private static class TestHost implements DockHost {
        private final DockLayoutPersistence persistence;
        private int width = SCREEN_WIDTH;
        private int height = SCREEN_HEIGHT;
        private boolean alt;
        private boolean control;
        private int redrawRequests;

        private TestHost(DockLayoutPersistence persistence) {
            this.persistence = persistence;
        }

        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public DockLayoutPersistence createPersistence(DockLayoutCodec codec) {
            return persistence;
        }

        @Override
        public boolean altDown() {
            return alt;
        }

        @Override
        public boolean controlDown() {
            return control;
        }

        @Override
        public void requestRedraw() {
            redrawRequests++;
        }
    }

    private static final class FakePersistence implements DockLayoutPersistence {
        private final DockWorkspace preloaded;
        private final Path quarantine;
        private int saveCount;
        private DockWorkspace savedWorkspace;

        private FakePersistence(DockWorkspace preloaded, Path quarantine) {
            this.preloaded = preloaded;
            this.quarantine = quarantine;
        }

        private FakePersistence(DockWorkspace preloaded) {
            this(preloaded, null);
        }

        @Override
        public Optional<DockLayoutCodec.DecodedLayout> load() throws IOException {
            if (preloaded == null) {
                return Optional.empty();
            }
            return Optional.of(new DockLayoutCodec.DecodedLayout(preloaded,
                    DockLayoutDto.CURRENT_VERSION, false, false));
        }

        @Override
        public void save(DockWorkspace workspace) {
            saveCount++;
            savedWorkspace = workspace;
        }

        @Override
        public Optional<Path> lastQuarantinedPath() {
            return Optional.ofNullable(quarantine);
        }
    }

    private static final class TestPanel extends ModulePanel {
        private final String moduleId;
        private final int minimumWidth;
        private final int minimumHeight;
        private final int defaultWidth;
        private final int defaultHeight;
        private boolean spliceable = true;
        private boolean consumeWheel;
        private int wheelCalls;
        private double lastWheelX = Double.NaN;
        private boolean consumeClick;
        private int clickCalls;
        private boolean closable = true;
        private boolean consumeScrollbar;
        private int scrollbarPressCalls;
        private int scrollbarDragCalls;
        private int scrollbarReleaseCalls;
        private boolean scrollbarDragging;

        private TestPanel(String moduleId, int minimumWidth, int minimumHeight, int defaultWidth,
                int defaultHeight) {
            this.moduleId = moduleId;
            this.minimumWidth = minimumWidth;
            this.minimumHeight = minimumHeight;
            this.defaultWidth = defaultWidth;
            this.defaultHeight = defaultHeight;
        }

        @Override
        public String id() {
            return moduleId;
        }

        @Override
        public String title() {
            return moduleId;
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
        public boolean canSplice() {
            return spliceable;
        }

        @Override
        public void layoutSlots() {
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollY) {
            wheelCalls++;
            lastWheelX = mx;
            return consumeWheel;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            clickCalls++;
            return consumeClick;
        }

        @Override
        public boolean scrollbarPressed(double mx, double my) {
            scrollbarPressCalls++;
            scrollbarDragging = consumeScrollbar;
            return consumeScrollbar;
        }

        @Override
        public boolean scrollbarDragged(double mx, double my) {
            scrollbarDragCalls++;
            return scrollbarDragging;
        }

        @Override
        public void scrollbarReleased() {
            scrollbarReleaseCalls++;
            scrollbarDragging = false;
        }

        @Override
        public boolean scrollbarDragging() {
            return scrollbarDragging;
        }

        @Override
        public boolean closable() {
            return closable;
        }
    }

    private static List<ModulePanel> defaultPanels() {
        return new ArrayList<ModulePanel>(Arrays.<ModulePanel>asList(
                new TestPanel("alpha", 90, 60, 160, 110),
                new TestPanel("beta", 100, 70, 180, 140),
                new TestPanel("gamma", 80, 60, 150, 120)));
    }

    private static DockManager initManager(DockHost host, List<ModulePanel> panels) {
        DockManager manager = new DockManager(host);
        manager.init(panels);
        return manager;
    }

    private static FloatingRoot rootFor(DockWorkspace workspace, String moduleId) {
        for (FloatingRoot root : workspace.roots()) {
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                if (leaf.moduleId().equals(moduleId)) {
                    return root;
                }
            }
        }
        throw new AssertionError("no root holds module " + moduleId);
    }

    private static DockWorkspace workspaceOf(List<FloatingRoot> roots) {
        return new DockWorkspace(roots);
    }

    private static FloatingRoot leafRoot(String rootId, String leafNodeId, String moduleId, DockRect bounds,
            boolean floating) {
        return new FloatingRoot(rootId, bounds, new LeafNode(leafNodeId, moduleId, true), floating, false);
    }

    /** 拖拽：按下 → 移动 → 释放。 */
    private static void drag(DockManager manager, double fromX, double fromY, double toX, double toY) {
        assertTrue(manager.mouseClicked(fromX, fromY, 0), "press must be consumed by the dock");
        assertTrue(manager.mouseDragged(toX, toY), "drag must be consumed by the dock");
        assertTrue(manager.mouseReleased(toX, toY, 0), "release must be consumed by the dock");
    }

    // --- 初始化与投影缓存 ---------------------------------------------------------

    @Test
    void initBuildsOneAnchoredWindowPerModule() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());

        DockWorkspace workspace = manager.workspaceSnapshot();
        assertEquals(3, workspace.roots().size());
        assertTrue(manager.hasVisibleAnchored());
        for (FloatingRoot root : workspace.roots()) {
            assertFalse(root.floating(), "defaults are anchored");
            assertInstanceOf(LeafNode.class, root.content());
        }
        // 默认级联位置来自 LegacyMigrationContext：tileStart 8、step 16、窗口 = default + rootInsets
        assertEquals(new DockRect(8, 8, 160, 110), rootFor(workspace, "alpha").bounds());
        assertEquals(new DockRect(24, 24, 180, 140), rootFor(workspace, "beta").bounds());
        assertEquals(new DockRect(40, 40, 150, 120), rootFor(workspace, "gamma").bounds());
        assertTrue(manager.isEffectivelyVisible(manager.panels().get(0)));
        assertEquals(DockWorkspaceDefaults.rootId("alpha"), rootFor(workspace, "alpha").rootId());
    }

    @Test
    void projectionIsReusedUntilTheWorkspaceChanges() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());

        LayoutProjection first = manager.projection();
        assertSame(first, manager.projection(), "the projection must be cached between calls");

        manager.toggleVisible(manager.panels().get(2));
        assertNotSame(first, manager.projection(), "a layout change must invalidate the cache");
        assertSame(manager.projection(), manager.projection(), "and be cached again");

        manager.setWindowFloating(manager.panels().get(0), true);
        assertTrue(manager.structureVersion() > 1, "structural edits bump the structure version");
    }

    @Test
    void viewportResizeReclampsWithoutTouchingTheCanonicalWorkspace() {
        TestHost host = new TestHost(null);
        DockManager manager = initManager(host, defaultPanels());
        DockWorkspace before = manager.workspaceSnapshot();

        host.width = 120;
        host.height = 100;
        manager.updateViewport(host.width, host.height);

        assertSame(before, manager.workspaceSnapshot(), "viewport clamping must not rewrite the layout");
        String betaRootId = rootFor(before, "beta").rootId();
        DockRect clamped = null;
        for (LayoutProjection.RootPlacement placement : manager.projection().roots()) {
            if (placement.rootId().equals(betaRootId)) {
                clamped = placement.bounds();
            }
        }
        assertTrue(clamped != null, "the root must stay projected");
        assertTrue(clamped.x() >= 0 && clamped.y() >= 0);
        assertTrue(clamped.right() <= 120 && clamped.bottom() <= 100);
    }

    // --- 手势：移动 / 吸附 --------------------------------------------------------

    @Test
    void draggingATitleBarMovesTheRoot() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());

        // alpha 的标题栏：按下 (28,16)，拖到 (320,208)（grab 偏移不变）
        drag(manager, 28, 16, 320, 208);

        assertEquals(new DockRect(300, 200, 160, 110), rootFor(manager.workspaceSnapshot(), "alpha").bounds());
        assertTrue(manager.canUndoLayout());
    }

    @Test
    void releasingNearAnotherWindowSnapsToItsEdge() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());

        // 抓取偏移是 (28-8)=20；拖到鼠标 x=213 即 alpha.x=193，落在 gamma 右边缘 (190) 的 3px 内
        int gammaRight = 40 + 150;
        drag(manager, 28, 16, 213, 16);

        FloatingRoot alpha = rootFor(manager.workspaceSnapshot(), "alpha");
        assertEquals(gammaRight, alpha.bounds().x());
    }

    @Test
    void droppingOnALeafEdgeSplicesTheTwoWindows() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());

        // beta(24,24,180x140) 拖到 alpha(8,8,160x110) 的右边缘落区（x∈[155,168)）。
        // y 必须小于 gamma 的顶边 40，否则命中的是压在 alpha 上面的 gamma。
        drag(manager, 24 + 40, 24 + 8, 160, 20);

        DockWorkspace workspace = manager.workspaceSnapshot();
        assertEquals(2, workspace.roots().size(), "both windows must merge into one root");
        FloatingRoot joined = rootFor(workspace, "alpha");
        SplitNode split = assertInstanceOf(SplitNode.class, joined.content());
        assertEquals(DockAxis.HORIZONTAL, split.axis());
        assertEquals("alpha", ((LeafNode) split.first()).moduleId(), "RIGHT edge keeps the target first");
        assertEquals("beta", ((LeafNode) split.second()).moduleId());
        assertEquals(joined.rootId(), rootFor(workspace, "beta").rootId());
        assertTrue(split.ratio() > 0.0 && split.ratio() < 1.0);
    }

    @Test
    void droppingInTheMiddleOfALeafDoesNotSplice() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());

        // (80,30) 在 alpha 内部、离各边都超过落区厚度 13px，且 y=30 在 gamma 顶边(40)之上
        assertTrue(manager.mouseClicked(24 + 40, 24 + 8, 0));
        assertTrue(manager.mouseDragged(80, 30));
        assertNull(manager.dropCandidate(), "a centre drop forms no candidate");
        assertTrue(manager.dragHoverBounds() != null, "but the hovered leaf is still tracked");
        assertTrue(manager.mouseReleased(80, 30, 0));

        DockWorkspace workspace = manager.workspaceSnapshot();
        assertEquals(3, workspace.roots().size());
        for (FloatingRoot root : workspace.roots()) {
            assertInstanceOf(LeafNode.class, root.content());
        }
    }

    @Test
    void panelsThatRefuseToSpliceNeverProduceADropCandidate() {
        List<ModulePanel> panels = defaultPanels();
        ((TestPanel) panels.get(1)).spliceable = false;
        DockManager manager = initManager(new TestHost(null), panels);

        drag(manager, 24 + 40, 24 + 8, 160, 20);

        assertEquals(3, manager.workspaceSnapshot().roots().size());
        assertNull(manager.dropCandidate());
    }

    @Test
    void altDraggingASectionTitleSplitsItOutOfAJoinedWindow() {
        TestHost host = new TestHost(null);
        DockManager manager = initManager(host, defaultPanels());
        drag(manager, 24 + 40, 24 + 8, 160, 20);
        assertEquals(2, manager.workspaceSnapshot().roots().size());

        // Alt + 拖 alpha 的区段标题 → alpha 拆成独立窗口
        host.alt = true;
        drag(manager, 8 + 30, 8 + 8, 500, 400);
        host.alt = false;

        DockWorkspace workspace = manager.workspaceSnapshot();
        assertEquals(3, workspace.roots().size());
        assertEquals("alpha", ((LeafNode) rootFor(workspace, "alpha").content()).moduleId());
        assertEquals("beta", ((LeafNode) rootFor(workspace, "beta").content()).moduleId());
    }

    @Test
    void draggingWithinTheDragThresholdKeepsTheWindowTogether() {
        TestHost host = new TestHost(null);
        DockManager manager = initManager(host, defaultPanels());
        drag(manager, 24 + 40, 24 + 8, 160, 20);
        int roots = manager.workspaceSnapshot().roots().size();

        host.alt = true;
        assertTrue(manager.mouseClicked(8 + 30, 8 + 8, 0));
        assertTrue(manager.mouseDragged(8 + 30 + 2, 8 + 8 + 2), "below LEAF_DRAG_THRESHOLD");
        assertTrue(manager.mouseReleased(8 + 30 + 2, 8 + 8 + 2, 0));
        host.alt = false;

        assertEquals(roots, manager.workspaceSnapshot().roots().size());
    }

    // --- 分隔条 / 缩放握把 --------------------------------------------------------

    @Test
    void dividerDragClampsToTheSectionMinimumSizes() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());
        drag(manager, 24 + 40, 24 + 8, 160, 20);

        LayoutProjection projection = manager.projection();
        LayoutProjection.DividerPlacement divider = projection.dividers().get(0);
        DockRect seam = divider.bounds();
        double seamX = seam.x() + seam.width() / 2.0;
        double seamY = seam.y() + seam.height() / 2.0;

        assertTrue(manager.mouseClicked(seamX, seamY, 0), "the divider must take the press");
        assertTrue(manager.mouseDragged(seamX + 1000, seamY), "dragging far right must be consumed");
        assertTrue(manager.mouseReleased(seamX + 1000, seamY, 0));

        SplitNode split = (SplitNode) rootFor(manager.workspaceSnapshot(), "alpha").content();
        // beta 保住了自己的最小宽度，alpha 也没有被压到 0
        DockRect alpha = manager.projection().visibleLeaf(
                ((LeafNode) split.first()).nodeId()).orElseThrow(() -> new AssertionError("projected")).bounds();
        DockRect beta = manager.projection().visibleLeaf(
                ((LeafNode) split.second()).nodeId()).orElseThrow(() -> new AssertionError("projected")).bounds();
        assertTrue(alpha.width() >= 90, "alpha must stay at or above its minimum width");
        assertTrue(beta.width() >= 100, "beta must stay at or above its minimum width");
        assertTrue(split.ratio() < 1.0);
    }

    @Test
    void resizeGripEnforcesRootMinimumSize() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());
        DockRect before = rootFor(manager.workspaceSnapshot(), "beta").bounds();

        // beta 的右下角握把：把它拖到远小于最小值
        double gripX = before.right() - 2;
        double gripY = before.bottom() - 2;
        assertTrue(manager.mouseClicked(gripX, gripY, 0), "the grip must take the press");
        assertTrue(manager.mouseDragged(20, 20));
        assertTrue(manager.mouseReleased(20, 20, 0));

        DockRect after = rootFor(manager.workspaceSnapshot(), "beta").bounds();
        DockRect leaf = manager.projection().visibleLeaf(
                LayoutTrees.leaves(rootFor(manager.workspaceSnapshot(), "beta").content()).get(0).nodeId())
                .orElseThrow(() -> new AssertionError("projected")).bounds();
        assertTrue(leaf.width() >= 100, "width must stay at or above the minimum");
        assertTrue(leaf.height() >= 70, "height must stay at or above the minimum");
        assertTrue(after.width() >= 100 && after.height() >= 70);
    }

    // --- Z 序 ---------------------------------------------------------------------

    @Test
    void clickingALowerWindowRaisesItToTheTop() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());
        String alphaRootId = rootFor(manager.workspaceSnapshot(), "alpha").rootId();

        // (20,30) 落在 alpha 的**内容区**（内容区起点 (15,25)；beta/gamma 的左上角分别在 24/40）
        assertFalse(manager.mouseClicked(20, 30, 0), "a content click is not a dock gesture");
        List<FloatingRoot> roots = manager.workspaceSnapshot().roots();
        assertEquals(alphaRootId, roots.get(roots.size() - 1).rootId(), "the clicked window must come up");
        assertTrue(manager.canUndoLayout(), "the raise alone is undoable");
    }

    @Test
    void pendingPanelInteractionCommitsWhenNoGestureTakesIt() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());
        String alphaRootId = rootFor(manager.workspaceSnapshot(), "alpha").rootId();

        manager.beginPanelInteraction(12, 30);
        manager.commitPanelInteraction();

        List<FloatingRoot> roots = manager.workspaceSnapshot().roots();
        assertEquals(alphaRootId, roots.get(roots.size() - 1).rootId());
        assertTrue(manager.canUndoLayout());
    }

    @Test
    void draggingTheBottomWindowThroughAHigherOneStillUsesTheUncoveredTarget() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());

        // gamma 在 (40,40,150x120)，把 beta 拖到 (560,320)：无落区，只是移动
        drag(manager, 24 + 40, 24 + 8, 24 + 40 + 536, 24 + 8 + 296);
        DockRect beta = rootFor(manager.workspaceSnapshot(), "beta").bounds();
        assertEquals(560, beta.x());
        assertEquals(320, beta.y());
    }

    // --- 浮动 / 置顶 / 可见性 ------------------------------------------------------

    @Test
    void togglingFloatingKeepsAtLeastOneAnchoredWindow() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());
        TestPanel alpha = (TestPanel) manager.panels().get(0);

        manager.toggleFloating(alpha);
        assertTrue(manager.isFloatingWindow(alpha));
        assertTrue(manager.hasVisibleAnchored());

        TestPanel beta = (TestPanel) manager.panels().get(1);
        manager.toggleFloating(beta);
        manager.toggleFloating((TestPanel) manager.panels().get(2));
        // 最后一个还没浮动的窗口不允许浮走
        assertTrue(manager.hasVisibleAnchored());
        assertFalse(manager.isFloatingWindow(manager.panels().get(2)));
    }

    @Test
    void everyVisibleWindowFloatingIsRepairedOnLoad() {
        DockWorkspace floating = workspaceOf(Arrays.asList(
                leafRoot("root-a", "leaf-a", "alpha", new DockRect(10, 10, 160, 110), true),
                leafRoot("root-b", "leaf-b", "beta", new DockRect(200, 10, 180, 140), true),
                leafRoot("root-c", "leaf-c", "gamma", new DockRect(400, 10, 150, 120), true)));
        FakePersistence persistence = new FakePersistence(floating);
        DockManager manager = initManager(new TestHost(persistence), defaultPanels());

        assertTrue(manager.hasVisibleAnchored(), "init must restore the anchored invariant");
        assertTrue(persistence.saveCount >= 1, "the repaired layout must be persisted");
    }

    @Test
    void hidingAndRevealingAModuleKeepsItsWindow() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());
        TestPanel gamma = (TestPanel) manager.panels().get(2);

        manager.hideModule(gamma);
        assertFalse(manager.policyFor(gamma).visible());
        assertFalse(manager.projection().isEffectivelyVisible(
                LayoutTrees.leaves(rootFor(manager.workspaceSnapshot(), "gamma").content()).get(0).nodeId()));

        manager.revealModule(gamma);
        assertTrue(manager.policyFor(gamma).visible());
        assertTrue(manager.isEffectivelyVisible(gamma));
    }

    @Test
    void pinningIsOfferedOnlyOnFloatingWindows() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());
        TestPanel alpha = (TestPanel) manager.panels().get(0);

        manager.togglePinned(alpha);
        assertFalse(rootFor(manager.workspaceSnapshot(), "alpha").pinned(), "anchored windows cannot pin");

        manager.toggleFloating(alpha);
        assertTrue(manager.isFloatingUnpinned(alpha), "a floating unpinned window reports as such");
        manager.togglePinned(alpha);
        assertTrue(rootFor(manager.workspaceSnapshot(), "alpha").pinned());
        assertFalse(manager.isFloatingUnpinned(alpha), "pinned windows are not floating-unpinned");
        assertFalse(manager.isFloatingWindow(manager.panels().get(1)), "beta is still anchored");
    }

    // --- undo / 持久化 ------------------------------------------------------------

    @Test
    void undoRestoresTheWorkspaceFromBeforeAGesture() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());
        DockRect before = rootFor(manager.workspaceSnapshot(), "alpha").bounds();

        drag(manager, 28, 16, 320, 208);
        assertEquals(new DockRect(300, 200, 160, 110), rootFor(manager.workspaceSnapshot(), "alpha").bounds());
        assertTrue(manager.canUndoLayout());

        manager.undoLayout();
        assertEquals(before, rootFor(manager.workspaceSnapshot(), "alpha").bounds());
        assertFalse(manager.canUndoLayout(), "only one undo point was recorded");
    }

    @Test
    void saveIsDebouncedUntilTheWorkspaceRevisionChanges() {
        DockWorkspace anchored = new DockWorkspace(Arrays.asList(
                leafRoot("root-a", "leaf-a", "alpha", new DockRect(10, 10, 160, 110), false),
                leafRoot("root-b", "leaf-b", "beta", new DockRect(200, 10, 180, 140), false),
                leafRoot("root-c", "leaf-c", "gamma", new DockRect(400, 10, 150, 120), false)));
        FakePersistence persistence = new FakePersistence(anchored);
        DockManager manager = initManager(new TestHost(persistence), defaultPanels());

        manager.save();
        assertEquals(0, persistence.saveCount, "a layout loaded at the current revision is not rewritten");

        manager.toggleVisible(manager.panels().get(0));
        assertEquals(1, persistence.saveCount);

        manager.save();
        assertEquals(1, persistence.saveCount, "a repeated save at the same revision writes nothing");

        manager.toggleVisible(manager.panels().get(0));
        assertEquals(2, persistence.saveCount);
        assertSame(manager.workspaceSnapshot(), persistence.savedWorkspace);
    }

    @Test
    void quarantineBlocksWritesUntilARealEditHappens() {
        FakePersistence persistence = new FakePersistence(null, Paths.get("layout.json.corrupt.1"));
        DockManager manager = initManager(new TestHost(persistence), defaultPanels());

        manager.save();
        assertEquals(0, persistence.saveCount, "a quarantined load must not be overwritten by defaults");

        drag(manager, 28, 16, 320, 208);
        assertTrue(persistence.saveCount >= 1, "a real edit releases the write guard");
    }

    @Test
    void layoutChangeRequestsARedraw() {
        TestHost host = new TestHost(null);
        DockManager manager = initManager(host, defaultPanels());
        int before = host.redrawRequests;

        manager.setWindowFloating(manager.panels().get(0), true);

        assertTrue(host.redrawRequests > before);
    }

    // --- 可见性（panelMask 的本地真相） --------------------------------------------

    @Test
    void visibleModulesAndMaskAlignment() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());

        assertEquals(3, manager.visibleModuleIds().size());
        assertTrue(manager.isModuleVisible("alpha"));
        assertTrue(manager.isModuleVisible("beta"));
        assertTrue(manager.isModuleVisible("gamma"));
        assertFalse(manager.isModuleVisible("nope"));

        assertTrue(manager.setModuleVisible("beta", false));
        assertFalse(manager.isModuleVisible("beta"));
        assertEquals(2, manager.visibleModuleIds().size());
        // 幂等：再隐藏一次不算改动
        assertFalse(manager.setModuleVisible("beta", false));

        assertTrue(manager.setModuleVisible("beta", true));
        assertTrue(manager.isModuleVisible("beta"));
        assertEquals(3, manager.visibleModuleIds().size());
    }

    @Test
    void oneAnchoredWindowMustStayVisible() {
        List<ModulePanel> panels = defaultPanels();
        DockManager manager = initManager(new TestHost(null), panels);
        // 把 alpha 单独留成唯一的 anchored 窗口
        assertTrue(manager.setModuleVisible("beta", false));
        assertTrue(manager.setModuleVisible("gamma", false));

        // 最后一个锚定窗口不允许隐藏（否则终端变空白）
        assertFalse(manager.setModuleVisible("alpha", false));
        assertTrue(manager.isModuleVisible("alpha"));
    }

    @Test
    void nonClosableModuleCannotBeHidden() {
        List<ModulePanel> panels = defaultPanels();
        TestPanel alpha = (TestPanel) panels.get(0);
        alpha.closable = false;
        DockManager manager = initManager(new TestHost(null), panels);

        assertFalse(manager.setModuleVisible("alpha", false));
        assertTrue(manager.isModuleVisible("alpha"));
        // 但可以重新显示（幂等为 false）
        assertFalse(manager.setModuleVisible("alpha", true));
    }

    @Test
    void applyVisibleModulesAlignsTheWholeSet() {
        List<ModulePanel> panels = defaultPanels();
        DockManager manager = initManager(new TestHost(null), panels);

        assertTrue(manager.applyVisibleModules(java.util.Arrays.asList("alpha")));
        assertEquals(1, manager.visibleModuleIds().size());
        assertFalse(manager.isModuleVisible("gamma"));

        assertTrue(manager.applyVisibleModules(java.util.Arrays.asList("alpha", "beta", "gamma")));
        assertEquals(3, manager.visibleModuleIds().size());
        assertFalse(manager.applyVisibleModules(java.util.Arrays.asList("alpha", "beta", "gamma")),
                "已经是目标状态 → 无改动");
    }

    // --- 查询 / 路由 --------------------------------------------------------------

    @Test
    void wheelIsRoutedToTheTopmostLeafOnly() {
        List<ModulePanel> panels = defaultPanels();
        TestPanel alpha = (TestPanel) panels.get(0);
        TestPanel beta = (TestPanel) panels.get(1);
        DockManager manager = initManager(new TestHost(null), panels);

        // alpha 的 root 在 (8,8) 尺寸 174×142 → 标题栏 y 8..25，内容区起点 (15,25)
        // (20,30) 落在 alpha 的内容区里
        assertFalse(manager.mouseScrolled(20, 30, 1.0D), "panels that do not consume return false");
        assertEquals(1, alpha.wheelCalls);
        assertEquals(0, beta.wheelCalls);
        assertEquals(20.0D, alpha.lastWheelX);

        // 标题栏不是内容区：滚轮不该被列表吞掉
        assertFalse(manager.mouseScrolled(12, 12, 1.0D), "title bar is not content");
        assertEquals(1, alpha.wheelCalls);

        alpha.consumeWheel = true;
        assertTrue(manager.mouseScrolled(20, 30, 1.0D));
        assertEquals(2, alpha.wheelCalls);
    }

    @Test
    void contentClickIsRoutedToTheTopmostLeafAndSwallowedWhenConsumed() {
        List<ModulePanel> panels = defaultPanels();
        TestPanel alpha = (TestPanel) panels.get(0);
        DockManager manager = initManager(new TestHost(null), panels);

        // 未消费：仍然交回宿主（AE2 的槽位逻辑要靠返回值判断）
        assertFalse(manager.mouseClicked(20, 30, 0));
        assertEquals(1, alpha.clickCalls);

        // 消费：dock 吞掉，不再往下穿透
        alpha.consumeClick = true;
        assertTrue(manager.mouseClicked(20, 30, 0));
        assertEquals(2, alpha.clickCalls);

        // 标题栏上的点击仍然是窗口拖动，不经过面板
        int before = alpha.clickCalls;
        assertTrue(manager.mouseClicked(12, 12, 0), "title bar press starts a window drag");
        assertEquals(before, alpha.clickCalls, "title bar clicks must not reach the panel");
        manager.mouseReleased(12, 12, 0);
    }

    @Test
    void nonLeftButtonIsRoutedToPanelContentOnly() {
        List<ModulePanel> panels = defaultPanels();
        TestPanel alpha = (TestPanel) panels.get(0);
        DockManager manager = initManager(new TestHost(null), panels);
        DockRect before = rootFor(manager.workspaceSnapshot(), "alpha").bounds();

        // 右键落在内容区：交给面板（SlotFake 的清空/减半语义靠这条路由）
        assertFalse(manager.mouseClicked(20, 30, 1));
        assertEquals(1, alpha.clickCalls);

        alpha.consumeClick = true;
        assertTrue(manager.mouseClicked(20, 30, 1), "面板消费右键后 dock 吞掉");
        assertEquals(2, alpha.clickCalls);

        // 右键落在标题栏：dock 手势只认左键，这里既不拖动也不路由
        assertFalse(manager.mouseClicked(12, 12, 1));
        assertEquals(2, alpha.clickCalls);
        assertEquals(before, rootFor(manager.workspaceSnapshot(), "alpha").bounds());
    }

    @Test
    void closeButtonHidesTheModule() {
        List<ModulePanel> panels = defaultPanels();
        TestPanel alpha = (TestPanel) panels.get(0);
        DockManager manager = initManager(new TestHost(null), panels);

        // 标题栏关闭按钮（常驻）
        int closeX = alpha.closeButtonX() + 1;
        int closeY = alpha.closeButtonY() + 1;
        assertTrue(alpha.inCloseButton(closeX, closeY));
        assertTrue(manager.mouseClicked(closeX, closeY, 0));
        assertFalse(manager.isModuleVisible("alpha"));

        // 不可关闭的模块不给关闭入口
        List<ModulePanel> second = defaultPanels();
        TestPanel beta = (TestPanel) second.get(1);
        beta.closable = false;
        DockManager manager2 = initManager(new TestHost(null), second);
        assertFalse(beta.inCloseButton(beta.closeButtonX() + 1, beta.closeButtonY() + 1));
    }

    @Test
    void scrollbarDragIsOwnedByThePanelUntilRelease() {
        List<ModulePanel> panels = defaultPanels();
        TestPanel alpha = (TestPanel) panels.get(0);
        DockManager manager = initManager(new TestHost(null), panels);
        DockRect before = rootFor(manager.workspaceSnapshot(), "alpha").bounds();
        alpha.consumeScrollbar = true;

        assertTrue(manager.mouseClicked(20, 30, 0), "the panel takes the scrollbar press");
        assertTrue(manager.isScrollbarDragging());
        assertEquals(1, alpha.scrollbarPressCalls);

        // 拖动期间不会变成窗口拖动：root 位置保持不变
        assertTrue(manager.mouseDragged(200, 120));
        assertEquals(1, alpha.scrollbarDragCalls);
        assertEquals(before, rootFor(manager.workspaceSnapshot(), "alpha").bounds());

        assertTrue(manager.mouseReleased(200, 120, 0));
        assertEquals(1, alpha.scrollbarReleaseCalls);
        assertFalse(manager.isScrollbarDragging());
        assertFalse(manager.mouseDragged(200, 120), "after release the drag is no longer owned");
    }

    @Test
    void topPanelAndDividerQueriesFollowTheProjection() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());

        assertEquals("alpha", manager.topPanelAt(12, 12).id());
        assertNull(manager.topPanelAt(-50, -50));
        assertNull(manager.dividerAt(12, 12));

        drag(manager, 24 + 40, 24 + 8, 160, 20);
        DockManager.DividerHit divider = manager.dividerAt(
                manager.projection().dividers().get(0).bounds().x(),
                manager.projection().dividers().get(0).bounds().y() + 10);
        assertTrue(divider != null, "the joined window must expose a divider");
        assertEquals(DockAxis.HORIZONTAL, divider.axis());
        assertEquals(1, manager.dividersOf(rootFor(manager.workspaceSnapshot(), "alpha").rootId()).size());
    }

    @Test
    void panelsRegisterNoSlotsInThisStage() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());
        // 本阶段没有 Container：面板不注册槽位，因此槽位渲染回调永远不会触发
        for (ModulePanel panel : manager.panels()) {
            assertTrue(panel.ownedSlots().isEmpty(), panel.id() + " must own no slots yet");
        }
    }

    @Test
    void layoutSurvivesARealStoreRoundTrip() throws IOException {
        Path file = temporaryDirectory.resolve("ae2enhanced/spliced_terminal/demo_layout.json");
        DockManager first = new DockManager(new HostWithStore(file));
        first.init(defaultPanels());
        assertFalse(Files.isRegularFile(file), "no file is written before the first edit");

        drag(first, 28, 16, 320, 208);
        assertTrue(Files.isRegularFile(file), "a committed gesture must persist the layout");
        String written = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(written.contains("\"version\": 8"));
        assertTrue(written.contains("alpha"));

        DockManager second = new DockManager(new HostWithStore(file));
        second.init(defaultPanels());
        assertEquals(first.workspaceSnapshot(), second.workspaceSnapshot(),
                "the stored layout must decode back to the same workspace");
        assertEquals(new DockRect(300, 200, 160, 110), rootFor(second.workspaceSnapshot(), "alpha").bounds());
    }

    /** 宿主：把编解码器包进真实的原子文件存储。 */
    private static final class HostWithStore extends TestHost {
        private final Path file;

        private HostWithStore(Path file) {
            super(null);
            this.file = file;
        }

        @Override
        public DockLayoutPersistence createPersistence(DockLayoutCodec codec) {
            return new AtomicFileDockLayoutStore(file, codec);
        }
    }

    // --- MEST 静态判定（工作区层面的可见性） ---------------------------------------

    @Test
    void panelWidgetAreaIsHiddenWhenAnyHigherVisibleRootOverlapsIt() {
        FloatingRoot owner = leafRoot("root-owner", "leaf-owner", "owner", new DockRect(0, 0, 100, 80), true);
        FloatingRoot higher = leafRoot("root-higher", "leaf-higher", "higher", new DockRect(80, 0, 100, 80), true);

        assertFalse(DockManager.isAreaUnobscured(
                workspaceOf(Arrays.asList(owner, higher)), owner.rootId(), new DockRect(82, 1, 16, 18)));
        assertTrue(DockManager.isAreaUnobscured(
                workspaceOf(Arrays.asList(owner, higher)), owner.rootId(), new DockRect(60, 1, 16, 18)));
    }

    @Test
    void hiddenHigherRootDoesNotOccludePanelWidgets() {
        FloatingRoot owner = leafRoot("root-owner", "leaf-owner", "owner", new DockRect(0, 0, 100, 80), true);
        FloatingRoot hiddenHigher = leafRoot("root-higher", "leaf-higher", "higher", new DockRect(80, 0, 100, 80),
                true);
        Map<String, ModuleLayoutPolicy> policies = new LinkedHashMap<String, ModuleLayoutPolicy>();
        policies.put("owner", new ModuleLayoutPolicy(true, true, true));
        policies.put("higher", new ModuleLayoutPolicy(false, true, true));
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(owner, hiddenHigher), policies);

        assertTrue(DockManager.isAreaUnobscured(workspace, owner.rootId(), new DockRect(82, 1, 16, 18)));
    }

    @Test
    void areaMustBelongToTheOwningRoot() {
        FloatingRoot owner = leafRoot("root-owner", "leaf-owner", "owner", new DockRect(0, 0, 100, 80), true);

        assertFalse(DockManager.isAreaUnobscured(
                workspaceOf(Collections.singletonList(owner)), owner.rootId(), new DockRect(90, 1, 16, 18)));
        assertFalse(DockManager.isAreaUnobscured(
                workspaceOf(Collections.singletonList(owner)), "missing", new DockRect(10, 1, 16, 18)));
    }

    @Test
    void detachRuleMatchesTheTerminalBehaviour() {
        assertFalse(DockManager.shouldDetachJoinedLeaf(false, false));
        assertTrue(DockManager.shouldDetachJoinedLeaf(true, false));
        assertFalse(DockManager.shouldDetachJoinedLeaf(true, true), "Ctrl drags the whole window");
        assertFalse(DockManager.shouldDetachJoinedLeaf(false, true));
    }

    @Test
    void applyWorkspaceRejectsLayoutsWithoutAnAnchoredWindow() {
        DockManager manager = initManager(new TestHost(null), defaultPanels());
        DockWorkspace allFloating = workspaceOf(Arrays.asList(
                leafRoot("root-a", "leaf-a", "alpha", new DockRect(10, 10, 160, 110), true),
                leafRoot("root-b", "leaf-b", "beta", new DockRect(200, 10, 180, 140), true),
                leafRoot("root-c", "leaf-c", "gamma", new DockRect(400, 10, 150, 120), true)));
        DockWorkspace before = manager.workspaceSnapshot();

        manager.applyWorkspace(allFloating);

        assertSame(before, manager.workspaceSnapshot(), "an all-floating layout must be refused");
        assertFalse(manager.canUndoLayout());
    }

    @Test
    void workspacesWithUnknownModulesAreRejectedInsteadOfSilentlyPatched() {
        // DockManager 只负责注册当前模块集；工作区里的模块集合与 catalog 不一致时，引擎抛错而不是静默修补
        DockWorkspace extra = workspaceOf(Arrays.asList(
                leafRoot("root-a", "leaf-a", "alpha", new DockRect(10, 10, 160, 110), false),
                leafRoot("root-b", "leaf-b", "beta", new DockRect(200, 10, 180, 140), false),
                leafRoot("root-c", "leaf-c", "gamma", new DockRect(400, 10, 150, 120), false),
                leafRoot("root-d", "leaf-d", "delta", new DockRect(600, 10, 100, 80), false)));
        FakePersistence persistence = new FakePersistence(extra);

        boolean threw = false;
        try {
            initManager(new TestHost(persistence), defaultPanels());
        } catch (RuntimeException e) {
            threw = true;
        }
        assertTrue(threw, "a workspace with unknown modules must not be projected");
    }
}
