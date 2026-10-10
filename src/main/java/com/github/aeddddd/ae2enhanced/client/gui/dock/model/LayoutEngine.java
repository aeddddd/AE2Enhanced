/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Pure recursive measure/arrange engine for outer-shell windows. It performs no rendering or file IO.
 *
 * <p>A root keeps its own rectangle and every split hands both children the full cross-axis extent
 * of its parent, so the leaf union is always a perfect rectangle and a single background can cover
 * it without any seam.
 */
public final class LayoutEngine {
    private final ModuleCatalog catalog;
    private final LayoutStyle style;

    public LayoutEngine(ModuleCatalog catalog, LayoutStyle style) {
        if (catalog == null || style == null) {
            throw new NullPointerException("catalog and style are required");
        }
        this.catalog = catalog;
        this.style = style;
    }

    public LayoutProjection project(DockWorkspace workspace) {
        WorkspaceValidator.validateStrict(workspace, catalog);
        ProjectionBuilder builder = new ProjectionBuilder();

        for (FloatingRoot root : workspace.roots()) {
            DockSize contentMinimum = measure(workspace, root.content(), builder.minimumSizes);
            DockSize rootMinimum = contentMinimum.isEmpty()
                    ? DockSize.ZERO
                    : new DockSize(
                            add(contentMinimum.width(), style.rootInsets().horizontal()),
                            add(contentMinimum.height(), style.rootInsets().vertical()));
            boolean effectivelyVisible = !root.hidden() && !contentMinimum.isEmpty();
            DockRect contentBounds = root.bounds().inset(style.rootInsets());
            builder.roots.add(new LayoutProjection.RootPlacement(
                    root.rootId(), root.bounds(), contentBounds, rootMinimum, effectivelyVisible));
            if (effectivelyVisible) {
                desiredMeasure(workspace, root.content(), builder.desiredSizes);
                arrangeShell(root.content(), contentBounds, builder);
            }
        }

        return builder.build();
    }

    private DockSize measure(
            DockWorkspace workspace,
            LayoutNode node,
            Map<String, DockSize> measurements) {
        DockSize result;
        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            result = workspace.policyFor(leaf.moduleId()).visible()
                    ? catalog.metrics(leaf.moduleId()).minimumSize()
                    : DockSize.ZERO;
        } else {
            SplitNode split = (SplitNode) node;
            DockSize first = measure(workspace, split.first(), measurements);
            DockSize second = measure(workspace, split.second(), measurements);
            if (first.isEmpty()) {
                result = second;
            } else if (second.isEmpty()) {
                result = first;
            } else if (split.axis() == DockAxis.HORIZONTAL) {
                result = new DockSize(
                        add(add(first.width(), style.dividerThickness()), second.width()),
                        Math.max(first.height(), second.height()));
            } else {
                result = new DockSize(
                        Math.max(first.width(), second.width()),
                        add(add(first.height(), style.dividerThickness()), second.height()));
            }
        }
        measurements.put(node.nodeId(), result);
        return result;
    }

    /**
     * Bottom-up pass: the size each node actually wants, using the
     * restored (or default) leaf size instead of the hard minimum. On the split axis a subtree
     * wants the sum of its children plus dividers; on the cross axis it wants the widest child,
     * which is exactly what lets the parent align both children without leaving a hole.
     */
    private DockSize desiredMeasure(
            DockWorkspace workspace,
            LayoutNode node,
            Map<String, DockSize> measurements) {
        DockSize result;
        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            if (!workspace.policyFor(leaf.moduleId()).visible()) {
                result = DockSize.ZERO;
            } else {
                DockSize minimum = catalog.metrics(leaf.moduleId()).minimumSize();
                DockSize desired = desiredSize(leaf, workspace);
                result = new DockSize(
                        Math.max(minimum.width(), desired.width()),
                        Math.max(minimum.height(), desired.height()));
            }
        } else {
            SplitNode split = (SplitNode) node;
            DockSize first = desiredMeasure(workspace, split.first(), measurements);
            DockSize second = desiredMeasure(workspace, split.second(), measurements);
            if (first.isEmpty()) {
                result = second;
            } else if (second.isEmpty()) {
                result = first;
            } else if (split.axis() == DockAxis.HORIZONTAL) {
                result = new DockSize(
                        add(add(first.width(), style.dividerThickness()), second.width()),
                        Math.max(first.height(), second.height()));
            } else {
                result = new DockSize(
                        Math.max(first.width(), second.width()),
                        add(add(first.height(), style.dividerThickness()), second.height()));
            }
        }
        measurements.put(node.nodeId(), result);
        return result;
    }

    /**
     * Top-down pass. It spends the split axis on preferred extents and only then hands out leftover,
     * and always gives both children the full cross-axis extent of the parent. The result is a
     * gap-free rectangle, so no seam can appear regardless of rounding.
     */
    private void arrangeShell(
            LayoutNode node,
            DockRect bounds,
            ProjectionBuilder builder) {
        DockSize minimum = builder.minimumSizes.get(node.nodeId());
        if (minimum == null || minimum.isEmpty()) {
            return;
        }
        builder.effectivelyVisibleNodeIds.add(node.nodeId());

        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            builder.visibleNodeBounds.put(node.nodeId(), bounds);
            LayoutProjection.LeafPlacement placement =
                    new LayoutProjection.LeafPlacement(leaf.nodeId(), leaf.moduleId(), bounds);
            builder.visibleLeavesByNodeId.put(leaf.nodeId(), placement);
            builder.visibleLeavesInPaintOrder.add(placement);
            return;
        }

        SplitNode split = (SplitNode) node;
        builder.visibleNodeBounds.put(node.nodeId(), bounds);
        DockSize firstMinimum = builder.minimumSizes.get(split.first().nodeId());
        DockSize secondMinimum = builder.minimumSizes.get(split.second().nodeId());
        boolean firstVisible = firstMinimum != null && !firstMinimum.isEmpty();
        boolean secondVisible = secondMinimum != null && !secondMinimum.isEmpty();
        if (!firstVisible) {
            arrangeShell(split.second(), bounds, builder);
            return;
        }
        if (!secondVisible) {
            arrangeShell(split.first(), bounds, builder);
            return;
        }

        int totalExtent = split.axis().extent(bounds);
        int dividerExtent = Math.min(style.dividerThickness(), totalExtent);
        int available = totalExtent - dividerExtent;
        int firstExtent = allocateShellFirst(
                split,
                available,
                extentOrZero(builder.desiredSizes.get(split.first().nodeId()), split.axis()),
                extentOrZero(builder.desiredSizes.get(split.second().nodeId()), split.axis()),
                split.axis().extent(firstMinimum),
                split.axis().extent(secondMinimum));
        int secondExtent = available - firstExtent;

        DockRect firstBounds;
        DockRect dividerBounds;
        DockRect secondBounds;
        if (split.axis() == DockAxis.HORIZONTAL) {
            firstBounds = new DockRect(bounds.x(), bounds.y(), firstExtent, bounds.height());
            dividerBounds = new DockRect(bounds.x() + firstExtent, bounds.y(), dividerExtent, bounds.height());
            secondBounds = new DockRect(
                    dividerBounds.right(), bounds.y(), secondExtent, bounds.height());
        } else {
            firstBounds = new DockRect(bounds.x(), bounds.y(), bounds.width(), firstExtent);
            dividerBounds = new DockRect(bounds.x(), bounds.y() + firstExtent, bounds.width(), dividerExtent);
            secondBounds = new DockRect(
                    bounds.x(), dividerBounds.bottom(), bounds.width(), secondExtent);
        }
        builder.dividers.add(new LayoutProjection.DividerPlacement(
                split.nodeId(), split.axis(), dividerBounds));
        arrangeShell(split.first(), firstBounds, builder);
        arrangeShell(split.second(), secondBounds, builder);
    }

    /**
     * Spends the split axis on preferred extents first, then gives leftover to whichever side
     * declares {@code expandVertically}, falling back to the split ratio. Falls back to
     * minimum-proportional allocation when the rectangle cannot satisfy both minima.
     */
    private int allocateShellFirst(
            SplitNode split,
            int available,
            int desiredFirst,
            int desiredSecond,
            int minFirst,
            int minSecond) {
        if (available <= 0) {
            return 0;
        }
        long combinedMinimum = (long) minFirst + minSecond;
        if (combinedMinimum >= available) {
            if (combinedMinimum == 0) {
                return Math.max(0, Math.min(available, desiredFirst));
            }
            return (int) Math.round((double) available * minFirst / combinedMinimum);
        }
        int slack = available - (desiredFirst + desiredSecond);
        if (slack <= 0) {
            long combinedDesired = (long) Math.max(0, desiredFirst) + Math.max(0, desiredSecond);
            if (combinedDesired == 0) {
                return (int) Math.round(available * split.ratio());
            }
            int squeezed = (int) Math.round((double) available * desiredFirst / combinedDesired);
            return Math.max(minFirst, Math.min(available - minSecond, squeezed));
        }
        boolean firstExpands = expandsOn(split.first(), split.axis());
        boolean secondExpands = expandsOn(split.second(), split.axis());
        if (firstExpands && !secondExpands) {
            return Math.max(minFirst, Math.min(available - minSecond, available - desiredSecond));
        }
        if (secondExpands && !firstExpands) {
            return Math.max(minFirst, Math.min(available - minSecond, desiredFirst));
        }
        int grown = (int) Math.round(desiredFirst + slack * split.ratio());
        return Math.max(minFirst, Math.min(available - minSecond, grown));
    }

    /** Preferred occupied size of a subtree, used when the workspace fits roots to their content. */
    public DockSize preferredSize(DockWorkspace workspace, LayoutNode node) {
        return desiredMeasure(workspace, node, new LinkedHashMap<String, DockSize>());
    }

    private DockSize desiredSize(LeafNode leaf, DockWorkspace workspace) {
        DockSize restore = workspace.restoreSizes().get(leaf.nodeId());
        if (restore != null && !restore.isEmpty()) {
            return restore;
        }
        return catalog.metrics(leaf.moduleId()).defaultSize();
    }

    private boolean expandsOn(LayoutNode node, DockAxis axis) {
        if (axis != DockAxis.VERTICAL) {
            return false;
        }
        if (node instanceof LeafNode) {
            return catalog.metrics(((LeafNode) node).moduleId()).expandVertically();
        }
        SplitNode split = (SplitNode) node;
        return expandsOn(split.first(), axis) || expandsOn(split.second(), axis);
    }

    private static int extentOrZero(DockSize size, DockAxis axis) {
        return size == null ? 0 : axis.extent(size);
    }

    private static int add(int first, int second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException e) {
            throw new WorkspaceValidationException("layout size overflow");
        }
    }

    private static final class ProjectionBuilder {
        private final ArrayList<LayoutProjection.RootPlacement> roots =
                new ArrayList<LayoutProjection.RootPlacement>();
        private final LinkedHashMap<String, DockSize> minimumSizes = new LinkedHashMap<String, DockSize>();
        private final LinkedHashMap<String, DockSize> desiredSizes = new LinkedHashMap<String, DockSize>();
        private final LinkedHashMap<String, DockRect> visibleNodeBounds = new LinkedHashMap<String, DockRect>();
        private final LinkedHashMap<String, LayoutProjection.LeafPlacement> visibleLeavesByNodeId =
                new LinkedHashMap<String, LayoutProjection.LeafPlacement>();
        private final ArrayList<LayoutProjection.LeafPlacement> visibleLeavesInPaintOrder =
                new ArrayList<LayoutProjection.LeafPlacement>();
        private final ArrayList<LayoutProjection.DividerPlacement> dividers =
                new ArrayList<LayoutProjection.DividerPlacement>();
        private final LinkedHashSet<String> effectivelyVisibleNodeIds = new LinkedHashSet<String>();

        private LayoutProjection build() {
            return new LayoutProjection(
                    roots,
                    minimumSizes,
                    visibleNodeBounds,
                    visibleLeavesByNodeId,
                    visibleLeavesInPaintOrder,
                    dividers,
                    effectivelyVisibleNodeIds);
        }
    }
}
