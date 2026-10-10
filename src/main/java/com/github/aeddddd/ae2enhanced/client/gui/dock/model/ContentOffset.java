/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

/** Non-negative nudge of a panel's content block inside a fixed shell section. */
public final class ContentOffset {
    public static final ContentOffset ZERO = new ContentOffset(0, 0);

    private final int x;
    private final int y;

    public ContentOffset(int x, int y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("content offset must be non-negative");
        }
        this.x = x;
        this.y = y;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public boolean isZero() {
        return x == 0 && y == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ContentOffset other = (ContentOffset) o;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        return result;
    }

    @Override
    public String toString() {
        return "ContentOffset[x=" + x + ", y=" + y + "]";
    }
}
