/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockAxis;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockInsets;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleLayoutPolicy;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleMetrics;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.SplitNode;

final class WorkspacePersistenceFixtures {
    private WorkspacePersistenceFixtures() {
    }

    static ModuleCatalog catalog() {
        Map<String, ModuleMetrics> entries = new LinkedHashMap<String, ModuleMetrics>();
        entries.put("a", new ModuleMetrics(new DockSize(20, 20), new DockSize(80, 60)));
        entries.put("b", new ModuleMetrics(new DockSize(30, 20), new DockSize(90, 60)));
        entries.put("c", new ModuleMetrics(new DockSize(20, 30), new DockSize(80, 70)));
        return new ModuleCatalog(entries);
    }

    static ModuleCatalog catalog(String... moduleIds) {
        Map<String, ModuleMetrics> entries = new LinkedHashMap<String, ModuleMetrics>();
        for (String moduleId : moduleIds) {
            entries.put(moduleId, new ModuleMetrics(
                    new DockSize(20, 20),
                    new DockSize(80, 60)));
        }
        return new ModuleCatalog(entries);
    }

    static LegacyMigrationContext migrationContext() {
        return new LegacyMigrationContext(DockInsets.NONE, 4, 8, 8, 16, 800, 600);
    }

    static DockWorkspace workspace() {
        List<FloatingRoot> roots = Arrays.asList(
                new FloatingRoot(
                        "root-a",
                        new DockRect(10, 20, 100, 80),
                        new LeafNode("leaf-a", "a", true)),
                new FloatingRoot(
                        "root-bc",
                        new DockRect(160, 40, 220, 140),
                        new SplitNode(
                                "split-bc",
                                DockAxis.VERTICAL,
                                0.35,
                                new LeafNode("leaf-b", "b", true),
                                new LeafNode("leaf-c", "c", true))));
        Map<String, ModuleLayoutPolicy> policies = new LinkedHashMap<String, ModuleLayoutPolicy>();
        policies.put("a", new ModuleLayoutPolicy(false, true, true));
        policies.put("b", ModuleLayoutPolicy.defaults());
        policies.put("c", ModuleLayoutPolicy.defaults());
        return new DockWorkspace(roots, policies);
    }
}
