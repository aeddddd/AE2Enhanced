/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

/** An absolute rectangle. Zero-sized rectangles are valid derived layout results. */
public final class DockRect {
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public DockRect(int x, int y, int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("rectangle size must be non-negative");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public DockSize size() {
        return new DockSize(width, height);
    }

    public DockRect withSize(DockSize size) {
        return new DockRect(x, y, size.width(), size.height());
    }

    public DockRect union(DockRect other) {
        if (other == null) {
            return this;
        }
        int left = Math.min(x, other.x);
        int top = Math.min(y, other.y);
        int right = Math.max(right(), other.right());
        int bottom = Math.max(bottom(), other.bottom());
        return new DockRect(left, top, right - left, bottom - top);
    }

    public DockRect inset(DockInsets insets) {
        int insetWidth = Math.max(0, width - insets.horizontal());
        int insetHeight = Math.max(0, height - insets.vertical());
        return new DockRect(x + insets.left(), y + insets.top(), insetWidth, insetHeight);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DockRect other = (DockRect) o;
        return x == other.x && y == other.y && width == other.width && height == other.height;
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        result = 31 * result + Integer.hashCode(width);
        result = 31 * result + Integer.hashCode(height);
        return result;
    }

    @Override
    public String toString() {
        return "DockRect[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]";
    }
}
