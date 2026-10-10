/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

/** Insets between a floating root frame and its recursive content tree. */
public final class DockInsets {
    public static final DockInsets NONE = new DockInsets(0, 0, 0, 0);

    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    public DockInsets(int left, int top, int right, int bottom) {
        if (left < 0 || top < 0 || right < 0 || bottom < 0) {
            throw new IllegalArgumentException("insets must be non-negative");
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int left() {
        return left;
    }

    public int top() {
        return top;
    }

    public int right() {
        return right;
    }

    public int bottom() {
        return bottom;
    }

    public int horizontal() {
        return left + right;
    }

    public int vertical() {
        return top + bottom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DockInsets other = (DockInsets) o;
        return left == other.left && top == other.top && right == other.right && bottom == other.bottom;
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + Integer.hashCode(left);
        result = 31 * result + Integer.hashCode(top);
        result = 31 * result + Integer.hashCode(right);
        result = 31 * result + Integer.hashCode(bottom);
        return result;
    }

    @Override
    public String toString() {
        return "DockInsets[left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom + "]";
    }
}
