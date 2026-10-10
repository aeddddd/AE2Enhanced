/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict structural validation used at persistence and mutation boundaries. */
public final class WorkspaceValidator {
    public static final int MAX_DEPTH = 64;
    public static final int MAX_NODES = 1024;

    private WorkspaceValidator() {
    }

    /**
     * Validates the parts of a workspace that are independent of the currently registered module
     * catalog.
     *
     * <p>Persisted layouts are allowed to outlive a module registration. In that case an old leaf
     * can reference a module that is no longer present and the persistence layer can reconcile it
     * before applying the stricter runtime validation. Keeping this check separate from
     * {@link #validateStrict(DockWorkspace, ModuleCatalog)} prevents a single removed module from
     * invalidating the whole document.</p>
     *
     * @return the distinct module ids in traversal order
     */
    public static Set<String> validateStructure(DockWorkspace workspace) {
        if (workspace == null) {
            throw new WorkspaceValidationException("workspace must not be null");
        }

        Set<String> identifiers = new LinkedHashSet<String>();
        Set<String> moduleIds = new LinkedHashSet<String>();
        Counter nodeCount = new Counter();
        for (FloatingRoot root : workspace.roots()) {
            if (root == null) {
                throw new WorkspaceValidationException("workspace contains a null root");
            }
            if (!identifiers.add(root.rootId())) {
                throw new WorkspaceValidationException("duplicate identifier: " + root.rootId());
            }
            validateNodeStructure(root.content(), identifiers, moduleIds, nodeCount, 1);
        }
        return Collections.unmodifiableSet(moduleIds);
    }

    public static void validateStrict(DockWorkspace workspace, ModuleCatalog catalog) {
        if (catalog == null) {
            throw new WorkspaceValidationException("module catalog must not be null");
        }

        Set<String> moduleIds = validateStructure(workspace);

        if (!moduleIds.equals(catalog.moduleIds())) {
            Set<String> missing = new LinkedHashSet<String>(catalog.moduleIds());
            missing.removeAll(moduleIds);
            Set<String> unexpected = new LinkedHashSet<String>(moduleIds);
            unexpected.removeAll(catalog.moduleIds());
            throw new WorkspaceValidationException(
                    "module set differs from catalog; missing=" + missing + ", unexpected=" + unexpected);
        }

        for (String moduleId : moduleIds) {
            if (!catalog.contains(moduleId)) {
                // Keep the explicit unknown-module diagnostic used by callers that validate a
                // workspace against a catalog with a mismatched set.
                throw new WorkspaceValidationException("unknown module: " + moduleId);
            }
        }
    }

    private static void validateNodeStructure(
            LayoutNode node,
            Set<String> identifiers,
            Set<String> moduleIds,
            Counter nodeCount,
            int depth) {
        if (node == null) {
            throw new WorkspaceValidationException("workspace contains a null node");
        }
        if (depth > MAX_DEPTH) {
            throw new WorkspaceValidationException("layout exceeds maximum depth " + MAX_DEPTH);
        }
        if (++nodeCount.value > MAX_NODES) {
            throw new WorkspaceValidationException("layout exceeds maximum node count " + MAX_NODES);
        }
        if (!identifiers.add(node.nodeId())) {
            throw new WorkspaceValidationException("duplicate identifier: " + node.nodeId());
        }

        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            if (!moduleIds.add(leaf.moduleId())) {
                throw new WorkspaceValidationException("duplicate module: " + leaf.moduleId());
            }
            return;
        }

        SplitNode split = (SplitNode) node;
        validateNodeStructure(split.first(), identifiers, moduleIds, nodeCount, depth + 1);
        validateNodeStructure(split.second(), identifiers, moduleIds, nodeCount, depth + 1);
    }

    private static final class Counter {
        private int value;
    }
}
