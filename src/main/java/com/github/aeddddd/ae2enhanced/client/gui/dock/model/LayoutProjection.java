/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Cached, immutable geometry derived from a workspace revision. */
public final class LayoutProjection {
    private final List<RootPlacement> roots;
    private final Map<String, DockSize> minimumSizes;
    private final Map<String, DockRect> visibleNodeBounds;
    private final Map<String, LeafPlacement> visibleLeavesByNodeId;
    private final List<LeafPlacement> visibleLeavesInPaintOrder;
    private final List<DividerPlacement> dividers;
    private final Set<String> effectivelyVisibleNodeIds;

    LayoutProjection(
            List<RootPlacement> roots,
            Map<String, DockSize> minimumSizes,
            Map<String, DockRect> visibleNodeBounds,
            Map<String, LeafPlacement> visibleLeavesByNodeId,
            List<LeafPlacement> visibleLeavesInPaintOrder,
            List<DividerPlacement> dividers,
            Set<String> effectivelyVisibleNodeIds) {
        this.roots = Collections.unmodifiableList(new ArrayList<RootPlacement>(roots));
        this.minimumSizes = immutableMap(minimumSizes);
        this.visibleNodeBounds = immutableMap(visibleNodeBounds);
        this.visibleLeavesByNodeId = immutableMap(visibleLeavesByNodeId);
        this.visibleLeavesInPaintOrder =
                Collections.unmodifiableList(new ArrayList<LeafPlacement>(visibleLeavesInPaintOrder));
        this.dividers = Collections.unmodifiableList(new ArrayList<DividerPlacement>(dividers));
        this.effectivelyVisibleNodeIds = Collections.unmodifiableSet(new LinkedHashSet<String>(effectivelyVisibleNodeIds));
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<K, V>(source));
    }

    public List<RootPlacement> roots() {
        return roots;
    }

    public Map<String, DockSize> minimumSizes() {
        return minimumSizes;
    }

    public Optional<DockRect> boundsFor(String nodeId) {
        return Optional.ofNullable(visibleNodeBounds.get(nodeId));
    }

    public Optional<LeafPlacement> visibleLeaf(String nodeId) {
        return Optional.ofNullable(visibleLeavesByNodeId.get(nodeId));
    }

    public List<LeafPlacement> visibleLeavesInPaintOrder() {
        return visibleLeavesInPaintOrder;
    }

    public List<DividerPlacement> dividers() {
        return dividers;
    }

    public boolean isEffectivelyVisible(String nodeId) {
        return effectivelyVisibleNodeIds.contains(nodeId);
    }

    public static final class RootPlacement {
        private final String rootId;
        private final DockRect bounds;
        private final DockRect contentBounds;
        private final DockSize minimumSize;
        private final boolean effectivelyVisible;

        public RootPlacement(
                String rootId,
                DockRect bounds,
                DockRect contentBounds,
                DockSize minimumSize,
                boolean effectivelyVisible) {
            this.rootId = rootId;
            this.bounds = bounds;
            this.contentBounds = contentBounds;
            this.minimumSize = minimumSize;
            this.effectivelyVisible = effectivelyVisible;
        }

        public String rootId() {
            return rootId;
        }

        public DockRect bounds() {
            return bounds;
        }

        public DockRect contentBounds() {
            return contentBounds;
        }

        public DockSize minimumSize() {
            return minimumSize;
        }

        public boolean effectivelyVisible() {
            return effectivelyVisible;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RootPlacement other = (RootPlacement) o;
            return effectivelyVisible == other.effectivelyVisible
                    && rootId.equals(other.rootId)
                    && bounds.equals(other.bounds)
                    && contentBounds.equals(other.contentBounds)
                    && minimumSize.equals(other.minimumSize);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + rootId.hashCode();
            result = 31 * result + bounds.hashCode();
            result = 31 * result + contentBounds.hashCode();
            result = 31 * result + minimumSize.hashCode();
            result = 31 * result + Boolean.hashCode(effectivelyVisible);
            return result;
        }

        @Override
        public String toString() {
            return "RootPlacement[rootId=" + rootId + ", bounds=" + bounds + ", contentBounds=" + contentBounds
                    + ", minimumSize=" + minimumSize + ", effectivelyVisible=" + effectivelyVisible + "]";
        }
    }

    public static final class LeafPlacement {
        private final String nodeId;
        private final String moduleId;
        private final DockRect bounds;

        public LeafPlacement(String nodeId, String moduleId, DockRect bounds) {
            this.nodeId = nodeId;
            this.moduleId = moduleId;
            this.bounds = bounds;
        }

        public String nodeId() {
            return nodeId;
        }

        public String moduleId() {
            return moduleId;
        }

        public DockRect bounds() {
            return bounds;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LeafPlacement other = (LeafPlacement) o;
            return nodeId.equals(other.nodeId)
                    && moduleId.equals(other.moduleId)
                    && bounds.equals(other.bounds);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + nodeId.hashCode();
            result = 31 * result + moduleId.hashCode();
            result = 31 * result + bounds.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "LeafPlacement[nodeId=" + nodeId + ", moduleId=" + moduleId + ", bounds=" + bounds + "]";
        }
    }

    public static final class DividerPlacement {
        private final String splitNodeId;
        private final DockAxis axis;
        private final DockRect bounds;

        public DividerPlacement(String splitNodeId, DockAxis axis, DockRect bounds) {
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
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DividerPlacement other = (DividerPlacement) o;
            return splitNodeId.equals(other.splitNodeId)
                    && axis == other.axis
                    && bounds.equals(other.bounds);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + splitNodeId.hashCode();
            result = 31 * result + axis.hashCode();
            result = 31 * result + bounds.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "DividerPlacement[splitNodeId=" + splitNodeId + ", axis=" + axis + ", bounds=" + bounds + "]";
        }
    }
}
