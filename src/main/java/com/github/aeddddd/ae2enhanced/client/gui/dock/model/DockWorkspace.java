/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable workspace containing structure and per-module runtime interaction policy. */
public final class DockWorkspace {
    private final List<FloatingRoot> roots;
    private final Map<String, ModuleLayoutPolicy> policies;
    private final Map<String, DockSize> restoreSizes;
    private final Map<String, ContentOffset> contentOffsets;

    public DockWorkspace(List<FloatingRoot> roots) {
        this(roots, defaultPolicies(roots), Collections.<String, DockSize>emptyMap(),
                Collections.<String, ContentOffset>emptyMap());
    }

    public DockWorkspace(List<FloatingRoot> roots, Map<String, ModuleLayoutPolicy> policies) {
        this(roots, policies, Collections.<String, DockSize>emptyMap(),
                Collections.<String, ContentOffset>emptyMap());
    }

    public DockWorkspace(
            List<FloatingRoot> roots,
            Map<String, ModuleLayoutPolicy> policies,
            Map<String, DockSize> restoreSizes) {
        this(roots, policies, restoreSizes, Collections.<String, ContentOffset>emptyMap());
    }

    private static Map<String, ModuleLayoutPolicy> defaultPolicies(List<FloatingRoot> roots) {
        Map<String, ModuleLayoutPolicy> policies = new LinkedHashMap<String, ModuleLayoutPolicy>();
        if (roots != null) {
            for (FloatingRoot root : roots) {
                collectPolicies(root.content(), policies);
            }
        }
        return policies;
    }

    private static void collectPolicies(LayoutNode node, Map<String, ModuleLayoutPolicy> policies) {
        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            policies.putIfAbsent(
                    leaf.moduleId(),
                    new ModuleLayoutPolicy(leaf.visible(), true, true));
        } else if (node instanceof SplitNode) {
            SplitNode split = (SplitNode) node;
            collectPolicies(split.first(), policies);
            collectPolicies(split.second(), policies);
        }
    }

    public DockWorkspace(
            List<FloatingRoot> roots,
            Map<String, ModuleLayoutPolicy> policies,
            Map<String, DockSize> restoreSizes,
            Map<String, ContentOffset> contentOffsets) {
        if (roots == null || policies == null || restoreSizes == null || contentOffsets == null) {
            throw new NullPointerException("roots, policies, restoreSizes and contentOffsets");
        }
        this.roots = copyOfList(roots);
        this.policies = copyOfMap(policies);
        this.restoreSizes = copyOfMap(restoreSizes);
        this.contentOffsets = copyOfMap(contentOffsets);
    }

    /** Java 8 等价实现：{@code List.copyOf}，返回拒绝 null 元素的不可变副本。 */
    private static <T> List<T> copyOfList(List<T> source) {
        List<T> copy = new ArrayList<T>(source.size());
        for (T element : source) {
            // JDK 的 List.copyOf 对 null 元素抛出无消息的 NullPointerException
            copy.add(Objects.requireNonNull(element));
        }
        return Collections.unmodifiableList(copy);
    }

    /** Java 8 等价实现：{@code Map.copyOf}，保持插入顺序并拒绝 null 键与 null 值。 */
    private static <K, V> Map<K, V> copyOfMap(Map<K, V> source) {
        Map<K, V> copy = new LinkedHashMap<K, V>(Math.max(1, source.size()));
        for (Map.Entry<K, V> entry : source.entrySet()) {
            // JDK 的 Map.copyOf 对 null 键/值抛出无消息的 NullPointerException
            K key = Objects.requireNonNull(entry.getKey());
            V value = Objects.requireNonNull(entry.getValue());
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    public List<FloatingRoot> roots() {
        return roots;
    }

    public Map<String, ModuleLayoutPolicy> policies() {
        return policies;
    }

    public Map<String, DockSize> restoreSizes() {
        return restoreSizes;
    }

    public Map<String, ContentOffset> contentOffsets() {
        return contentOffsets;
    }

    public ModuleLayoutPolicy policyFor(String moduleId) {
        return policies.getOrDefault(moduleId, ModuleLayoutPolicy.defaults());
    }

    public DockWorkspace withRoots(List<FloatingRoot> newRoots) {
        return new DockWorkspace(newRoots, policies, restoreSizes, contentOffsets);
    }

    public DockWorkspace withPolicies(Map<String, ModuleLayoutPolicy> newPolicies) {
        return new DockWorkspace(roots, newPolicies, restoreSizes, contentOffsets);
    }

    public DockWorkspace withRestoreSizes(Map<String, DockSize> newRestoreSizes) {
        return new DockWorkspace(roots, policies, newRestoreSizes, contentOffsets);
    }

    public DockWorkspace withContentOffsets(Map<String, ContentOffset> newContentOffsets) {
        return new DockWorkspace(roots, policies, restoreSizes, newContentOffsets);
    }

    public ContentOffset contentOffset(String moduleId) {
        return contentOffsets.getOrDefault(moduleId, ContentOffset.ZERO);
    }

    public DockWorkspace rebuilt(List<FloatingRoot> newRoots, Map<String, DockSize> newRestoreSizes) {
        return new DockWorkspace(newRoots, policies, newRestoreSizes, contentOffsets);
    }

    /**
     * Last standalone window size for {@code nodeId}, placed at {@code fallback}'s origin.
     * Used when unsplicing so a panel does not keep the combined occupancy of its former siblings.
     */
    public DockRect restoredBounds(String nodeId, DockRect fallback) {
        if (fallback == null) {
            return null;
        }
        DockSize restore = restoreSizes.get(nodeId);
        if (restore == null || restore.isEmpty()) {
            return fallback;
        }
        return fallback.withSize(restore);
    }

    public static Map<String, DockSize> retainRestoreSizes(
            Map<String, DockSize> restoreSizes,
            List<FloatingRoot> roots) {
        if (restoreSizes == null || restoreSizes.isEmpty() || roots == null) {
            return Collections.emptyMap();
        }
        Set<String> nodeIds = new LinkedHashSet<String>();
        for (FloatingRoot root : roots) {
            collectNodeIds(root.content(), nodeIds);
        }
        Map<String, DockSize> retained = new LinkedHashMap<String, DockSize>();
        for (Map.Entry<String, DockSize> entry : restoreSizes.entrySet()) {
            if (nodeIds.contains(entry.getKey())) {
                retained.put(entry.getKey(), entry.getValue());
            }
        }
        return retained;
    }

    private static void collectNodeIds(LayoutNode node, Set<String> nodeIds) {
        nodeIds.add(node.nodeId());
        if (node instanceof SplitNode) {
            SplitNode split = (SplitNode) node;
            collectNodeIds(split.first(), nodeIds);
            collectNodeIds(split.second(), nodeIds);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DockWorkspace other = (DockWorkspace) o;
        return roots.equals(other.roots)
                && policies.equals(other.policies)
                && restoreSizes.equals(other.restoreSizes)
                && contentOffsets.equals(other.contentOffsets);
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + roots.hashCode();
        result = 31 * result + policies.hashCode();
        result = 31 * result + restoreSizes.hashCode();
        result = 31 * result + contentOffsets.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "DockWorkspace[roots=" + roots + ", policies=" + policies + ", restoreSizes=" + restoreSizes
                + ", contentOffsets=" + contentOffsets + "]";
    }
}
