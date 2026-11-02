/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.dock.workspace;

import com.github.aeddddd.ae2enhanced.dock.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Deterministic default roots for registered modules. */
public final class DockWorkspaceDefaults {
    // network_toolkit 是 MEST 的模块 id，仅用于 v3/v4 版本的历史摆放位置补偿。
    public static final String NETWORK_TOOLKIT_MODULE = "network_toolkit";
    /** The network-tool panel sits 6px further left than the generic cascade placement. */
    public static final int NETWORK_TOOLKIT_SHIFT_X = 6;
    /**
     * Placement deltas by source layout version: index 3 is the v3→v4 shift, index 4 the v4→v5 one.
     * {@link #NETWORK_TOOLKIT_SHIFT_X} is their sum, i.e. the offset new placements use.
     */
    private static final int[] SHIFT_BY_FROM_VERSION = { 0, 0, 0, 4, 2 };

    private DockWorkspaceDefaults() {
    }

    /** Cumulative shift owed to a document stored as {@code version}. */
    public static int networkToolkitShiftFrom(int version) {
        int shift = 0;
        for (int from = Math.max(0, version); from < SHIFT_BY_FROM_VERSION.length; from++) {
            shift += SHIFT_BY_FROM_VERSION[from];
        }
        return shift;
    }

    /** Placement tweak for {@link #NETWORK_TOOLKIT_MODULE}; every other module is unchanged. */
    public static DockRect defaultBounds(String moduleId, DockRect bounds) {
        return NETWORK_TOOLKIT_MODULE.equals(moduleId)
                ? shiftNetworkToolkit(bounds, NETWORK_TOOLKIT_SHIFT_X)
                : bounds;
    }

    public static DockRect networkToolkitBounds(DockRect bounds, int shift) {
        return shiftNetworkToolkit(bounds, shift);
    }

    private static DockRect shiftNetworkToolkit(DockRect bounds, int shift) {
        if (shift == 0) {
            return bounds;
        }
        return new DockRect(
                Math.max(0, bounds.x() - shift),
                bounds.y(),
                bounds.width(),
                bounds.height());
    }

    public static DockWorkspace create(ModuleCatalog catalog, LegacyMigrationContext geometry) {
        return create(catalog, geometry, UnaryOperator.identity());
    }

    public static DockWorkspace create(ModuleCatalog catalog, LegacyMigrationContext geometry,
            UnaryOperator<DockWorkspace> shellFit) {
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>();
        int index = 0;
        for (String moduleId : catalog.moduleIds()) {
            LeafNode leaf = new LeafNode(
                    leafNodeId(moduleId), moduleId, catalog.metrics(moduleId).defaults().visible());
            ModuleDefaults defaults = catalog.metrics(moduleId).defaults();
            roots.add(new FloatingRoot(
                    rootId(moduleId),
                    defaultBounds(moduleId, geometry.defaultRootBounds(catalog.metrics(moduleId), index++)),
                    leaf,
                    defaults.floating(),
                    defaults.pinned()));
        }
        Map<String, ModuleLayoutPolicy> policies = new LinkedHashMap<String, ModuleLayoutPolicy>();
        for (String moduleId : catalog.moduleIds()) {
            policies.put(moduleId, catalog.metrics(moduleId).defaults().policy());
        }
        return new DockWorkspace(roots, policies);
    }

    public static String leafNodeId(String moduleId) {
        return NodeIds.deterministic("leaf", moduleId);
    }

    public static String rootId(String moduleId) {
        return NodeIds.deterministic("root", moduleId);
    }
}
