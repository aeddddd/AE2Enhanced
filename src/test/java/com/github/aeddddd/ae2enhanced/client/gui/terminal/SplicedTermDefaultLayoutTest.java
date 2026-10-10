package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockInsets;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspaceEditor;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutEngine;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutProjection;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutStyle;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutTrees;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleMetrics;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutCodec;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutFormatException;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockWorkspaceDefaults;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.LegacyMigrationContext;
import com.github.aeddddd.ae2enhanced.client.gui.panel.CraftingPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.PatternEncodingPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.PlayerInvPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.StoragePanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.UpgradePanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.ViewCellPanel;

/**
 * 默认布局：四个窗口，每个窗口都按自己内容的尺寸开，窗口之间留 8px 间隔。
 *
 * <pre>
 * w1 = H[repo | viewcell]        w3 = V[playerinv / upgrade]
 * w2 = H[crafting | storage]     w4 = pattern
 * </pre>
 *
 * <p>默认布局一旦构造失败，{@code DockManager} 会静默退回"每个模块一个独立窗口"的级联摆放，
 * 因此这里把"四个窗口、七个模块齐全、尺寸贴合内容、能存读往返"这几条不变量锁住。</p>
 */
class SplicedTermDefaultLayoutTest {

    private static final int REPO_W = 352;
    private static final int REPO_H = 132;
    private static final int VIEWCELL_W = 32;
    private static final int VIEWCELL_H = 114;
    private static final int CRAFTING_W = 98;
    private static final int CRAFTING_H = 78;
    private static final int STORAGE_W = 176;
    private static final int STORAGE_H = 78;
    private static final int UPGRADE_W = 176;
    private static final int UPGRADE_H = 42;
    private static final int PLAYER_W = 176;
    private static final int PLAYER_H = 96;
    private static final int PATTERN_W = 171;
    private static final int PATTERN_H = 132;

    /** 真实尺寸 catalog（投影用）。 */
    private static ModuleCatalog catalog() {
        Map<String, ModuleMetrics> entries = new LinkedHashMap<String, ModuleMetrics>();
        entries.put(RepoPanel.MODULE_ID, metrics(REPO_W, REPO_H));
        entries.put(ViewCellPanel.MODULE_ID, metrics(VIEWCELL_W, VIEWCELL_H));
        entries.put(CraftingPanel.MODULE_ID, metrics(CRAFTING_W, CRAFTING_H));
        entries.put(StoragePanel.MODULE_ID, metrics(STORAGE_W, STORAGE_H));
        entries.put(UpgradePanel.MODULE_ID, metrics(UPGRADE_W, UPGRADE_H));
        entries.put(PlayerInvPanel.MODULE_ID, metrics(PLAYER_W, PLAYER_H));
        entries.put(PatternEncodingPanel.MODULE_ID, metrics(PATTERN_W, PATTERN_H));
        return new ModuleCatalog(entries);
    }

    private static ModuleMetrics metrics(int width, int height) {
        return new ModuleMetrics(new DockSize(width, height), new DockSize(width, height));
    }

    /**
     * 与线上 {@code DockManager.createDefaultWorkspace(persistenceCatalog, ...)} 同口径的 catalog：
     * legacy v1 内边距（{@code 7,25,7,7}）已经从面板尺寸里减掉了，构造默认布局时再加回去。
     */
    private static ModuleCatalog persistenceCatalog() {
        DockInsets insets = geometry(1280, 685).rootInsets();
        Map<String, ModuleMetrics> entries = new LinkedHashMap<String, ModuleMetrics>();
        entries.put(RepoPanel.MODULE_ID, inset(REPO_W, REPO_H, insets));
        entries.put(ViewCellPanel.MODULE_ID, inset(VIEWCELL_W, VIEWCELL_H, insets));
        entries.put(CraftingPanel.MODULE_ID, inset(CRAFTING_W, CRAFTING_H, insets));
        entries.put(StoragePanel.MODULE_ID, inset(STORAGE_W, STORAGE_H, insets));
        entries.put(UpgradePanel.MODULE_ID, inset(UPGRADE_W, UPGRADE_H, insets));
        entries.put(PlayerInvPanel.MODULE_ID, inset(PLAYER_W, PLAYER_H, insets));
        entries.put(PatternEncodingPanel.MODULE_ID, inset(PATTERN_W, PATTERN_H, insets));
        return new ModuleCatalog(entries);
    }

    private static ModuleMetrics inset(int width, int height, DockInsets insets) {
        return metrics(Math.max(1, width - insets.horizontal()), Math.max(1, height - insets.vertical()));
    }

    private static LegacyMigrationContext geometry(int width, int height) {
        return LegacyMigrationContext.currentDockDefaults(width, height);
    }

    /** 与线上 {@code DockManager.fitCompositeRootsToShell} 同口径的 shell 拟合。 */
    private static UnaryOperator<DockWorkspace> shellFit(ModuleCatalog realCatalog) {
        LayoutEngine engine = new LayoutEngine(realCatalog, new LayoutStyle(DockInsets.NONE, 0));
        DockWorkspaceEditor editor = new DockWorkspaceEditor(realCatalog);
        return workspace -> {
            DockWorkspace next = workspace;
            for (FloatingRoot root : workspace.roots()) {
                int visibleLeaves = 0;
                for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                    if (workspace.policyFor(leaf.moduleId()).visible()) {
                        visibleLeaves++;
                    }
                }
                if (visibleLeaves < 2) {
                    continue;
                }
                DockSize fitted = engine.preferredSize(next, root.content());
                if (fitted.isEmpty()) {
                    continue;
                }
                next = editor.setRootBounds(next, root.rootId(), new DockRect(
                        root.bounds().x(), root.bounds().y(),
                        Math.max(1, fitted.width()), Math.max(1, fitted.height())));
            }
            return next;
        };
    }

    private static Set<String> moduleIds(DockWorkspace workspace) {
        Set<String> ids = new LinkedHashSet<String>();
        for (FloatingRoot root : workspace.roots()) {
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                ids.add(leaf.moduleId());
            }
        }
        return ids;
    }

    private static DockRect rootOf(DockWorkspace workspace, String moduleId) {
        String wanted = DockWorkspaceDefaults.rootId(moduleId);
        for (FloatingRoot root : workspace.roots()) {
            if (root.rootId().equals(wanted)) {
                return root.bounds();
            }
        }
        throw new AssertionError("no root " + wanted + " containing " + moduleId);
    }

    private static Map<String, DockRect> leafBounds(DockWorkspace workspace, ModuleCatalog realCatalog) {
        LayoutProjection projection = new LayoutEngine(realCatalog, new LayoutStyle(DockInsets.NONE, 0))
                .project(workspace);
        Map<String, DockRect> result = new LinkedHashMap<String, DockRect>();
        for (String moduleId : realCatalog.moduleIds()) {
            String leafId = DockWorkspaceDefaults.leafNodeId(moduleId);
            result.put(moduleId, projection.visibleLeaf(leafId).get().bounds());
        }
        return result;
    }

    private static DockWorkspace defaultLayout(int viewportWidth, int viewportHeight) {
        return SplicedTermDefaultLayout.create(persistenceCatalog(), geometry(viewportWidth, viewportHeight),
                UnaryOperator.identity());
    }

    @Test
    void defaultLayoutIsFourWindowsEachSizedToItsContent() {
        DockWorkspace workspace = defaultLayout(1280, 685);

        assertEquals(4, workspace.roots().size(),
                "默认布局是四个窗口（多于/少于 4 说明 split 构造抛异常后退回了级联摆放）");
        assertEquals(7, moduleIds(workspace).size(), "七个模块都要在默认布局里");

        // w1 = repo | viewcell，行高由 repo 决定
        assertEquals(new DockRect(356, 203, REPO_W + VIEWCELL_W, REPO_H), rootOf(workspace, RepoPanel.MODULE_ID),
                "w1 应贴合 repo+viewcell 的内容尺寸");
        // w2 = crafting | storage，两块内容等高
        assertEquals(new DockRect(356, 343, CRAFTING_W + STORAGE_W, Math.max(CRAFTING_H, STORAGE_H)),
                rootOf(workspace, CraftingPanel.MODULE_ID), "w2 应贴合 crafting+storage 的内容尺寸");
        // w3 = playerinv / upgrade，两块内容等宽
        assertEquals(new DockRect(748, 203, PLAYER_W, PLAYER_H + UPGRADE_H),
                rootOf(workspace, PlayerInvPanel.MODULE_ID), "w3 应贴合 playerinv+upgrade 的内容尺寸");
        // w4 = pattern，独占一窗
        assertEquals(new DockRect(748, 349, PATTERN_W, PATTERN_H),
                rootOf(workspace, PatternEncodingPanel.MODULE_ID), "w4 应贴合编码区的内容尺寸");
    }

    /** 四个窗口互不重叠，窗口之间至少留 8px 间隔。 */
    @Test
    void windowsDoNotOverlap() {
        DockWorkspace workspace = defaultLayout(1280, 685);
        List<FloatingRoot> roots = workspace.roots();
        for (int i = 0; i < roots.size(); i++) {
            for (int j = i + 1; j < roots.size(); j++) {
                DockRect a = roots.get(i).bounds();
                DockRect b = roots.get(j).bounds();
                boolean separated = a.right() + 1 <= b.x() || b.right() + 1 <= a.x()
                        || a.bottom() + 1 <= b.y() || b.bottom() + 1 <= a.y();
                assertTrue(separated, "窗口重叠或没有间隔: " + a + " / " + b);
            }
        }
    }

    /** 每个窗口内部的叶子都要拿到正面积、且不越出窗口矩形。 */
    @Test
    void everyModuleGetsAVisibleLeafInsideItsWindow() {
        ModuleCatalog real = catalog();
        DockWorkspace workspace = defaultLayout(1280, 685);
        verifyLeavesInsideWindows(workspace, real);
    }

    /** 视口缩到 320x200（比内容还小）时，四个窗口要能整体等比缩下去且不越出视口。 */
    @Test
    void defaultLayoutShrinksIntoSmallViewports() {
        ModuleCatalog real = catalog();
        DockWorkspace workspace = defaultLayout(320, 200);

        assertEquals(4, workspace.roots().size());
        for (FloatingRoot root : workspace.roots()) {
            DockRect bounds = root.bounds();
            assertTrue(bounds.width() > 0 && bounds.height() > 0, "缩后窗口必须有正面积");
            assertTrue(bounds.right() <= 320, "root 越出视口宽度: " + bounds);
            assertTrue(bounds.bottom() <= 200, "root 越出视口高度: " + bounds);
            assertTrue(bounds.x() >= 0 && bounds.y() >= 0, "root 越出视口左上角: " + bounds);
        }
        verifyLeavesInsideWindows(workspace, real);
    }

    /** 视口变大不会让任何面板变大：默认布局的尺寸只由内容决定。 */
    @Test
    void panelsDoNotGrowWithTheViewport() {
        ModuleCatalog real = catalog();
        Map<String, DockRect> small = leafBounds(defaultLayout(800, 600), real);
        Map<String, DockRect> large = leafBounds(defaultLayout(1920, 1080), real);

        for (String moduleId : real.moduleIds()) {
            assertEquals(small.get(moduleId).width(), large.get(moduleId).width(),
                    "视口变大后宽度被撑大: " + moduleId);
            assertEquals(small.get(moduleId).height(), large.get(moduleId).height(),
                    "视口变大后高度被撑大: " + moduleId);
        }
    }

    /** 每个窗口里的叶子都按内容尺寸排（不会有面板被拉大）：这是拆成四窗的全部意义。 */
    @Test
    void everyLeafKeepsItsContentSize() {
        ModuleCatalog real = catalog();
        Map<String, DockRect> leaves = leafBounds(defaultLayout(1280, 685), real);

        assertEquals(REPO_W, leaves.get(RepoPanel.MODULE_ID).width(), "repo 宽度 = 内容宽度");
        assertEquals(REPO_H, leaves.get(RepoPanel.MODULE_ID).height(), "repo 高度 = 内容高度");
        // view cell 与 repo 同行，列高由行高决定（内容 114 只占顶部，底部留白 18px）
        assertEquals(VIEWCELL_W, leaves.get(ViewCellPanel.MODULE_ID).width(), "view cell 宽度 = 内容宽度");
        assertEquals(REPO_H, leaves.get(ViewCellPanel.MODULE_ID).height(), "view cell 与 repo 同行，行高取行高");

        assertEquals(CRAFTING_W, leaves.get(CraftingPanel.MODULE_ID).width(), "crafting 宽度 = 内容宽度");
        assertEquals(CRAFTING_H, leaves.get(CraftingPanel.MODULE_ID).height(), "crafting 高度 = 内容高度");
        assertEquals(STORAGE_W, leaves.get(StoragePanel.MODULE_ID).width(), "storage 宽度 = 内容宽度");
        assertEquals(STORAGE_H, leaves.get(StoragePanel.MODULE_ID).height(), "storage 高度 = 内容高度");

        assertEquals(PLAYER_W, leaves.get(PlayerInvPanel.MODULE_ID).width(), "playerinv 宽度 = 内容宽度");
        assertEquals(PLAYER_H, leaves.get(PlayerInvPanel.MODULE_ID).height(), "playerinv 高度 = 内容高度");
        assertEquals(UPGRADE_W, leaves.get(UpgradePanel.MODULE_ID).width(), "upgrade 宽度 = 内容宽度");
        assertEquals(UPGRADE_H, leaves.get(UpgradePanel.MODULE_ID).height(), "upgrade 高度 = 内容高度");

        assertEquals(PATTERN_W, leaves.get(PatternEncodingPanel.MODULE_ID).width(), "编码区宽度 = 内容宽度");
        assertEquals(PATTERN_H, leaves.get(PatternEncodingPanel.MODULE_ID).height(), "编码区高度 = 内容高度");
    }

    private static void verifyLeavesInsideWindows(DockWorkspace workspace, ModuleCatalog real) {
        LayoutProjection projection = new LayoutEngine(real, new LayoutStyle(DockInsets.NONE, 0))
                .project(workspace);
        Set<String> checked = new LinkedHashSet<String>();
        for (FloatingRoot root : workspace.roots()) {
            DockRect bounds = root.bounds();
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                DockRect leafBounds = projection.visibleLeaf(leaf.nodeId()).get().bounds();
                assertTrue(leafBounds.width() > 0 && leafBounds.height() > 0,
                        "leaf 必须有正面积: " + leaf.moduleId());
                assertTrue(leafBounds.x() >= bounds.x() && leafBounds.right() <= bounds.right(),
                        "leaf 越出 root 水平范围: " + leaf.moduleId() + " " + leafBounds + " vs " + bounds);
                assertTrue(leafBounds.y() >= bounds.y() && leafBounds.bottom() <= bounds.bottom(),
                        "leaf 越出 root 垂直范围: " + leaf.moduleId() + " " + leafBounds + " vs " + bounds);
                checked.add(leaf.moduleId());
            }
        }
        assertEquals(real.moduleIds(), checked, "每个模块都要有一个可见叶子");
    }

    /**
     * 默认布局必须能经受"存盘 → 读回"的往返：读回后的每个叶子尺寸/位置都要和刚构造出来时一致。
     *
     * <p>实机踩过：读回时 {@code shellFit}（{@code fitCompositeRootsToShell}）会用 catalog 的口径
     * 重算多叶窗口尺寸，若与构造口径不一致（一个含 legacy 内边距、一个不含），窗口会被改成
     * "装不下自己内容"的尺寸，叶子溢出 root、槽位坐标全错。</p>
     */
    @Test
    void defaultLayoutSurvivesTheSaveLoadRoundTrip() {
        ModuleCatalog real = catalog();
        LegacyMigrationContext context = geometry(1280, 685);
        DockWorkspace created = SplicedTermDefaultLayout.create(persistenceCatalog(), context, shellFit(real));

        DockLayoutCodec codec = new DockLayoutCodec(persistenceCatalog(), context, shellFit(real));
        DockWorkspace reloaded;
        try {
            reloaded = codec.decode(codec.encode(created)).workspace();
        } catch (DockLayoutFormatException e) {
            throw new AssertionError("默认布局无法读回: " + e.getMessage(), e);
        }

        List<String> expectedRootIds = new ArrayList<String>();
        for (FloatingRoot root : created.roots()) {
            expectedRootIds.add(root.rootId());
        }
        List<String> actualRootIds = new ArrayList<String>();
        for (FloatingRoot root : reloaded.roots()) {
            actualRootIds.add(root.rootId());
        }
        assertEquals(expectedRootIds, actualRootIds, "往返后窗口集合发生变化");

        Map<String, DockRect> before = leafBounds(created, real);
        Map<String, DockRect> after = leafBounds(reloaded, real);
        StringBuilder changes = new StringBuilder();
        for (String moduleId : real.moduleIds()) {
            if (!before.get(moduleId).equals(after.get(moduleId))) {
                changes.append('\n').append("  ").append(moduleId)
                        .append(' ').append(before.get(moduleId)).append(" -> ").append(after.get(moduleId));
            }
        }
        assertEquals("", changes.toString(), "往返后叶子尺寸/位置发生变化:");
    }
}
