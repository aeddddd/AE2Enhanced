/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.Objects;

/**
 * One independently movable floating tree. Workspace list order is its Z order.
 *
 * <p>{@code floating} is a window property: an anchored window belongs to the terminal group
 * (toolbar, upgrade column), a floating one stands on its own whatever modules it holds. A pinned
 * window is always floating and stays above unpinned ones. A floating window may also be a
 * combined module ({@code group} 1 to {@link #MAX_GROUPS}), which the terminal shows and hides as
 * a whole; {@code hidden} is that toggle.
 */
public final class FloatingRoot {
    public static final int MAX_GROUPS = 3;

    private final String rootId;
    private final DockRect bounds;
    private final LayoutNode content;
    private final boolean floating;
    private final boolean pinned;
    private final int group;
    private final boolean hidden;

    public FloatingRoot(String rootId, DockRect bounds, LayoutNode content) {
        this(rootId, bounds, content, false, false);
    }

    public FloatingRoot(String rootId, DockRect bounds, LayoutNode content, boolean floating, boolean pinned) {
        this(rootId, bounds, content, floating, pinned, 0, false);
    }

    public FloatingRoot(
            String rootId,
            DockRect bounds,
            LayoutNode content,
            boolean floating,
            boolean pinned,
            int group,
            boolean hidden) {
        NodeIds.requireValid(rootId, "rootId");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(content, "content");
        if (bounds.width() == 0 || bounds.height() == 0) {
            throw new IllegalArgumentException("floating root bounds must be positive");
        }
        floating |= pinned;
        if (!floating || group < 1 || group > MAX_GROUPS) {
            group = 0;
        }
        hidden &= group != 0;
        this.rootId = rootId;
        this.bounds = bounds;
        this.content = content;
        this.floating = floating;
        this.pinned = pinned;
        this.group = group;
        this.hidden = hidden;
    }

    public String rootId() {
        return rootId;
    }

    public DockRect bounds() {
        return bounds;
    }

    public LayoutNode content() {
        return content;
    }

    public boolean floating() {
        return floating;
    }

    public boolean pinned() {
        return pinned;
    }

    public int group() {
        return group;
    }

    public boolean hidden() {
        return hidden;
    }

    public FloatingRoot withContent(LayoutNode newContent) {
        return new FloatingRoot(rootId, bounds, newContent, floating, pinned, group, hidden);
    }

    public FloatingRoot withBounds(DockRect newBounds) {
        return new FloatingRoot(rootId, newBounds, content, floating, pinned, group, hidden);
    }

    /** Docking back to the terminal also drops the pin and any combined-module slot. */
    public FloatingRoot withMode(boolean newFloating, boolean newPinned) {
        return new FloatingRoot(rootId, bounds, content, newFloating, newFloating && newPinned, group, hidden);
    }

    public FloatingRoot withGroup(int newGroup) {
        return new FloatingRoot(rootId, bounds, content, floating, pinned, newGroup, newGroup != 0 && hidden);
    }

    public FloatingRoot withHidden(boolean newHidden) {
        return new FloatingRoot(rootId, bounds, content, floating, pinned, group, newHidden);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FloatingRoot other = (FloatingRoot) o;
        return floating == other.floating
                && pinned == other.pinned
                && group == other.group
                && hidden == other.hidden
                && rootId.equals(other.rootId)
                && bounds.equals(other.bounds)
                && content.equals(other.content);
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + rootId.hashCode();
        result = 31 * result + bounds.hashCode();
        result = 31 * result + content.hashCode();
        result = 31 * result + Boolean.hashCode(floating);
        result = 31 * result + Boolean.hashCode(pinned);
        result = 31 * result + Integer.hashCode(group);
        result = 31 * result + Boolean.hashCode(hidden);
        return result;
    }

    @Override
    public String toString() {
        return "FloatingRoot[rootId=" + rootId + ", bounds=" + bounds + ", content=" + content
                + ", floating=" + floating + ", pinned=" + pinned + ", group=" + group
                + ", hidden=" + hidden + "]";
    }
}
