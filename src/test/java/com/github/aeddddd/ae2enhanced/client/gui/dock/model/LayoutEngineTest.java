/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class LayoutEngineTest {
    @Test
    void recursivelyArrangesVisibleBranchesAndCollapsesHiddenOnes() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c");
        LayoutNode nested = new SplitNode(
                "split-outer",
                DockAxis.HORIZONTAL,
                0.4,
                new LeafNode("leaf-a", "a", true),
                new SplitNode(
                        "split-inner",
                        DockAxis.VERTICAL,
                        0.5,
                        new LeafNode("leaf-b", "b", true),
                        new LeafNode("leaf-c", "c", true)));
        Map<String, ModuleLayoutPolicy> policies = new LinkedHashMap<String, ModuleLayoutPolicy>();
        policies.put("a", ModuleLayoutPolicy.defaults());
        policies.put("b", new ModuleLayoutPolicy(false, true, true));
        policies.put("c", ModuleLayoutPolicy.defaults());
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-main", new DockRect(0, 0, 200, 100), nested)), policies);

        LayoutProjection projection = new LayoutEngine(
                catalog,
                new LayoutStyle(DockInsets.NONE, 4)).project(workspace);

        // Both sides get their 80px preferred width; the 36px of slack is split 0.4 / 0.6.
        assertEquals(new DockRect(0, 0, 94, 100), projection.visibleLeaf("leaf-a").get().bounds());
        assertEquals(new DockRect(98, 0, 102, 100), projection.visibleLeaf("leaf-c").get().bounds());
        assertTrue(!projection.visibleLeaf("leaf-b").isPresent());
        assertTrue(projection.isEffectivelyVisible("split-inner"));
        assertFalse(projection.isEffectivelyVisible("leaf-b"));
        assertEquals(1, projection.dividers().size(), "collapsed split must not emit a divider");
    }

    @Test
    void preferredWidthsComeFirstAndOnlySlackFollowsTheRatio() {
        Map<String, ModuleMetrics> metrics = new LinkedHashMap<String, ModuleMetrics>();
        metrics.put("wide", new ModuleMetrics(new DockSize(70, 20), new DockSize(70, 20)));
        metrics.put("narrow", new ModuleMetrics(new DockSize(20, 20), new DockSize(20, 20)));
        ModuleCatalog catalog = new ModuleCatalog(metrics);
        LayoutNode split = new SplitNode(
                "split-main",
                DockAxis.HORIZONTAL,
                0.1,
                new LeafNode("leaf-wide", "wide", true),
                new LeafNode("leaf-narrow", "narrow", true));
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-main", new DockRect(0, 0, 100, 40), split)));

        LayoutProjection projection = new LayoutEngine(
                catalog,
                new LayoutStyle(DockInsets.NONE, 4)).project(workspace);

        // A 0.1 ratio cannot squeeze "wide" below its preferred 70px; it only gets 10% of the slack.
        assertEquals(71, projection.visibleLeaf("leaf-wide").get().bounds().width());
        assertEquals(25, projection.visibleLeaf("leaf-narrow").get().bounds().width());
        assertEquals(new DockRect(71, 0, 4, 40), projection.dividers().get(0).bounds());
    }

    @Test
    void retainsRootAndLeafPaintOrder() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c");
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                ModelTestFixtures.root("root-c", "leaf-c", "c", 0),
                ModelTestFixtures.root("root-a", "leaf-a", "a", 20),
                ModelTestFixtures.root("root-b", "leaf-b", "b", 40)));

        LayoutProjection projection = new LayoutEngine(
                catalog,
                new LayoutStyle(DockInsets.NONE, 4)).project(workspace);

        assertEquals(
                Arrays.asList("root-c", "root-a", "root-b"),
                projection.roots().stream().map(LayoutProjection.RootPlacement::rootId)
                        .collect(Collectors.<String>toList()));
        assertEquals(
                Arrays.asList("c", "a", "b"),
                projection.visibleLeavesInPaintOrder().stream()
                        .map(LayoutProjection.LeafPlacement::moduleId)
                        .collect(Collectors.<String>toList()));
    }

    @Test
    void splicedWindowFillsTheRootAsARectangleAndGivesLeftoverToTheExpandingLeaf() {
        Map<String, ModuleMetrics> metrics = new LinkedHashMap<String, ModuleMetrics>();
        metrics.put("fixed", new ModuleMetrics(new DockSize(50, 40), new DockSize(80, 60)));
        metrics.put("flex", new ModuleMetrics(new DockSize(40, 30), new DockSize(90, 80), true));
        ModuleCatalog catalog = new ModuleCatalog(metrics);
        LayoutNode split = new SplitNode(
                "split-main",
                DockAxis.VERTICAL,
                0.5,
                new LeafNode("leaf-fixed", "fixed", true),
                new LeafNode("leaf-flex", "flex", true));
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-main", new DockRect(0, 0, 200, 200), split)));

        LayoutProjection projection = new LayoutEngine(catalog, new LayoutStyle(DockInsets.NONE, 0))
                .project(workspace);

        DockRect fixed = projection.visibleLeaf("leaf-fixed").get().bounds();
        DockRect flex = projection.visibleLeaf("leaf-flex").get().bounds();
        assertEquals(new DockRect(0, 0, 200, 60), fixed);
        assertEquals(new DockRect(0, 60, 200, 140), flex);
        assertEquals(fixed.right(), flex.right());
        assertEquals(fixed.bottom(), flex.y());
        assertEquals(200, flex.bottom());
        assertEquals(1, projection.dividers().size());
    }

    @Test
    void splicedWindowKeepsANestedRowRectangularInsteadOfLShaped() {
        Map<String, ModuleMetrics> metrics = new LinkedHashMap<String, ModuleMetrics>();
        metrics.put("crafting", new ModuleMetrics(new DockSize(100, 60), new DockSize(200, 80)));
        metrics.put("inventory", new ModuleMetrics(new DockSize(80, 60), new DockSize(100, 100)));
        metrics.put("access", new ModuleMetrics(new DockSize(60, 60), new DockSize(150, 100)));
        metrics.put("encoding", new ModuleMetrics(new DockSize(80, 60), new DockSize(120, 80)));
        ModuleCatalog catalog = new ModuleCatalog(metrics);
        LayoutNode left = new SplitNode(
                "split-left",
                DockAxis.VERTICAL,
                0.5,
                new LeafNode("leaf-crafting", "crafting", true),
                new SplitNode(
                        "split-lower",
                        DockAxis.HORIZONTAL,
                        0.5,
                        new LeafNode("leaf-inventory", "inventory", true),
                        new LeafNode("leaf-access", "access", true)));
        LayoutNode rootNode = new SplitNode(
                "split-root",
                DockAxis.HORIZONTAL,
                0.5,
                left,
                new LeafNode("leaf-encoding", "encoding", true));
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-main", new DockRect(0, 0, 450, 180), rootNode)));

        LayoutProjection projection = new LayoutEngine(catalog, new LayoutStyle(DockInsets.NONE, 0))
                .project(workspace);

        DockRect crafting = projection.visibleLeaf("leaf-crafting").get().bounds();
        DockRect inventory = projection.visibleLeaf("leaf-inventory").get().bounds();
        DockRect access = projection.visibleLeaf("leaf-access").get().bounds();
        DockRect encoding = projection.visibleLeaf("leaf-encoding").get().bounds();
        assertEquals(crafting.right(), encoding.x());
        assertEquals(inventory.right(), access.x());
        assertEquals(crafting.bottom(), inventory.y());
        assertEquals(inventory.y(), access.y());
        assertEquals(inventory.bottom(), access.bottom());
        assertEquals(encoding.bottom(), inventory.bottom());
        assertEquals(0, crafting.x());
        assertEquals(0, crafting.y());
        assertEquals(450, encoding.right());
        assertEquals(180, encoding.bottom());
    }

    /**
     * 任务 C：富余高度只给"声明了 expandVertically 的那一侧"，即使它在嵌套子树里也一样；
     * 与之无关的行/列保持各自的内容尺寸（不参与均摊）。
     *
     * <p>布局：{@code V[ H[fixedA | expander] / H[rowMate1 | rowMate2] ]}，根高 400。
     * 第一行的内容高 80（expander 的默认高），第二行内容高 40 —— 第二行不与 expander 同行，
     * 必须原样保持 40；多出来的 280 全部落在第一行。</p>
     */
    @Test
    void leftoverHeightGoesOnlyToTheExpandingSubtree() {
        Map<String, ModuleMetrics> metrics = new LinkedHashMap<String, ModuleMetrics>();
        metrics.put("expander", new ModuleMetrics(new DockSize(40, 30), new DockSize(90, 80), true));
        metrics.put("fixedA", new ModuleMetrics(new DockSize(50, 40), new DockSize(80, 60)));
        metrics.put("rowMate1", new ModuleMetrics(new DockSize(40, 30), new DockSize(60, 40)));
        metrics.put("rowMate2", new ModuleMetrics(new DockSize(40, 30), new DockSize(60, 40)));
        ModuleCatalog catalog = new ModuleCatalog(metrics);
        LayoutNode expandingRow = new SplitNode(
                "split-row1",
                DockAxis.HORIZONTAL,
                0.5,
                new LeafNode("leaf-fixedA", "fixedA", true),
                new LeafNode("leaf-expander", "expander", true));
        LayoutNode plainRow = new SplitNode(
                "split-row2",
                DockAxis.HORIZONTAL,
                0.5,
                new LeafNode("leaf-rowMate1", "rowMate1", true),
                new LeafNode("leaf-rowMate2", "rowMate2", true));
        LayoutNode rootNode = new SplitNode("split-root", DockAxis.VERTICAL, 0.5, expandingRow, plainRow);
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-main", new DockRect(0, 0, 200, 400), rootNode)));

        LayoutProjection projection = new LayoutEngine(catalog, new LayoutStyle(DockInsets.NONE, 0))
                .project(workspace);

        DockRect expander = projection.visibleLeaf("leaf-expander").get().bounds();
        DockRect rowMate1 = projection.visibleLeaf("leaf-rowMate1").get().bounds();
        DockRect rowMate2 = projection.visibleLeaf("leaf-rowMate2").get().bounds();

        assertEquals(40, rowMate1.height(), "不与 expander 同行的面板必须保持内容高度");
        assertEquals(40, rowMate2.height(), "不与 expander 同行的面板必须保持内容高度");
        assertEquals(360, expander.height(), "富余高度全部给含 expander 的那一行");
        assertEquals(360, expander.bottom(), "expander 所在行吃满到根底");
        assertEquals(400, rowMate1.bottom(), "普通行紧贴根底，没有被撑过");
    }
}
