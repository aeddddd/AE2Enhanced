/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;

class WorkspaceUndoHistoryTest {

    private static DockWorkspace workspace(String rootId) {
        return new DockWorkspace(Arrays.asList(
                new FloatingRoot(rootId, new DockRect(0, 0, 100, 80),
                        new LeafNode("leaf-" + rootId, rootId, true))));
    }

    @Test
    void rememberSkipsIdenticalSnapshot() {
        WorkspaceUndoHistory history = new WorkspaceUndoHistory(4);
        DockWorkspace snapshot = workspace("a");
        assertTrue(history.remember(snapshot));
        assertFalse(history.remember(snapshot), "dedup against the current top");
        assertEquals(1, history.size());
    }

    @Test
    void popReturnsNullWhenEmpty() {
        assertNull(new WorkspaceUndoHistory(4).pop());
    }

    @Test
    void popReturnsAndRemovesMostRecent() {
        WorkspaceUndoHistory history = new WorkspaceUndoHistory(4);
        DockWorkspace first = workspace("a");
        DockWorkspace second = workspace("b");
        history.remember(first);
        history.remember(second);
        assertEquals(second, history.pop());
        assertEquals(first, history.pop());
        assertTrue(history.isEmpty());
    }

    @Test
    void rememberHonorsBoundedSize() {
        WorkspaceUndoHistory history = new WorkspaceUndoHistory(2);
        history.remember(workspace("a"));
        history.remember(workspace("b"));
        history.remember(workspace("c"));
        history.remember(workspace("d"));
        assertEquals(2, history.size());
        // The two most recent remain; the oldest two are dropped.
        assertEquals(workspace("d"), history.pop());
        assertEquals(workspace("c"), history.pop());
        assertTrue(history.isEmpty());
    }

    @Test
    void rememberIgnoresNullSnapshot() {
        WorkspaceUndoHistory history = new WorkspaceUndoHistory(2);
        assertFalse(history.remember(null));
        assertTrue(history.isEmpty());
    }

    @Test
    void clearEmptiesHistory() {
        WorkspaceUndoHistory history = new WorkspaceUndoHistory(2);
        history.remember(workspace("a"));
        history.clear();
        assertTrue(history.isEmpty());
    }

    @Test
    void constructorRejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceUndoHistory(0));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceUndoHistory(-1));
    }

    @Test
    void restoreDropsEntriesRememberedAfterTheSnapshot() {
        WorkspaceUndoHistory history = new WorkspaceUndoHistory(4);
        DockWorkspace before = workspace("a");
        history.remember(before);
        List<DockWorkspace> snapshot = history.snapshot();

        history.remember(workspace("editor-1"));
        history.remember(workspace("editor-2"));
        history.restore(snapshot);

        assertEquals(1, history.size());
        assertEquals(before, history.pop());
    }
}
