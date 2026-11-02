package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.aeddddd.ae2enhanced.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.dock.model.LayoutNode;
import com.github.aeddddd.ae2enhanced.dock.model.LayoutProjection;
import com.github.aeddddd.ae2enhanced.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.dock.model.SplitNode;

final class RootStack {
    private final List<FloatingRoot> roots;
    private final Map<String, Integer> bands;

    RootStack(DockWorkspace workspace, LayoutProjection projection, int maxBand) {
        List<FloatingRoot> unpinned = new ArrayList<>();
        List<FloatingRoot> pinned = new ArrayList<>();
        for (FloatingRoot root : workspace.roots()) {
            if (!visible(workspace, projection, root)) {
                continue;
            }
            (root.pinned() ? pinned : unpinned).add(root);
        }
        List<FloatingRoot> ordered = new ArrayList<>(unpinned.size() + pinned.size());
        ordered.addAll(unpinned);
        ordered.addAll(pinned);
        this.roots = Collections.unmodifiableList(ordered);
        this.bands = new HashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            FloatingRoot root = ordered.get(index);
            int band = 0;
            for (int earlier = 0; earlier < index; earlier++) {
                FloatingRoot candidate = ordered.get(earlier);
                if (intersects(candidate.bounds(), root.bounds())) {
                    band = Math.max(band, bands.get(candidate.rootId()) + 1);
                }
            }
            bands.put(root.rootId(), Math.min(maxBand, band));
        }
    }

    private static boolean visible(DockWorkspace workspace, LayoutProjection projection, FloatingRoot root) {
        if (projection != null) {
            for (LayoutProjection.RootPlacement placement : projection.roots()) {
                if (placement.rootId().equals(root.rootId())) {
                    return placement.effectivelyVisible();
                }
            }
            return false;
        }
        return !root.hidden() && hasVisibleLeaf(workspace, root.content());
    }

    private static boolean hasVisibleLeaf(DockWorkspace workspace, LayoutNode node) {
        if (node instanceof LeafNode) {
            return workspace.policyFor(((LeafNode) node).moduleId()).visible();
        }
        SplitNode split = (SplitNode) node;
        return hasVisibleLeaf(workspace, split.first()) || hasVisibleLeaf(workspace, split.second());
    }

    private static boolean contains(DockRect outer, DockRect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    private static boolean intersects(DockRect first, DockRect second) {
        return first.x() < second.right() && first.right() > second.x()
                && first.y() < second.bottom() && first.bottom() > second.y();
    }

    List<FloatingRoot> roots() {
        return roots;
    }

    int bandOf(String rootId) {
        Integer band = bands.get(rootId);
        return band == null ? 0 : band;
    }

    boolean isAreaUnobscured(String ownerRootId, DockRect area) {
        int ownerIndex = -1;
        for (int index = 0; index < roots.size(); index++) {
            FloatingRoot root = roots.get(index);
            if (root.rootId().equals(ownerRootId)) {
                if (!contains(root.bounds(), area)) {
                    return false;
                }
                ownerIndex = index;
                break;
            }
        }
        if (ownerIndex < 0) {
            return false;
        }
        for (int index = ownerIndex + 1; index < roots.size(); index++) {
            if (intersects(roots.get(index).bounds(), area)) {
                return false;
            }
        }
        return true;
    }
}
