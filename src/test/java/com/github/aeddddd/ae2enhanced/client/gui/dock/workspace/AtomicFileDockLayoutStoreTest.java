/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspaceEditor;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;

class AtomicFileDockLayoutStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyCreatesAndOverwritesInjectedPath() throws Exception {
        Path path = temporaryDirectory.resolve("nested").resolve("layout.json");
        ModuleCatalog catalog = WorkspacePersistenceFixtures.catalog();
        DockLayoutCodec codec = new DockLayoutCodec(catalog, WorkspacePersistenceFixtures.migrationContext());
        AtomicFileDockLayoutStore store = new AtomicFileDockLayoutStore(path, codec);
        DockWorkspace first = WorkspacePersistenceFixtures.workspace();

        assertTrue(!store.load().isPresent());
        store.save(first);
        assertTrue(Files.isRegularFile(path));
        assertEquals(first, store.load().get().workspace());

        DockWorkspace raised = new DockWorkspaceEditor(catalog).raiseRoot(first, "root-a");
        store.save(raised);
        assertEquals(raised, store.load().get().workspace());
        try (Stream<Path> children = Files.list(path.getParent())) {
            assertFalse(children.anyMatch(file -> file.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void quarantinesMalformedLayoutInsteadOfLeavingItForOverwrite() throws Exception {
        Path path = temporaryDirectory.resolve("layout.json");
        Files.write(path, "{ this is not valid json".getBytes(StandardCharsets.UTF_8));

        DockLayoutCodec codec = new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog(),
                WorkspacePersistenceFixtures.migrationContext());
        AtomicFileDockLayoutStore store = new AtomicFileDockLayoutStore(path, codec);

        assertTrue(!store.load().isPresent());
        assertFalse(Files.exists(path));
        Path quarantine = store.lastQuarantinedPath().get();
        assertTrue(Files.isRegularFile(quarantine));
        assertTrue(quarantine.getFileName().toString().contains(".corrupt."));
    }
}
