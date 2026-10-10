/*
 * Derived from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.util.Objects;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockEdge;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;

/**
 * The leaf (and its edge) that a dragged root would splice into if released. The {@code
 * highlightBounds} is the rect used to draw the drop preview, not the dragged root's own bounds.
 */
public final class DropCandidate {
    private final String targetNodeId;
    private final DockEdge edge;
    private final DockRect highlightBounds;

    public DropCandidate(String targetNodeId, DockEdge edge, DockRect highlightBounds) {
        this.targetNodeId = targetNodeId;
        this.edge = edge;
        this.highlightBounds = highlightBounds;
    }

    public String targetNodeId() {
        return targetNodeId;
    }

    public DockEdge edge() {
        return edge;
    }

    public DockRect highlightBounds() {
        return highlightBounds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DropCandidate other = (DropCandidate) o;
        return Objects.equals(targetNodeId, other.targetNodeId)
                && edge == other.edge
                && Objects.equals(highlightBounds, other.highlightBounds);
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + Objects.hashCode(targetNodeId);
        result = 31 * result + Objects.hashCode(edge);
        result = 31 * result + Objects.hashCode(highlightBounds);
        return result;
    }

    @Override
    public String toString() {
        return "DropCandidate[targetNodeId=" + targetNodeId + ", edge=" + edge
                + ", highlightBounds=" + highlightBounds + "]";
    }
}
