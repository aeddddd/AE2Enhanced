package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.util.List;

import com.github.aeddddd.ae2enhanced.dock.model.LayoutProjection;

final class PanelLayoutReconciler {
    private LayoutProjection committed;

    void reconcile(LayoutProjection projection, List<ModulePanel> panels) {
        if (projection == null || projection == committed) {
            return;
        }
        for (ModulePanel panel : panels) {
            panel.contentRightInset = panel.preferredContentRightInset();
            panel.contentOffsetX = 0;
            panel.contentOffsetY = 0;
            panel.layoutSlots();
        }
        committed = projection;
    }
}
