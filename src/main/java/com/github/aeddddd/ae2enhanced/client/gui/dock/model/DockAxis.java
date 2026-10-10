/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

public enum DockAxis {
    HORIZONTAL,
    VERTICAL;

    public int extent(DockRect rect) {
        return this == HORIZONTAL ? rect.width() : rect.height();
    }

    public int extent(DockSize size) {
        return this == HORIZONTAL ? size.width() : size.height();
    }

    public int crossExtent(DockSize size) {
        return this == HORIZONTAL ? size.height() : size.width();
    }
}
