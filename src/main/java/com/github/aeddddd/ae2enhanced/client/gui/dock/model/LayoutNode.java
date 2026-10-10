/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

/**
 * A node in the recursive layout tree. A future tab node can be added without changing roots.
 *
 * <p>Java 8 降级说明：原实现为 {@code sealed interface LayoutNode permits LeafNode, SplitNode}，
 * Java 8 没有 sealed 类型，此处退化为普通 interface，实现者仅此二者（由包内约定保证）。</p>
 */
public interface LayoutNode {
    String nodeId();
}
