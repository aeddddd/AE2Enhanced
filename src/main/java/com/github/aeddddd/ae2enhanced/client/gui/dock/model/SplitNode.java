/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.Objects;

/** A recursive two-way split. Ratio is the fraction allocated to {@code first}. */
public final class SplitNode implements LayoutNode {
    private final String nodeId;
    private final DockAxis axis;
    private final double ratio;
    private final LayoutNode first;
    private final LayoutNode second;

    public SplitNode(
            String nodeId,
            DockAxis axis,
            double ratio,
            LayoutNode first,
            LayoutNode second) {
        NodeIds.requireValid(nodeId, "nodeId");
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!Double.isFinite(ratio) || ratio <= 0.0 || ratio >= 1.0) {
            throw new IllegalArgumentException("split ratio must be finite and strictly between 0 and 1");
        }
        this.nodeId = nodeId;
        this.axis = axis;
        this.ratio = ratio;
        this.first = first;
        this.second = second;
    }

    public String nodeId() {
        return nodeId;
    }

    public DockAxis axis() {
        return axis;
    }

    public double ratio() {
        return ratio;
    }

    public LayoutNode first() {
        return first;
    }

    public LayoutNode second() {
        return second;
    }

    public SplitNode withChildren(LayoutNode newFirst, LayoutNode newSecond) {
        return new SplitNode(nodeId, axis, ratio, newFirst, newSecond);
    }

    public SplitNode withRatio(double newRatio) {
        return ratio == newRatio ? this : new SplitNode(nodeId, axis, newRatio, first, second);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SplitNode other = (SplitNode) o;
        // record 语义: double 分量按 Double.compare 比较（NaN 等于自身，-0.0 不等于 0.0）
        return Double.compare(ratio, other.ratio) == 0
                && nodeId.equals(other.nodeId)
                && axis == other.axis
                && first.equals(other.first)
                && second.equals(other.second);
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + nodeId.hashCode();
        result = 31 * result + axis.hashCode();
        result = 31 * result + Double.hashCode(ratio);
        result = 31 * result + first.hashCode();
        result = 31 * result + second.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SplitNode[nodeId=" + nodeId + ", axis=" + axis + ", ratio=" + ratio
                + ", first=" + first + ", second=" + second + "]";
    }
}
