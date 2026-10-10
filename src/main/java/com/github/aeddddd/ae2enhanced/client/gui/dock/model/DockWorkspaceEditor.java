/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;

/** Immutable tree mutations used by drag, splice, detach, hide and Z-order controls. */
public final class DockWorkspaceEditor {
    private final ModuleCatalog catalog;

    public DockWorkspaceEditor(ModuleCatalog catalog) {
        if (catalog == null) {
            throw new NullPointerException("catalog");
        }
        this.catalog = catalog;
    }

    public DockWorkspace insertSplit(
            DockWorkspace workspace,
            String draggedNodeId,
            String targetNodeId,
            DockEdge edge,
            String splitNodeId,
            double ratio) {
        validate(workspace);
        NodeIds.requireValid(draggedNodeId, "draggedNodeId");
        NodeIds.requireValid(targetNodeId, "targetNodeId");
        NodeIds.requireValid(splitNodeId, "splitNodeId");
        if (edge == null) {
            throw new NullPointerException("edge");
        }
        if (draggedNodeId.equals(targetNodeId)) {
            throw new IllegalArgumentException("cannot split a node with itself");
        }
        if (identifierExists(workspace, splitNodeId)) {
            throw new IllegalArgumentException("split identifier already exists: " + splitNodeId);
        }

        LayoutNode dragged = findRequired(workspace, draggedNodeId);
        LayoutNode target = findRequired(workspace, targetNodeId);
        if (LayoutTrees.contains(dragged, targetNodeId) || LayoutTrees.contains(target, draggedNodeId)) {
            throw new IllegalArgumentException("dragged and target subtrees must be disjoint");
        }

        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots());
        int draggedRootIndex = rootIndexContaining(roots, draggedNodeId);
        FloatingRoot draggedRoot = roots.get(draggedRootIndex);
        FloatingRoot hostRoot = roots.get(rootIndexContaining(roots, targetNodeId));
        String hostContentId = hostRoot.content().nodeId();
        DockSize hostRestore = hostRoot.bounds().size();
        Removal removal = remove(draggedRoot.content(), draggedNodeId);
        if (removal.remaining() == null) {
            roots.remove(draggedRootIndex);
        } else {
            roots.set(draggedRootIndex, draggedRoot.withContent(removal.remaining()));
        }

        int targetRootIndex = rootIndexContaining(roots, targetNodeId);
        FloatingRoot targetRoot = roots.get(targetRootIndex);
        LayoutNode first = edge.draggedFirst() ? removal.removed() : target;
        LayoutNode second = edge.draggedFirst() ? target : removal.removed();
        LayoutNode replacement = new SplitNode(splitNodeId, edge.axis(), ratio, first, second);
        Replace replacementResult = replace(targetRoot.content(), targetNodeId, replacement);
        if (!replacementResult.replaced()) {
            throw new IllegalStateException("target disappeared during split insertion");
        }
        roots.set(targetRootIndex, targetRoot.withContent(replacementResult.node()));

        Map<String, DockSize> restoreSizes = new java.util.LinkedHashMap<String, DockSize>(workspace.restoreSizes());
        if (draggedRoot.content().nodeId().equals(draggedNodeId)) {
            restoreSizes.put(draggedNodeId, draggedRoot.bounds().size());
        }
        restoreSizes.put(hostContentId, hostRestore);
        return validated(workspace.rebuilt(
                roots, DockWorkspace.retainRestoreSizes(restoreSizes, roots)));
    }

    public DockWorkspace detach(
            DockWorkspace workspace,
            String nodeId,
            String newRootId,
            DockRect newBounds) {
        validate(workspace);
        NodeIds.requireValid(nodeId, "nodeId");
        NodeIds.requireValid(newRootId, "newRootId");
        if (identifierExists(workspace, newRootId)) {
            throw new IllegalArgumentException("root identifier already exists: " + newRootId);
        }

        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots());
        int rootIndex = rootIndexContaining(roots, nodeId);
        FloatingRoot sourceRoot = roots.get(rootIndex);
        if (sourceRoot.content().nodeId().equals(nodeId)) {
            throw new IllegalArgumentException("node is already a floating root");
        }
        Removal removal = remove(sourceRoot.content(), nodeId);
        if (removal.removed() == null || removal.remaining() == null) {
            throw new IllegalStateException("failed to detach node " + nodeId);
        }
        roots.set(rootIndex, sourceRoot.withContent(removal.remaining()));
        // A branch taken out of a window always floats on its own; dock it back explicitly.
        roots.add(new FloatingRoot(
                newRootId,
                workspace.restoredBounds(removal.removed().nodeId(), newBounds),
                removal.removed(),
                true,
                false));
        return copyWithRoots(workspace, roots);
    }

    public DockWorkspace setLeafVisible(DockWorkspace workspace, String leafNodeId, boolean visible) {
        return setLeafVisible(workspace, leafNodeId, visible, null, null, null);
    }

    /**
     * Toggles a leaf's policy visibility and, when it lives in a split, restores geometry.
     *
     * <p>Hiding a spliced leaf shrinks the host to the remaining visible branch and remembers
     * the hidden leaf's last projected size. Showing it again reopens a standalone window at
     * that size instead of filling the combined host.
     */
    public DockWorkspace setLeafVisible(
            DockWorkspace workspace,
            String leafNodeId,
            boolean visible,
            DockRect hiddenBounds,
            String revealedRootId,
            DockRect revealedFallback) {
        validate(workspace);
        LayoutNode node = findRequired(workspace, leafNodeId);
        if (!(node instanceof LeafNode)) {
            throw new IllegalArgumentException("node is not a leaf: " + leafNodeId);
        }
        LeafNode leaf = (LeafNode) node;
        Map<String, ModuleLayoutPolicy> policies =
                new java.util.LinkedHashMap<String, ModuleLayoutPolicy>(workspace.policies());
        ModuleLayoutPolicy current = workspace.policyFor(leaf.moduleId());
        policies.put(
                leaf.moduleId(),
                new ModuleLayoutPolicy(
                        visible,
                        current.movable(),
                        current.resizable(),
                        current.floating(),
                        current.pinned(),
                        current.showTerminalButton()));
        DockWorkspace next = workspace.withPolicies(policies);
        if (current.visible() == visible) {
            return validated(next);
        }
        if (!visible) {
            return validated(rememberHiddenLeaf(next, leafNodeId, hiddenBounds));
        }
        FloatingRoot host = workspace.roots().get(
                rootIndexContaining(new ArrayList<FloatingRoot>(workspace.roots()), leafNodeId));
        if (host.content().nodeId().equals(leafNodeId)) {
            return validated(next);
        }
        return validated(revealHiddenLeaf(next, leafNodeId, revealedRootId, revealedFallback));
    }

    public DockWorkspace setRootMode(DockWorkspace workspace, String rootId, boolean floating, boolean pinned) {
        return updateRoot(workspace, rootId, root -> root.withMode(floating, pinned));
    }

    /** Makes the window combined module {@code group}, taking the slot from any other window. */
    public DockWorkspace setRootGroup(DockWorkspace workspace, String rootId, int group) {
        validate(workspace);
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots());
        for (int i = 0; i < roots.size(); i++) {
            FloatingRoot root = roots.get(i);
            if (group != 0 && root.group() == group && !root.rootId().equals(rootId)) {
                roots.set(i, root.withGroup(0));
            }
        }
        DockWorkspace cleared = copyWithRoots(workspace, roots);
        return updateRoot(cleared, rootId, root -> root.withGroup(group));
    }

    public DockWorkspace setRootHidden(DockWorkspace workspace, String rootId, boolean hidden) {
        return updateRoot(workspace, rootId, root -> root.withHidden(hidden));
    }

    private DockWorkspace updateRoot(DockWorkspace workspace, String rootId,
            java.util.function.UnaryOperator<FloatingRoot> change) {
        validate(workspace);
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots());
        for (int i = 0; i < roots.size(); i++) {
            if (roots.get(i).rootId().equals(rootId)) {
                roots.set(i, change.apply(roots.get(i)));
                return copyWithRoots(workspace, roots);
            }
        }
        throw new IllegalArgumentException("unknown root: " + rootId);
    }

    public DockWorkspace setRootBounds(DockWorkspace workspace, String rootId, DockRect bounds) {
        validate(workspace);
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots());
        for (int i = 0; i < roots.size(); i++) {
            if (roots.get(i).rootId().equals(rootId)) {
                roots.set(i, roots.get(i).withBounds(bounds));
                return copyWithRoots(workspace, roots);
            }
        }
        throw new IllegalArgumentException("unknown root: " + rootId);
    }

    /** Updates several root bounds in one validated immutable workspace rebuild. */
    public DockWorkspace setRootBounds(DockWorkspace workspace, Map<String, DockRect> boundsByRoot) {
        validate(workspace);
        if (boundsByRoot == null || boundsByRoot.isEmpty()) {
            return workspace;
        }
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots());
        boolean changed = false;
        for (int i = 0; i < roots.size(); i++) {
            FloatingRoot root = roots.get(i);
            DockRect bounds = boundsByRoot.get(root.rootId());
            if (bounds != null && !bounds.equals(root.bounds())) {
                roots.set(i, root.withBounds(bounds));
                changed = true;
            }
        }
        return changed ? copyWithRoots(workspace, roots) : workspace;
    }

    /**
     * Translates the named roots as one group so {@code union} is centered on the screen.
     * Roots not in {@code rootIds} keep their current bounds.
     */
    public DockWorkspace centerRoots(
            DockWorkspace workspace,
            Collection<String> rootIds,
            DockRect union,
            int screenWidth,
            int screenHeight) {
        validate(workspace);
        if (rootIds == null || rootIds.isEmpty() || union == null
                || union.width() <= 0 || union.height() <= 0
                || screenWidth <= 0 || screenHeight <= 0) {
            return workspace;
        }
        int dx = (screenWidth - union.width()) / 2 - union.x();
        int dy = (screenHeight - union.height()) / 2 - union.y();
        return translateRoots(workspace, rootIds, dx, dy);
    }

    public DockWorkspace centerRoots(
            DockWorkspace workspace,
            Collection<String> rootIds,
            int screenWidth,
            int screenHeight) {
        validate(workspace);
        if (rootIds == null || rootIds.isEmpty()) {
            return workspace;
        }
        Set<String> ids = new LinkedHashSet<String>(rootIds);
        DockRect union = null;
        for (FloatingRoot root : workspace.roots()) {
            if (!ids.contains(root.rootId())) {
                continue;
            }
            union = union == null ? root.bounds() : union.union(root.bounds());
        }
        return centerRoots(workspace, ids, union, screenWidth, screenHeight);
    }

    public DockWorkspace translateRoots(DockWorkspace workspace, Collection<String> rootIds, int dx, int dy) {
        validate(workspace);
        if (rootIds == null || rootIds.isEmpty() || (dx == 0 && dy == 0)) {
            return workspace;
        }
        Set<String> ids = new LinkedHashSet<String>(rootIds);
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots().size());
        boolean moved = false;
        for (FloatingRoot root : workspace.roots()) {
            if (!ids.contains(root.rootId())) {
                roots.add(root);
                continue;
            }
            DockRect bounds = root.bounds();
            roots.add(root.withBounds(new DockRect(
                    bounds.x() + dx, bounds.y() + dy, bounds.width(), bounds.height())));
            moved = true;
        }
        return moved ? copyWithRoots(workspace, roots) : workspace;
    }

    public DockWorkspace setSplitRatio(DockWorkspace workspace, String splitNodeId, double ratio) {
        validate(workspace);
        NodeIds.requireValid(splitNodeId, "splitNodeId");
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots());
        for (int i = 0; i < roots.size(); i++) {
            FloatingRoot root = roots.get(i);
            RatioChange change = setRatio(root.content(), splitNodeId, ratio);
            if (change.found()) {
                roots.set(i, root.withContent(change.node()));
                return copyWithRoots(workspace, roots);
            }
        }
        throw new IllegalArgumentException("unknown split node: " + splitNodeId);
    }

    public DockWorkspace raiseRoot(DockWorkspace workspace, String rootId) {
        validate(workspace);
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots());
        for (int i = 0; i < roots.size(); i++) {
            if (roots.get(i).rootId().equals(rootId)) {
                FloatingRoot root = roots.remove(i);
                roots.add(root);
                return copyWithRoots(workspace, roots);
            }
        }
        throw new IllegalArgumentException("unknown root: " + rootId);
    }

    private void validate(DockWorkspace workspace) {
        WorkspaceValidator.validateStrict(workspace, catalog);
    }

    private DockWorkspace validated(DockWorkspace workspace) {
        validate(workspace);
        return workspace;
    }

    private static DockWorkspace rememberHiddenLeaf(
            DockWorkspace workspace,
            String hiddenNodeId,
            DockRect hiddenBounds) {
        int rootIndex = rootIndexContaining(new ArrayList<FloatingRoot>(workspace.roots()), hiddenNodeId);
        FloatingRoot host = workspace.roots().get(rootIndex);
        Map<String, DockSize> restoreSizes =
                new java.util.LinkedHashMap<String, DockSize>(workspace.restoreSizes());
        if (hiddenBounds != null && hiddenBounds.width() > 0 && hiddenBounds.height() > 0) {
            restoreSizes.put(hiddenNodeId, hiddenBounds.size());
        }
        if (host.content().nodeId().equals(hiddenNodeId)) {
            return workspace.withRestoreSizes(restoreSizes);
        }
        LayoutNode remaining = remainingVisibleContent(host.content(), hiddenNodeId, workspace);
        DockRect remainingBounds = remaining == null
                ? host.bounds()
                : remainingBoundsAfterHide(host.bounds(), hiddenBounds);
        if (remaining != null && remainingBounds.width() > 0 && remainingBounds.height() > 0) {
            restoreSizes.put(remaining.nodeId(), remainingBounds.size());
        } else {
            remainingBounds = host.bounds();
        }
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots());
        roots.set(rootIndex, host.withBounds(remainingBounds));
        return workspace.rebuilt(roots, restoreSizes);
    }

    private DockWorkspace revealHiddenLeaf(
            DockWorkspace workspace,
            String leafNodeId,
            String revealedRootId,
            DockRect revealedFallback) {
        int rootIndex = rootIndexContaining(new ArrayList<FloatingRoot>(workspace.roots()), leafNodeId);
        FloatingRoot host = workspace.roots().get(rootIndex);
        if (host.content().nodeId().equals(leafNodeId)) {
            return workspace;
        }
        if (revealedRootId == null || identifierExists(workspace, revealedRootId)) {
            revealedRootId = uniqueRootId(workspace, leafNodeId);
        }
        DockRect fallback = revealedFallback != null && revealedFallback.width() > 0 && revealedFallback.height() > 0
                ? revealedFallback
                : catalogDefaultBounds(workspace, leafNodeId, host.bounds());
        DockRect revealedBounds = workspace.restoredBounds(leafNodeId, fallback);
        Removal removal = remove(host.content(), leafNodeId);
        if (removal.removed() == null || removal.remaining() == null) {
            throw new IllegalStateException("failed to reveal node " + leafNodeId);
        }
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>(workspace.roots());
        roots.set(rootIndex, host.withContent(removal.remaining()));
        ModuleLayoutPolicy preferred =
                workspace.policyFor(((LeafNode) findRequired(workspace, leafNodeId)).moduleId());
        roots.add(new FloatingRoot(revealedRootId, revealedBounds, removal.removed(),
                preferred.floating(), preferred.pinned()));
        return copyWithRoots(workspace, roots);
    }

    private DockRect catalogDefaultBounds(DockWorkspace workspace, String leafNodeId, DockRect hostBounds) {
        LayoutNode node = findRequired(workspace, leafNodeId);
        if (!(node instanceof LeafNode)) {
            return hostBounds;
        }
        LeafNode leaf = (LeafNode) node;
        DockSize size = catalog.metrics(leaf.moduleId()).defaultSize();
        int x = hostBounds == null ? 0 : hostBounds.right() + 4;
        int y = hostBounds == null ? 0 : hostBounds.y();
        return new DockRect(x, y, Math.max(1, size.width()), Math.max(1, size.height()));
    }

    private static String uniqueRootId(DockWorkspace workspace, String leafNodeId) {
        String preferred = "root:" + leafNodeId;
        if (!identifierExists(workspace, preferred)) {
            return preferred;
        }
        int suffix = 1;
        String candidate;
        do {
            candidate = preferred + ":" + suffix++;
        } while (identifierExists(workspace, candidate));
        return candidate;
    }

    private static LayoutNode remainingVisibleContent(
            LayoutNode node,
            String hiddenNodeId,
            DockWorkspace workspace) {
        if (node.nodeId().equals(hiddenNodeId)) {
            return null;
        }
        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            return workspace.policyFor(leaf.moduleId()).visible() ? leaf : null;
        }
        SplitNode split = (SplitNode) node;
        LayoutNode first = remainingVisibleContent(split.first(), hiddenNodeId, workspace);
        LayoutNode second = remainingVisibleContent(split.second(), hiddenNodeId, workspace);
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return split.withChildren(first, second);
    }

    private static DockRect remainingBoundsAfterHide(DockRect host, DockRect hidden) {
        if (host == null) {
            return null;
        }
        if (hidden == null || hidden.width() <= 0 || hidden.height() <= 0) {
            return host;
        }
        boolean spansHeight = hidden.y() <= host.y() && hidden.bottom() >= host.bottom();
        boolean spansWidth = hidden.x() <= host.x() && hidden.right() >= host.right();
        if (spansHeight && hidden.width() < host.width()) {
            if (hidden.x() <= host.x()) {
                int width = host.right() - hidden.right();
                if (width > 0) {
                    return new DockRect(hidden.right(), host.y(), width, host.height());
                }
            } else if (hidden.right() >= host.right()) {
                int width = hidden.x() - host.x();
                if (width > 0) {
                    return new DockRect(host.x(), host.y(), width, host.height());
                }
            }
        }
        if (spansWidth && hidden.height() < host.height()) {
            if (hidden.y() <= host.y()) {
                int height = host.bottom() - hidden.bottom();
                if (height > 0) {
                    return new DockRect(host.x(), hidden.bottom(), host.width(), height);
                }
            } else if (hidden.bottom() >= host.bottom()) {
                int height = hidden.y() - host.y();
                if (height > 0) {
                    return new DockRect(host.x(), host.y(), host.width(), height);
                }
            }
        }
        return host;
    }

    private DockWorkspace copyWithRoots(DockWorkspace workspace, ArrayList<FloatingRoot> roots) {
        return validated(workspace.rebuilt(
                roots, DockWorkspace.retainRestoreSizes(workspace.restoreSizes(), roots)));
    }

    private static LayoutNode findRequired(DockWorkspace workspace, String nodeId) {
        for (FloatingRoot root : workspace.roots()) {
            java.util.Optional<LayoutNode> found = LayoutTrees.find(root.content(), nodeId);
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw new IllegalArgumentException("unknown node: " + nodeId);
    }

    private static int rootIndexContaining(ArrayList<FloatingRoot> roots, String nodeId) {
        for (int i = 0; i < roots.size(); i++) {
            if (LayoutTrees.contains(roots.get(i).content(), nodeId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("unknown node: " + nodeId);
    }

    private static boolean identifierExists(DockWorkspace workspace, String identifier) {
        for (FloatingRoot root : workspace.roots()) {
            if (root.rootId().equals(identifier) || LayoutTrees.contains(root.content(), identifier)) {
                return true;
            }
        }
        return false;
    }

    private static Removal remove(LayoutNode node, String nodeId) {
        if (node.nodeId().equals(nodeId)) {
            return new Removal(null, node);
        }
        if (!(node instanceof SplitNode)) {
            return new Removal(node, null);
        }
        SplitNode split = (SplitNode) node;

        Removal firstRemoval = remove(split.first(), nodeId);
        if (firstRemoval.removed() != null) {
            LayoutNode remaining = firstRemoval.remaining() == null
                    ? split.second()
                    : split.withChildren(firstRemoval.remaining(), split.second());
            return new Removal(remaining, firstRemoval.removed());
        }

        Removal secondRemoval = remove(split.second(), nodeId);
        if (secondRemoval.removed() != null) {
            LayoutNode remaining = secondRemoval.remaining() == null
                    ? split.first()
                    : split.withChildren(split.first(), secondRemoval.remaining());
            return new Removal(remaining, secondRemoval.removed());
        }
        return new Removal(node, null);
    }

    private static Replace replace(LayoutNode node, String nodeId, LayoutNode replacement) {
        if (node.nodeId().equals(nodeId)) {
            return new Replace(replacement, true);
        }
        if (!(node instanceof SplitNode)) {
            return new Replace(node, false);
        }
        SplitNode split = (SplitNode) node;
        Replace first = replace(split.first(), nodeId, replacement);
        if (first.replaced()) {
            return new Replace(split.withChildren(first.node(), split.second()), true);
        }
        Replace second = replace(split.second(), nodeId, replacement);
        if (second.replaced()) {
            return new Replace(split.withChildren(split.first(), second.node()), true);
        }
        return new Replace(node, false);
    }

    private static RatioChange setRatio(LayoutNode node, String splitNodeId, double ratio) {
        if (node.nodeId().equals(splitNodeId)) {
            if (!(node instanceof SplitNode)) {
                throw new IllegalArgumentException("node is not a split: " + splitNodeId);
            }
            return new RatioChange(((SplitNode) node).withRatio(ratio), true);
        }
        if (!(node instanceof SplitNode)) {
            return new RatioChange(node, false);
        }
        SplitNode split = (SplitNode) node;
        RatioChange first = setRatio(split.first(), splitNodeId, ratio);
        if (first.found()) {
            return new RatioChange(split.withChildren(first.node(), split.second()), true);
        }
        RatioChange second = setRatio(split.second(), splitNodeId, ratio);
        if (second.found()) {
            return new RatioChange(split.withChildren(split.first(), second.node()), true);
        }
        return new RatioChange(node, false);
    }

    /** 原为私有 record，仅在本类内部按值传递。 */
    private static final class Removal {
        private final LayoutNode remaining;
        private final LayoutNode removed;

        private Removal(LayoutNode remaining, LayoutNode removed) {
            this.remaining = remaining;
            this.removed = removed;
        }

        private LayoutNode remaining() {
            return remaining;
        }

        private LayoutNode removed() {
            return removed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Removal other = (Removal) o;
            return java.util.Objects.equals(remaining, other.remaining)
                    && java.util.Objects.equals(removed, other.removed);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + java.util.Objects.hashCode(remaining);
            result = 31 * result + java.util.Objects.hashCode(removed);
            return result;
        }

        @Override
        public String toString() {
            return "Removal[remaining=" + remaining + ", removed=" + removed + "]";
        }
    }

    /** 原为私有 record，仅在本类内部按值传递。 */
    private static final class Replace {
        private final LayoutNode node;
        private final boolean replaced;

        private Replace(LayoutNode node, boolean replaced) {
            this.node = node;
            this.replaced = replaced;
        }

        private LayoutNode node() {
            return node;
        }

        private boolean replaced() {
            return replaced;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Replace other = (Replace) o;
            return replaced == other.replaced && java.util.Objects.equals(node, other.node);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + java.util.Objects.hashCode(node);
            result = 31 * result + Boolean.hashCode(replaced);
            return result;
        }

        @Override
        public String toString() {
            return "Replace[node=" + node + ", replaced=" + replaced + "]";
        }
    }

    /** 原为私有 record，仅在本类内部按值传递。 */
    private static final class RatioChange {
        private final LayoutNode node;
        private final boolean found;

        private RatioChange(LayoutNode node, boolean found) {
            this.node = node;
            this.found = found;
        }

        private LayoutNode node() {
            return node;
        }

        private boolean found() {
            return found;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RatioChange other = (RatioChange) o;
            return found == other.found && java.util.Objects.equals(node, other.node);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + java.util.Objects.hashCode(node);
            result = 31 * result + Boolean.hashCode(found);
            return result;
        }

        @Override
        public String toString() {
            return "RatioChange[node=" + node + ", found=" + found + "]";
        }
    }
}
