package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.util.List;

import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.LayoutPresetBank;
import com.github.aeddddd.ae2enhanced.dock.model.DockWorkspace;

final class LayoutEditingSession {
    private boolean active;
    private DockWorkspace originalWorkspace;
    private List<DockWorkspace> undoMark;
    private LayoutPresetBank.Data originalPresets;

    boolean begin(DockWorkspace workspace, List<DockWorkspace> undoMark, LayoutPresetBank.Data presets) {
        if (active) {
            return false;
        }
        active = true;
        originalWorkspace = workspace;
        this.undoMark = undoMark;
        originalPresets = presets == null ? null : presets.copy();
        return true;
    }

    boolean active() {
        return active;
    }

    DockWorkspace originalWorkspace() {
        return originalWorkspace;
    }

    List<DockWorkspace> undoMark() {
        return undoMark;
    }

    LayoutPresetBank.Data originalPresets() {
        return originalPresets == null ? null : originalPresets.copy();
    }

    void finish() {
        active = false;
        originalWorkspace = null;
        undoMark = null;
        originalPresets = null;
    }
}
