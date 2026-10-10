/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.LinkedHashMap;
import java.util.Map;

final class ModelTestFixtures {
    private ModelTestFixtures() {
    }

    static ModuleCatalog catalog(String... moduleIds) {
        Map<String, ModuleMetrics> entries = new LinkedHashMap<String, ModuleMetrics>();
        for (String moduleId : moduleIds) {
            entries.put(moduleId, new ModuleMetrics(new DockSize(20, 20), new DockSize(80, 60)));
        }
        return new ModuleCatalog(entries);
    }

    static FloatingRoot root(String rootId, String nodeId, String moduleId, int x) {
        return new FloatingRoot(rootId, new DockRect(x, 0, 100, 80), new LeafNode(nodeId, moduleId, true));
    }
}
