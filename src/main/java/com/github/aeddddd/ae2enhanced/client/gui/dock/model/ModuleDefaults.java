/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

/**
 * How a module first appears in a layout that has never seen it. A pinned module is always
 * floating.
 */
public final class ModuleDefaults {
    public static final ModuleDefaults STANDARD = new ModuleDefaults(true, false, false, true);

    private final boolean visible;
    private final boolean floating;
    private final boolean pinned;
    private final boolean showTerminalButton;

    public ModuleDefaults(boolean visible, boolean floating, boolean pinned, boolean showTerminalButton) {
        floating |= pinned;
        this.visible = visible;
        this.floating = floating;
        this.pinned = pinned;
        this.showTerminalButton = showTerminalButton;
    }

    public boolean visible() {
        return visible;
    }

    public boolean floating() {
        return floating;
    }

    public boolean pinned() {
        return pinned;
    }

    public boolean showTerminalButton() {
        return showTerminalButton;
    }

    public ModuleLayoutPolicy policy() {
        return new ModuleLayoutPolicy(visible, true, true, floating, pinned, showTerminalButton);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ModuleDefaults other = (ModuleDefaults) o;
        return visible == other.visible
                && floating == other.floating
                && pinned == other.pinned
                && showTerminalButton == other.showTerminalButton;
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + Boolean.hashCode(visible);
        result = 31 * result + Boolean.hashCode(floating);
        result = 31 * result + Boolean.hashCode(pinned);
        result = 31 * result + Boolean.hashCode(showTerminalButton);
        return result;
    }

    @Override
    public String toString() {
        return "ModuleDefaults[visible=" + visible + ", floating=" + floating + ", pinned=" + pinned
                + ", showTerminalButton=" + showTerminalButton + "]";
    }
}
