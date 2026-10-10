/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

/** A registered terminal module and its persistent user-visible state. */
public final class LeafNode implements LayoutNode {
    private final String nodeId;
    private final String moduleId;
    private final boolean visible;

    public LeafNode(String nodeId, String moduleId, boolean visible) {
        NodeIds.requireValid(nodeId, "nodeId");
        NodeIds.requireValid(moduleId, "moduleId");
        this.nodeId = nodeId;
        this.moduleId = moduleId;
        this.visible = visible;
    }

    public String nodeId() {
        return nodeId;
    }

    public String moduleId() {
        return moduleId;
    }

    public boolean visible() {
        return visible;
    }

    public LeafNode withVisible(boolean newVisible) {
        return visible == newVisible ? this : new LeafNode(nodeId, moduleId, newVisible);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LeafNode other = (LeafNode) o;
        return visible == other.visible
                && nodeId.equals(other.nodeId)
                && moduleId.equals(other.moduleId);
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + nodeId.hashCode();
        result = 31 * result + moduleId.hashCode();
        result = 31 * result + Boolean.hashCode(visible);
        return result;
    }

    @Override
    public String toString() {
        return "LeafNode[nodeId=" + nodeId + ", moduleId=" + moduleId + ", visible=" + visible + "]";
    }
}
