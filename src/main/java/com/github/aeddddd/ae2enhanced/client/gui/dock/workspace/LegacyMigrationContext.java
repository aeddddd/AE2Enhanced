/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import java.util.Objects;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockInsets;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleMetrics;

/** Geometry needed to translate the unversioned one-level layout into v2. */
public final class LegacyMigrationContext {
    private final DockInsets rootInsets;
    private final int dividerThickness;
    private final int tileStartX;
    private final int tileStartY;
    private final int tileStep;
    private final int viewportWidth;
    private final int viewportHeight;

    public LegacyMigrationContext(
            DockInsets rootInsets,
            int dividerThickness,
            int tileStartX,
            int tileStartY,
            int tileStep,
            int viewportWidth,
            int viewportHeight) {
        Objects.requireNonNull(rootInsets, "rootInsets");
        if (dividerThickness < 0 || tileStep < 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("invalid legacy migration geometry");
        }
        this.rootInsets = rootInsets;
        this.dividerThickness = dividerThickness;
        this.tileStartX = tileStartX;
        this.tileStartY = tileStartY;
        this.tileStep = tileStep;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    public DockInsets rootInsets() {
        return rootInsets;
    }

    public int dividerThickness() {
        return dividerThickness;
    }

    public int tileStartX() {
        return tileStartX;
    }

    public int tileStartY() {
        return tileStartY;
    }

    public int tileStep() {
        return tileStep;
    }

    public int viewportWidth() {
        return viewportWidth;
    }

    public int viewportHeight() {
        return viewportHeight;
    }

    /** Matches the unversioned v1 title/padding and divider geometry. */
    public static LegacyMigrationContext currentDockDefaults(int viewportWidth, int viewportHeight) {
        return new LegacyMigrationContext(
                new DockInsets(7, 25, 7, 7),
                4,
                8,
                8,
                16,
                viewportWidth,
                viewportHeight);
    }

    public DockRect defaultRootBounds(ModuleMetrics metrics, int tileIndex) {
        int width = metrics.defaultSize().width() + rootInsets.horizontal();
        int height = metrics.defaultSize().height() + rootInsets.vertical();
        int x = tileStartX + tileIndex * tileStep;
        int y = tileStartY + tileIndex * tileStep;
        return clampToViewport(new DockRect(x, y, width, height));
    }

    public DockRect clampToViewport(DockRect bounds) {
        int x = Math.max(0, Math.min(bounds.x(), Math.max(0, viewportWidth - bounds.width())));
        int y = Math.max(0, Math.min(bounds.y(), Math.max(0, viewportHeight - bounds.height())));
        return new DockRect(x, y, bounds.width(), bounds.height());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LegacyMigrationContext other = (LegacyMigrationContext) o;
        return dividerThickness == other.dividerThickness
                && tileStartX == other.tileStartX
                && tileStartY == other.tileStartY
                && tileStep == other.tileStep
                && viewportWidth == other.viewportWidth
                && viewportHeight == other.viewportHeight
                && rootInsets.equals(other.rootInsets);
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + rootInsets.hashCode();
        result = 31 * result + Integer.hashCode(dividerThickness);
        result = 31 * result + Integer.hashCode(tileStartX);
        result = 31 * result + Integer.hashCode(tileStartY);
        result = 31 * result + Integer.hashCode(tileStep);
        result = 31 * result + Integer.hashCode(viewportWidth);
        result = 31 * result + Integer.hashCode(viewportHeight);
        return result;
    }

    @Override
    public String toString() {
        return "LegacyMigrationContext[rootInsets=" + rootInsets
                + ", dividerThickness=" + dividerThickness
                + ", tileStartX=" + tileStartX
                + ", tileStartY=" + tileStartY
                + ", tileStep=" + tileStep
                + ", viewportWidth=" + viewportWidth
                + ", viewportHeight=" + viewportHeight + "]";
    }
}
