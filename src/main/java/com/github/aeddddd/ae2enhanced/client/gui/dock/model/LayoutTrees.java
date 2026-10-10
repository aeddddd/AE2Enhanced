/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Read-only recursive tree queries shared by layout, editing and integration code. */
public final class LayoutTrees {
    private LayoutTrees() {
    }

    public static Optional<LayoutNode> find(LayoutNode node, String nodeId) {
        if (node.nodeId().equals(nodeId)) {
            return Optional.of(node);
        }
        if (node instanceof SplitNode) {
            SplitNode split = (SplitNode) node;
            Optional<LayoutNode> first = find(split.first(), nodeId);
            return first.isPresent() ? first : find(split.second(), nodeId);
        }
        return Optional.empty();
    }

    public static boolean contains(LayoutNode node, String nodeId) {
        return find(node, nodeId).isPresent();
    }

    public static boolean hasVisibleLeaf(LayoutNode node) {
        if (node instanceof LeafNode) {
            return ((LeafNode) node).visible();
        }
        SplitNode split = (SplitNode) node;
        return hasVisibleLeaf(split.first()) || hasVisibleLeaf(split.second());
    }

    public static List<LeafNode> leaves(LayoutNode node) {
        List<LeafNode> result = new ArrayList<LeafNode>();
        collectLeaves(node, result);
        return Collections.unmodifiableList(result);
    }

    private static void collectLeaves(LayoutNode node, List<LeafNode> result) {
        if (node instanceof LeafNode) {
            result.add((LeafNode) node);
            return;
        }
        SplitNode split = (SplitNode) node;
        collectLeaves(split.first(), result);
        collectLeaves(split.second(), result);
    }
}
