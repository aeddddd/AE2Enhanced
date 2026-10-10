/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

/** Edge of a target node onto which a dragged subtree is inserted. */
public enum DockEdge {
    LEFT(DockAxis.HORIZONTAL, true),
    RIGHT(DockAxis.HORIZONTAL, false),
    TOP(DockAxis.VERTICAL, true),
    BOTTOM(DockAxis.VERTICAL, false);

    private final DockAxis axis;
    private final boolean draggedFirst;

    DockEdge(DockAxis axis, boolean draggedFirst) {
        this.axis = axis;
        this.draggedFirst = draggedFirst;
    }

    public DockAxis axis() {
        return axis;
    }

    public boolean draggedFirst() {
        return draggedFirst;
    }
}
