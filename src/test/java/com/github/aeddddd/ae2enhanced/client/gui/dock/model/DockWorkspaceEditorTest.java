/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class DockWorkspaceEditorTest {
    @Test
    void insertsNestedSplitsThenDetachesAndCompressesTheParent() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace initial = workspace();

        DockWorkspace first = editor.insertSplit(
                initial, "leaf-a", "leaf-b", DockEdge.LEFT, "split-ab", 0.4);
        assertEquals(Arrays.asList("root-b", "root-c"), rootIds(first));
        SplitNode splitAb = assertInstanceOf(SplitNode.class, first.roots().get(0).content());
        assertEquals("leaf-a", splitAb.first().nodeId());
        assertEquals("leaf-b", splitAb.second().nodeId());

        DockWorkspace nested = editor.insertSplit(
                first, "leaf-c", "leaf-b", DockEdge.BOTTOM, "split-bc", 0.5);
        assertEquals(Arrays.asList("root-b"), rootIds(nested));
        SplitNode outer = assertInstanceOf(SplitNode.class, nested.roots().get(0).content());
        SplitNode inner = assertInstanceOf(SplitNode.class, outer.second());
        assertEquals("leaf-b", inner.first().nodeId());
        assertEquals("leaf-c", inner.second().nodeId());

        DockWorkspace detached = editor.detach(
                nested, "split-bc", "root-bc", new DockRect(200, 40, 160, 100));
        assertEquals(Arrays.asList("root-b", "root-bc"), rootIds(detached));
        assertEquals("leaf-a", detached.roots().get(0).content().nodeId());
        assertEquals("split-bc", detached.roots().get(detached.roots().size() - 1).content().nodeId());
    }

    @Test
    void hidingLeafPreservesStructureButChangesEffectiveVisibility() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace joined = editor.insertSplit(
                workspace(), "leaf-a", "leaf-b", DockEdge.LEFT, "split-ab", 0.5);

        DockWorkspace hidden = editor.setLeafVisible(
                joined, "leaf-a", false, joined.roots().get(0).bounds(), null, null);
        SplitNode split = assertInstanceOf(SplitNode.class, hidden.roots().get(0).content());
        assertEquals("split-ab", split.nodeId(), "visibility must not rewrite the tree");
        assertTrue(((LeafNode) split.first()).visible(), "tree visibility is normalized structural data");
        assertFalse(hidden.policyFor("a").visible());

        LayoutProjection projection = new LayoutEngine(
                catalog,
                new LayoutStyle(DockInsets.NONE, 4)).project(hidden);
        assertFalse(projection.isEffectivelyVisible("leaf-a"));
        assertEquals(
                hidden.roots().get(0).bounds(),
                projection.visibleLeaf("leaf-b").get().bounds());
    }

    @Test
    void hidingASplicedLeafShrinksTheHostAndShowingRestoresStandaloneSize() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace joined = editor.insertSplit(
                new DockWorkspace(Arrays.asList(
                        new FloatingRoot("root-a", new DockRect(10, 20, 200, 80), new LeafNode("leaf-a", "a", true)),
                        new FloatingRoot("root-b", new DockRect(240, 20, 90, 60), new LeafNode("leaf-b", "b", true)))),
                "leaf-b", "leaf-a", DockEdge.RIGHT, "split-ab", 0.31);
        joined = editor.setRootBounds(joined, "root-a", new DockRect(10, 20, 294, 80));

        DockWorkspace hidden = editor.setLeafVisible(
                joined, "leaf-b", false, new DockRect(214, 20, 90, 80), null, null);
        assertEquals(1, hidden.roots().size());
        assertEquals(new DockRect(10, 20, 204, 80), hidden.roots().get(0).bounds());
        assertEquals(new DockSize(90, 80), hidden.restoreSizes().get("leaf-b"));
        assertFalse(hidden.policyFor("b").visible());

        DockWorkspace shown = editor.setLeafVisible(
                hidden, "leaf-b", true, null, "root-b2", new DockRect(214, 20, 80, 80));
        assertEquals(2, shown.roots().size());
        assertEquals(new DockRect(10, 20, 204, 80), shown.roots().get(0).bounds());
        assertEquals(new DockRect(214, 20, 90, 80), shown.roots().get(shown.roots().size() - 1).bounds());
        assertTrue(shown.policyFor("b").visible());
        assertEquals("leaf-b", shown.roots().get(shown.roots().size() - 1).content().nodeId());
    }

    @Test
    void rejectsDockingAnAncestorWithItsDescendant() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace joined = editor.insertSplit(
                workspace(), "leaf-a", "leaf-b", DockEdge.LEFT, "split-ab", 0.5);

        assertThrows(
                IllegalArgumentException.class,
                () -> editor.insertSplit(
                        joined, "leaf-a", "split-ab", DockEdge.LEFT, "split-new", 0.5));
    }

    @Test
    void raisesRootWithoutReorderingOthers() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c");
        DockWorkspace raised = new DockWorkspaceEditor(catalog).raiseRoot(workspace(), "root-a");
        assertEquals(Arrays.asList("root-b", "root-c", "root-a"), rootIds(raised));
    }

    @Test
    void updatesNestedSplitRatioWithoutRewritingItsChildren() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace first = editor.insertSplit(
                workspace(), "leaf-a", "leaf-b", DockEdge.LEFT, "split-ab", 0.4);
        DockWorkspace nested = editor.insertSplit(
                first, "leaf-c", "leaf-b", DockEdge.BOTTOM, "split-bc", 0.5);

        DockWorkspace resized = editor.setSplitRatio(nested, "split-bc", 0.7);

        SplitNode outer = assertInstanceOf(SplitNode.class, resized.roots().get(0).content());
        SplitNode inner = assertInstanceOf(SplitNode.class, outer.second());
        assertEquals(0.7, inner.ratio());
        assertEquals("leaf-b", inner.first().nodeId());
        assertEquals("leaf-c", inner.second().nodeId());
    }

    @Test
    void supportsFourLevelsAndDetachingADeepSubtree() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c", "d", "e");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace current = new DockWorkspace(Arrays.asList(
                ModelTestFixtures.root("root-a", "leaf-a", "a", 0),
                ModelTestFixtures.root("root-b", "leaf-b", "b", 20),
                ModelTestFixtures.root("root-c", "leaf-c", "c", 40),
                ModelTestFixtures.root("root-d", "leaf-d", "d", 60),
                ModelTestFixtures.root("root-e", "leaf-e", "e", 80)));
        current = editor.insertSplit(current, "leaf-a", "leaf-b", DockEdge.LEFT, "split-ab", 0.5);
        current = editor.insertSplit(current, "leaf-c", "leaf-b", DockEdge.TOP, "split-bc", 0.5);
        current = editor.insertSplit(current, "leaf-d", "leaf-b", DockEdge.RIGHT, "split-bd", 0.5);
        current = editor.insertSplit(current, "leaf-e", "leaf-b", DockEdge.BOTTOM, "split-be", 0.5);

        LayoutProjection projection = new LayoutEngine(
                catalog,
                new LayoutStyle(DockInsets.NONE, 4)).project(current);
        assertEquals(5, projection.visibleLeavesInPaintOrder().size());

        DockWorkspace detached = editor.detach(
                current, "split-be", "root-be", new DockRect(200, 40, 160, 100));
        WorkspaceValidator.validateStrict(detached, catalog);
        assertEquals(2, detached.roots().size());
        assertEquals("split-be", detached.roots().get(detached.roots().size() - 1).content().nodeId());
    }

    @Test
    void centersNamedRootsAsOneGroupAndLeavesOthersPut() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-a", new DockRect(0, 10, 100, 80), new LeafNode("leaf-a", "a", true)),
                new FloatingRoot("root-b", new DockRect(100, 10, 100, 80), new LeafNode("leaf-b", "b", true)),
                new FloatingRoot("root-c", new DockRect(20, 200, 80, 60), new LeafNode("leaf-c", "c", true))));

        DockWorkspace centered = editor.centerRoots(workspace, Arrays.asList("root-a", "root-b"), 400, 240);

        assertEquals(new DockRect(100, 80, 100, 80), centered.roots().get(0).bounds());
        assertEquals(new DockRect(200, 80, 100, 80), centered.roots().get(1).bounds());
        assertEquals(new DockRect(20, 200, 80, 60), centered.roots().get(2).bounds());
    }

    @Test
    void centerRootsIsANoOpWhenTheGroupIsAlreadyCentered() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-a", new DockRect(150, 80, 100, 80), new LeafNode("leaf-a", "a", true))));

        DockWorkspace centered = editor.centerRoots(workspace, Arrays.asList("root-a"), 400, 240);

        assertEquals(workspace, centered);
    }

    @Test
    void batchRootBoundsPreservesOrderAndReturnsIdentityForNoOp() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace initial = new DockWorkspace(Arrays.asList(
                ModelTestFixtures.root("root-a", "leaf-a", "a", 0),
                ModelTestFixtures.root("root-b", "leaf-b", "b", 20)));

        assertSame(initial, editor.setRootBounds(initial, Collections.<String, DockRect>emptyMap()));
        DockWorkspace moved = editor.setRootBounds(initial, batchBounds());
        assertEquals(
                Arrays.asList("root-a", "root-b"),
                moved.roots().stream().map(FloatingRoot::rootId).collect(Collectors.<String>toList()));
        assertEquals(new DockRect(50, 60, 100, 80), moved.roots().get(1).bounds());
    }

    private static Map<String, DockRect> batchBounds() {
        Map<String, DockRect> bounds = new java.util.LinkedHashMap<String, DockRect>();
        bounds.put("root-a", new DockRect(30, 40, 100, 80));
        bounds.put("root-b", new DockRect(50, 60, 100, 80));
        return bounds;
    }

    @Test
    void centerRootsUsesProvidedChromeUnionNotTheFullRoot() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-a", new DockRect(0, 10, 122, 80), new LeafNode("leaf-a", "a", true))));

        DockWorkspace centered = editor.centerRoots(
                workspace, Arrays.asList("root-a"), new DockRect(0, 10, 100, 80), 400, 240);

        assertEquals(new DockRect(150, 80, 122, 80), centered.roots().get(0).bounds());
    }

    @Test
    void spliceRemembersStandaloneSizesForLaterDetach() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace initial = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-a", new DockRect(10, 20, 200, 80), new LeafNode("leaf-a", "a", true)),
                new FloatingRoot("root-b", new DockRect(240, 30, 90, 60), new LeafNode("leaf-b", "b", true))));

        DockWorkspace joined = editor.insertSplit(
                initial, "leaf-b", "leaf-a", DockEdge.RIGHT, "split-ab", 0.31);
        joined = editor.setRootBounds(joined, "root-a", new DockRect(10, 20, 294, 80));

        assertEquals(new DockSize(200, 80), joined.restoreSizes().get("leaf-a"));
        assertEquals(new DockSize(90, 60), joined.restoreSizes().get("leaf-b"));
        assertEquals(
                new DockRect(214, 20, 90, 60),
                joined.restoredBounds("leaf-b", new DockRect(214, 20, 80, 80)));
        assertEquals(
                new DockRect(10, 20, 200, 80),
                joined.restoredBounds("leaf-a", new DockRect(10, 20, 294, 80)));

        DockWorkspace detached = editor.detach(
                joined, "leaf-b", "root-b2", new DockRect(214, 20, 80, 80));
        assertEquals(new DockRect(214, 20, 90, 60), detached.roots().get(detached.roots().size() - 1).bounds());
        assertEquals("leaf-a", detached.roots().get(0).content().nodeId());
    }

    private static DockWorkspace workspace() {
        return new DockWorkspace(Arrays.asList(
                ModelTestFixtures.root("root-a", "leaf-a", "a", 0),
                ModelTestFixtures.root("root-b", "leaf-b", "b", 20),
                ModelTestFixtures.root("root-c", "leaf-c", "c", 40)));
    }

    private static List<String> rootIds(DockWorkspace workspace) {
        return workspace.roots().stream().map(FloatingRoot::rootId).collect(Collectors.<String>toList());
    }

    @Test
    void windowModeFollowsTheWindowThroughSpliceAndDetach() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace floated = editor.setRootMode(workspace(), "root-b", true, true);
        assertTrue(floated.roots().get(1).floating());
        assertTrue(floated.roots().get(1).pinned());

        // Splicing an anchored window into a floating one keeps the host's mode.
        DockWorkspace joined = editor.insertSplit(floated, "leaf-a", "leaf-b", DockEdge.LEFT, "split-ab", 0.5);
        FloatingRoot host = joined.roots().get(0);
        assertEquals("root-b", host.rootId());
        assertTrue(host.floating());

        // A section taken out of an anchored window floats on its own.
        DockWorkspace docked = editor.setRootMode(joined, "root-b", false, true);
        assertFalse(docked.roots().get(0).floating());
        assertFalse(docked.roots().get(0).pinned(), "docking drops the pin");
        DockWorkspace detached = editor.detach(docked, "leaf-a", "root-a2", new DockRect(0, 0, 80, 60));
        assertFalse(detached.roots().get(0).floating());
        assertTrue(detached.roots().get(detached.roots().size() - 1).floating());
    }

    @Test
    void threeSplicedFloatingModulesToggleIndependently() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c", "d", "e", "f", "g");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        List<FloatingRoot> roots = new ArrayList<FloatingRoot>();
        for (int i = 0; i < 7; i++) {
            String id = String.valueOf((char) ('a' + i));
            roots.add(new FloatingRoot("root-" + id, new DockRect(i * 120, 0, 110, 80),
                    new LeafNode("leaf-" + id, id, true), i != 0, false));
        }
        DockWorkspace joined = new DockWorkspace(roots);
        joined = editor.insertSplit(joined, "leaf-c", "leaf-b", DockEdge.RIGHT, "split-bc", 0.5);
        joined = editor.insertSplit(joined, "leaf-e", "leaf-d", DockEdge.RIGHT, "split-de", 0.5);
        joined = editor.insertSplit(joined, "leaf-g", "leaf-f", DockEdge.RIGHT, "split-fg", 0.5);
        for (int group = 1; group <= FloatingRoot.MAX_GROUPS; group++) {
            joined = editor.setRootGroup(joined, "root-" + (char) ('a' + group * 2 - 1), group);
        }
        assertEquals(
                Arrays.asList(0, 1, 2, 3),
                joined.roots().stream().map(FloatingRoot::group).collect(Collectors.<Integer>toList()));

        DockWorkspace hidden = editor.setRootHidden(joined, "root-d", true);
        LayoutProjection projection = new LayoutEngine(catalog, new LayoutStyle(DockInsets.NONE, 4)).project(hidden);
        assertTrue(projection.isEffectivelyVisible("leaf-b"));
        assertTrue(projection.isEffectivelyVisible("leaf-c"));
        assertFalse(projection.isEffectivelyVisible("leaf-d"));
        assertFalse(projection.isEffectivelyVisible("leaf-e"));
        assertTrue(projection.isEffectivelyVisible("leaf-f"));
        assertTrue(projection.isEffectivelyVisible("leaf-g"));
        assertEquals(2, editor.setRootHidden(hidden, "root-d", false).roots().get(2).group());
    }

    @Test
    void combinedModuleSlotsAreUniqueAndOnlyForFloatingWindows() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b", "c");
        DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
        DockWorkspace floated = editor.setRootMode(
                editor.setRootMode(workspace(), "root-a", true, false), "root-b", true, false);

        DockWorkspace first = editor.setRootGroup(floated, "root-a", 1);
        DockWorkspace moved = editor.setRootGroup(first, "root-b", 1);
        assertEquals(0, moved.roots().get(0).group(), "slot 1 moves to the newer window");
        assertEquals(1, moved.roots().get(1).group());

        DockWorkspace anchored = editor.setRootGroup(floated, "root-c", 2);
        assertEquals(0, anchored.roots().get(2).group(), "an anchored window cannot be a combined module");

        DockWorkspace hidden = editor.setRootHidden(moved, "root-b", true);
        assertTrue(hidden.roots().get(1).hidden());
        DockWorkspace released = editor.setRootGroup(hidden, "root-b", 0);
        assertFalse(released.roots().get(1).hidden(), "releasing a slot shows the window again");
        DockWorkspace docked = editor.setRootMode(hidden, "root-b", false, false);
        assertEquals(0, docked.roots().get(1).group(), "docking back drops the slot");
    }
}
