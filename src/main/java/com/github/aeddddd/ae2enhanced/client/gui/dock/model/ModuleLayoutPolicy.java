/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

/** Runtime interaction permissions for one registered terminal module. */
public final class ModuleLayoutPolicy {
    private final boolean visible;
    private final boolean movable;
    private final boolean resizable;
    private final boolean floating;
    private final boolean pinned;
    private final boolean showTerminalButton;

    public ModuleLayoutPolicy(
            boolean visible,
            boolean movable,
            boolean resizable,
            boolean floating,
            boolean pinned,
            boolean showTerminalButton) {
        this.visible = visible;
        this.movable = movable;
        this.resizable = resizable;
        this.floating = floating;
        this.pinned = pinned;
        this.showTerminalButton = showTerminalButton;
    }

    public ModuleLayoutPolicy(boolean visible, boolean movable, boolean resizable) {
        this(visible, movable, resizable, false, false, true);
    }

    public ModuleLayoutPolicy(boolean visible, boolean movable, boolean resizable, boolean floating) {
        this(visible, movable, resizable, floating, false, true);
    }

    public ModuleLayoutPolicy(
            boolean visible,
            boolean movable,
            boolean resizable,
            boolean floating,
            boolean pinned) {
        this(visible, movable, resizable, floating, pinned, true);
    }

    public static ModuleLayoutPolicy defaults() {
        return new ModuleLayoutPolicy(true, true, true, false, false, true);
    }

    public static ModuleLayoutPolicy fixed() {
        return new ModuleLayoutPolicy(true, false, false, false, false, true);
    }

    public boolean visible() {
        return visible;
    }

    public boolean movable() {
        return movable;
    }

    public boolean resizable() {
        return resizable;
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

    public ModuleLayoutPolicy withVisible(boolean visible) {
        return new ModuleLayoutPolicy(visible, movable, resizable, floating, pinned, showTerminalButton);
    }

    public ModuleLayoutPolicy withFloating(boolean floating) {
        return new ModuleLayoutPolicy(visible, movable, resizable, floating, floating && pinned, showTerminalButton);
    }

    public ModuleLayoutPolicy withPinned(boolean pinned) {
        return new ModuleLayoutPolicy(visible, movable, resizable, floating, floating && pinned, showTerminalButton);
    }

    public ModuleLayoutPolicy withShowTerminalButton(boolean showTerminalButton) {
        return new ModuleLayoutPolicy(visible, movable, resizable, floating, pinned, showTerminalButton);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ModuleLayoutPolicy other = (ModuleLayoutPolicy) o;
        return visible == other.visible
                && movable == other.movable
                && resizable == other.resizable
                && floating == other.floating
                && pinned == other.pinned
                && showTerminalButton == other.showTerminalButton;
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + Boolean.hashCode(visible);
        result = 31 * result + Boolean.hashCode(movable);
        result = 31 * result + Boolean.hashCode(resizable);
        result = 31 * result + Boolean.hashCode(floating);
        result = 31 * result + Boolean.hashCode(pinned);
        result = 31 * result + Boolean.hashCode(showTerminalButton);
        return result;
    }

    @Override
    public String toString() {
        return "ModuleLayoutPolicy[visible=" + visible + ", movable=" + movable + ", resizable=" + resizable
                + ", floating=" + floating + ", pinned=" + pinned
                + ", showTerminalButton=" + showTerminalButton + "]";
    }
}
