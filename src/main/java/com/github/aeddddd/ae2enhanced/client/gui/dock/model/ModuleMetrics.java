/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.Objects;

/**
 * Minimum and initial allocation of a leaf module, excluding root-frame insets, plus its defaults.
 */
public final class ModuleMetrics {
    private final DockSize minimumSize;
    private final DockSize defaultSize;
    private final boolean expandVertically;
    private final ModuleDefaults defaults;

    public ModuleMetrics(DockSize minimumSize, DockSize defaultSize) {
        this(minimumSize, defaultSize, false);
    }

    public ModuleMetrics(DockSize minimumSize, DockSize defaultSize, boolean expandVertically) {
        this(minimumSize, defaultSize, expandVertically, ModuleDefaults.STANDARD);
    }

    public ModuleMetrics(
            DockSize minimumSize,
            DockSize defaultSize,
            boolean expandVertically,
            ModuleDefaults defaults) {
        Objects.requireNonNull(minimumSize, "minimumSize");
        Objects.requireNonNull(defaultSize, "defaultSize");
        Objects.requireNonNull(defaults, "defaults");
        if (minimumSize.isEmpty()) {
            throw new IllegalArgumentException("module minimum size must be positive");
        }
        if (defaultSize.width() < minimumSize.width() || defaultSize.height() < minimumSize.height()) {
            throw new IllegalArgumentException("default size must satisfy the minimum size");
        }
        this.minimumSize = minimumSize;
        this.defaultSize = defaultSize;
        this.expandVertically = expandVertically;
        this.defaults = defaults;
    }

    public DockSize minimumSize() {
        return minimumSize;
    }

    public DockSize defaultSize() {
        return defaultSize;
    }

    public boolean expandVertically() {
        return expandVertically;
    }

    public ModuleDefaults defaults() {
        return defaults;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ModuleMetrics other = (ModuleMetrics) o;
        return expandVertically == other.expandVertically
                && minimumSize.equals(other.minimumSize)
                && defaultSize.equals(other.defaultSize)
                && defaults.equals(other.defaults);
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + minimumSize.hashCode();
        result = 31 * result + defaultSize.hashCode();
        result = 31 * result + Boolean.hashCode(expandVertically);
        result = 31 * result + defaults.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ModuleMetrics[minimumSize=" + minimumSize + ", defaultSize=" + defaultSize
                + ", expandVertically=" + expandVertically + ", defaults=" + defaults + "]";
    }
}
