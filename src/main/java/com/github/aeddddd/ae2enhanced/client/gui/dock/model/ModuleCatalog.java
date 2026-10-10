/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Immutable module registration metadata. Registration order is stable. */
public final class ModuleCatalog {
    private final Map<String, ModuleMetrics> entries;

    public ModuleCatalog(Map<String, ModuleMetrics> entries) {
        if (entries == null) {
            throw new NullPointerException("entries");
        }
        Map<String, ModuleMetrics> copy = new LinkedHashMap<String, ModuleMetrics>();
        for (Map.Entry<String, ModuleMetrics> entry : entries.entrySet()) {
            String moduleId = entry.getKey();
            ModuleMetrics metrics = entry.getValue();
            NodeIds.requireValid(moduleId, "moduleId");
            if (metrics == null) {
                throw new NullPointerException("metrics for " + moduleId);
            }
            copy.put(moduleId, metrics);
        }
        this.entries = Collections.unmodifiableMap(copy);
    }

    public ModuleMetrics metrics(String moduleId) {
        ModuleMetrics metrics = entries.get(moduleId);
        if (metrics == null) {
            throw new IllegalArgumentException("unknown module: " + moduleId);
        }
        return metrics;
    }

    public boolean contains(String moduleId) {
        return entries.containsKey(moduleId);
    }

    public Set<String> moduleIds() {
        return entries.keySet();
    }

    public int size() {
        return entries.size();
    }
}
