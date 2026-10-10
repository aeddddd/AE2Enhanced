/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

/** A non-negative two-dimensional size. */
public final class DockSize {
    public static final DockSize ZERO = new DockSize(0, 0);

    private final int width;
    private final int height;

    public DockSize(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DockSize other = (DockSize) o;
        return width == other.width && height == other.height;
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + Integer.hashCode(width);
        result = 31 * result + Integer.hashCode(height);
        return result;
    }

    @Override
    public String toString() {
        return "DockSize[width=" + width + ", height=" + height + "]";
    }
}
