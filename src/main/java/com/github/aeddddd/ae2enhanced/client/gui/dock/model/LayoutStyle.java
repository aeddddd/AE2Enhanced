/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.Objects;

/** Geometry constants supplied by the eventual renderer integration. */
public final class LayoutStyle {
    private final DockInsets rootInsets;
    private final int dividerThickness;

    public LayoutStyle(DockInsets rootInsets, int dividerThickness) {
        Objects.requireNonNull(rootInsets, "rootInsets");
        if (dividerThickness < 0) {
            throw new IllegalArgumentException("dividerThickness must be non-negative");
        }
        this.rootInsets = rootInsets;
        this.dividerThickness = dividerThickness;
    }

    public DockInsets rootInsets() {
        return rootInsets;
    }

    public int dividerThickness() {
        return dividerThickness;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LayoutStyle other = (LayoutStyle) o;
        return dividerThickness == other.dividerThickness && rootInsets.equals(other.rootInsets);
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + rootInsets.hashCode();
        result = 31 * result + Integer.hashCode(dividerThickness);
        return result;
    }

    @Override
    public String toString() {
        return "LayoutStyle[rootInsets=" + rootInsets + ", dividerThickness=" + dividerThickness + "]";
    }
}
