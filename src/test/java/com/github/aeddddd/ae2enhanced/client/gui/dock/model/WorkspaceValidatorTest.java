/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class WorkspaceValidatorTest {
    @Test
    void rejectsDuplicateNodeIdentifiers() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b");
        LayoutNode split = new SplitNode(
                "split-main",
                DockAxis.HORIZONTAL,
                0.5,
                new LeafNode("leaf-duplicate", "a", true),
                new LeafNode("leaf-duplicate", "b", true));
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-main", new DockRect(0, 0, 100, 80), split)));

        assertThrows(
                WorkspaceValidationException.class,
                () -> WorkspaceValidator.validateStrict(workspace, catalog));
    }

    @Test
    void rejectsDuplicateAndMissingModules() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b");
        LayoutNode split = new SplitNode(
                "split-main",
                DockAxis.HORIZONTAL,
                0.5,
                new LeafNode("leaf-a1", "a", true),
                new LeafNode("leaf-a2", "a", true));
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                new FloatingRoot("root-main", new DockRect(0, 0, 100, 80), split)));

        assertThrows(
                WorkspaceValidationException.class,
                () -> WorkspaceValidator.validateStrict(workspace, catalog));
    }

    @Test
    void rejectsWorkspaceThatOmitsRegisteredModule() {
        ModuleCatalog catalog = ModelTestFixtures.catalog("a", "b");
        DockWorkspace workspace = new DockWorkspace(Arrays.asList(
                ModelTestFixtures.root("root-a", "leaf-a", "a", 0)));

        assertThrows(
                WorkspaceValidationException.class,
                () -> WorkspaceValidator.validateStrict(workspace, catalog));
    }
}
