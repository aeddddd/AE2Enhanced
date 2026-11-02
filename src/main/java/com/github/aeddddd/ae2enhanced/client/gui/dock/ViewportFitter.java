package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.util.ArrayList;
import java.util.List;

import com.github.aeddddd.ae2enhanced.dock.model.DockInsets;
import com.github.aeddddd.ae2enhanced.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.dock.model.DockSize;
import com.github.aeddddd.ae2enhanced.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.dock.model.DockWorkspaceEditor;
import com.github.aeddddd.ae2enhanced.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.dock.model.LayoutEngine;
import com.github.aeddddd.ae2enhanced.dock.model.LayoutProjection;

final class ViewportFitter {
    private static final int FIT_MARGIN = 8;

    private final int width;
    private final int height;
    private final DockInsets insets;
    private final DockWorkspaceEditor editor;
    private final LayoutEngine layoutEngine;

    ViewportFitter(int width, int height, DockInsets insets, DockWorkspaceEditor editor,
            LayoutEngine layoutEngine) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.insets = insets;
        this.editor = editor;
        this.layoutEngine = layoutEngine;
    }

    private static DockSize minimumOf(LayoutProjection measured, String rootId) {
        for (LayoutProjection.RootPlacement placement : measured.roots()) {
            if (placement.rootId().equals(rootId)) {
                return placement.minimumSize();
            }
        }
        return DockSize.ZERO;
    }

    DockWorkspace fit(DockWorkspace source) {
        DockWorkspace next = clamp(source);
        for (FloatingRoot root : next.roots()) {
            DockRect bounds = root.bounds();
            int maxWidth = Math.max(1, width - FIT_MARGIN);
            int maxHeight = Math.max(1, height - FIT_MARGIN);
            if (bounds.width() <= maxWidth && bounds.height() <= maxHeight) {
                continue;
            }
            DockSize minimum = minimumOf(layoutEngine.project(next), root.rootId());
            DockRect fitted = clampRect(new DockRect(bounds.x(), bounds.y(),
                    Math.max(minimum.width(), Math.min(bounds.width(), maxWidth)),
                    Math.max(minimum.height(), Math.min(bounds.height(), maxHeight))), DockSize.ZERO);
            next = editor.setRootBounds(next, root.rootId(), fitted);
        }
        return next;
    }

    DockWorkspace clamp(DockWorkspace source) {
        List<FloatingRoot> sanitizedRoots = new ArrayList<FloatingRoot>();
        for (FloatingRoot root : source.roots()) {
            sanitizedRoots.add(root.withBounds(clampRect(root.bounds(), DockSize.ZERO)));
        }
        DockWorkspace sanitized = source.withRoots(sanitizedRoots);
        LayoutProjection measured = layoutEngine.project(sanitized);
        List<FloatingRoot> clampedRoots = new ArrayList<FloatingRoot>();
        for (FloatingRoot root : sanitized.roots()) {
            DockSize minimum = minimumOf(measured, root.rootId());
            clampedRoots.add(root.withBounds(clampRect(root.bounds(), minimum)));
        }
        return source.withRoots(clampedRoots);
    }

    DockRect clampRect(DockRect bounds, DockSize minimum) {
        int minX = insets.left();
        int minY = insets.top();
        int maxRight = Math.max(minX + 1, width - insets.right());
        int maxBottom = Math.max(minY + 1, height - insets.bottom());
        int canvasWidth = Math.max(1, maxRight - minX);
        int canvasHeight = Math.max(1, maxBottom - minY);
        int width = Math.max(1, Math.min(canvasWidth, Math.max(bounds.width(), minimum.width())));
        int height = Math.max(1, Math.min(canvasHeight, Math.max(bounds.height(), minimum.height())));
        int maxX = Math.max(minX, maxRight - width);
        int maxY = Math.max(minY, maxBottom - height);
        int x = (int) Math.max((long) minX, Math.min((long) bounds.x(), (long) maxX));
        int y = (int) Math.max((long) minY, Math.min((long) bounds.y(), (long) maxY));
        return new DockRect(x, y, width, height);
    }
}
