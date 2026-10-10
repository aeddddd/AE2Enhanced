/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ContentOffset;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockAxis;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize;

/** Versioned persistence DTO, deliberately separate from the runtime tree. */
public final class DockLayoutDto {
    /**
     * v4 tightened the network-tool panel's placement; v5 tightened it by another 2px; v6 dropped
     * the stretch and compact splice modes, so every window is an outer shell; v7 made floating
     * and pinned a property of the window instead of its modules; v8 added combined-module slots.
     */
    public static final int CURRENT_VERSION = 8;
    /** Oldest version this codec still decodes. */
    public static final int MIN_SUPPORTED_VERSION = 2;

    private final int version;
    private final List<RootDto> roots;
    private final Map<String, PolicyDto> policies;
    private final Map<String, DockSize> restoreSizes;
    private final Map<String, ContentOffset> contentOffsets;

    public DockLayoutDto(int version, List<RootDto> roots) {
        this(version, roots, Collections.<String, PolicyDto>emptyMap(),
                Collections.<String, DockSize>emptyMap(), Collections.<String, ContentOffset>emptyMap());
    }

    public DockLayoutDto(int version, List<RootDto> roots, Map<String, PolicyDto> policies) {
        this(version, roots, policies, Collections.<String, DockSize>emptyMap(),
                Collections.<String, ContentOffset>emptyMap());
    }

    public DockLayoutDto(
            int version,
            List<RootDto> roots,
            Map<String, PolicyDto> policies,
            Map<String, DockSize> restoreSizes) {
        this(version, roots, policies, restoreSizes, Collections.<String, ContentOffset>emptyMap());
    }

    public DockLayoutDto(
            int version,
            List<RootDto> roots,
            Map<String, PolicyDto> policies,
            Map<String, DockSize> restoreSizes,
            Map<String, ContentOffset> contentOffsets) {
        this.version = version;
        this.roots = roots == null ? null : copyOfList(roots);
        this.policies = policies == null ? null : copyOfMap(policies);
        this.restoreSizes = restoreSizes == null ? Collections.<String, DockSize>emptyMap() : copyOfMap(restoreSizes);
        this.contentOffsets = contentOffsets == null
                ? Collections.<String, ContentOffset>emptyMap()
                : copyOfMap(contentOffsets);
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

    public int version() {
        return version;
    }

    public List<RootDto> roots() {
        return roots;
    }

    public Map<String, PolicyDto> policies() {
        return policies;
    }

    public Map<String, DockSize> restoreSizes() {
        return restoreSizes;
    }

    public Map<String, ContentOffset> contentOffsets() {
        return contentOffsets;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DockLayoutDto other = (DockLayoutDto) o;
        return version == other.version
                && Objects.equals(roots, other.roots)
                && Objects.equals(policies, other.policies)
                && Objects.equals(restoreSizes, other.restoreSizes)
                && Objects.equals(contentOffsets, other.contentOffsets);
    }

    @Override
    public int hashCode() {
        // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
        int result = 0;
        result = 31 * result + Integer.hashCode(version);
        result = 31 * result + Objects.hashCode(roots);
        result = 31 * result + Objects.hashCode(policies);
        result = 31 * result + Objects.hashCode(restoreSizes);
        result = 31 * result + Objects.hashCode(contentOffsets);
        return result;
    }

    @Override
    public String toString() {
        return "DockLayoutDto[version=" + version
                + ", roots=" + roots
                + ", policies=" + policies
                + ", restoreSizes=" + restoreSizes
                + ", contentOffsets=" + contentOffsets + "]";
    }

    public static final class RootDto {
        private final String rootId;
        private final DockRect bounds;
        private final NodeDto content;
        private final boolean floating;
        private final boolean pinned;
        private final int group;
        private final boolean hidden;

        public RootDto(String rootId, DockRect bounds, NodeDto content) {
            this(rootId, bounds, content, false, false, 0, false);
        }

        public RootDto(
                String rootId,
                DockRect bounds,
                NodeDto content,
                boolean floating,
                boolean pinned,
                int group,
                boolean hidden) {
            this.rootId = rootId;
            this.bounds = bounds;
            this.content = content;
            this.floating = floating;
            this.pinned = pinned;
            this.group = group;
            this.hidden = hidden;
        }

        public String rootId() {
            return rootId;
        }

        public DockRect bounds() {
            return bounds;
        }

        public NodeDto content() {
            return content;
        }

        public boolean floating() {
            return floating;
        }

        public boolean pinned() {
            return pinned;
        }

        public int group() {
            return group;
        }

        public boolean hidden() {
            return hidden;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RootDto other = (RootDto) o;
            return floating == other.floating
                    && pinned == other.pinned
                    && group == other.group
                    && hidden == other.hidden
                    && Objects.equals(rootId, other.rootId)
                    && Objects.equals(bounds, other.bounds)
                    && Objects.equals(content, other.content);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + Objects.hashCode(rootId);
            result = 31 * result + Objects.hashCode(bounds);
            result = 31 * result + Objects.hashCode(content);
            result = 31 * result + Boolean.hashCode(floating);
            result = 31 * result + Boolean.hashCode(pinned);
            result = 31 * result + Integer.hashCode(group);
            result = 31 * result + Boolean.hashCode(hidden);
            return result;
        }

        @Override
        public String toString() {
            return "RootDto[rootId=" + rootId + ", bounds=" + bounds + ", content=" + content
                    + ", floating=" + floating + ", pinned=" + pinned + ", group=" + group
                    + ", hidden=" + hidden + "]";
        }
    }

    public static final class PolicyDto {
        private final boolean visible;
        private final boolean movable;
        private final boolean resizable;
        private final boolean floating;
        private final boolean pinned;
        private final boolean showTerminalButton;

        public PolicyDto(boolean visible, boolean movable, boolean resizable) {
            this(visible, movable, resizable, false, false, true);
        }

        public PolicyDto(boolean visible, boolean movable, boolean resizable, boolean floating) {
            this(visible, movable, resizable, floating, false, true);
        }

        public PolicyDto(boolean visible, boolean movable, boolean resizable, boolean floating, boolean pinned) {
            this(visible, movable, resizable, floating, pinned, true);
        }

        public PolicyDto(
                boolean visible,
                boolean movable,
                boolean resizable,
                boolean floating,
                boolean pinned,
                boolean showTerminalButton) {
            this.visible = visible;
            this.movable = movable;
            this.resizable = resizable;
            this.floating = floating;
            this.pinned = pinned;
            this.showTerminalButton = showTerminalButton;
        }

        public boolean visible() {
            return visible;
        }

        public boolean movable() {
            return movable;
        }

        public boolean resizable() {
            return resizable;
        }

        public boolean floating() {
            return floating;
        }

        public boolean pinned() {
            return pinned;
        }

        public boolean showTerminalButton() {
            return showTerminalButton;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PolicyDto other = (PolicyDto) o;
            return visible == other.visible
                    && movable == other.movable
                    && resizable == other.resizable
                    && floating == other.floating
                    && pinned == other.pinned
                    && showTerminalButton == other.showTerminalButton;
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + Boolean.hashCode(visible);
            result = 31 * result + Boolean.hashCode(movable);
            result = 31 * result + Boolean.hashCode(resizable);
            result = 31 * result + Boolean.hashCode(floating);
            result = 31 * result + Boolean.hashCode(pinned);
            result = 31 * result + Boolean.hashCode(showTerminalButton);
            return result;
        }

        @Override
        public String toString() {
            return "PolicyDto[visible=" + visible + ", movable=" + movable + ", resizable=" + resizable
                    + ", floating=" + floating + ", pinned=" + pinned
                    + ", showTerminalButton=" + showTerminalButton + "]";
        }
    }

    /**
     * Java 8 降级说明：原实现为 {@code sealed interface NodeDto permits LeafDto, SplitDto}，
     * Java 8 没有 sealed 类型，此处退化为普通 interface。
     */
    public interface NodeDto {
        String nodeId();
    }

    public static final class LeafDto implements NodeDto {
        private final String nodeId;
        private final String moduleId;
        private final boolean visible;

        public LeafDto(String nodeId, String moduleId, boolean visible) {
            this.nodeId = nodeId;
            this.moduleId = moduleId;
            this.visible = visible;
        }

        @Override
        public String nodeId() {
            return nodeId;
        }

        public String moduleId() {
            return moduleId;
        }

        public boolean visible() {
            return visible;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LeafDto other = (LeafDto) o;
            return visible == other.visible
                    && Objects.equals(nodeId, other.nodeId)
                    && Objects.equals(moduleId, other.moduleId);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + Objects.hashCode(nodeId);
            result = 31 * result + Objects.hashCode(moduleId);
            result = 31 * result + Boolean.hashCode(visible);
            return result;
        }

        @Override
        public String toString() {
            return "LeafDto[nodeId=" + nodeId + ", moduleId=" + moduleId + ", visible=" + visible + "]";
        }
    }

    public static final class SplitDto implements NodeDto {
        private final String nodeId;
        private final DockAxis axis;
        private final double ratio;
        private final NodeDto first;
        private final NodeDto second;

        public SplitDto(String nodeId, DockAxis axis, double ratio, NodeDto first, NodeDto second) {
            this.nodeId = nodeId;
            this.axis = axis;
            this.ratio = ratio;
            this.first = first;
            this.second = second;
        }

        @Override
        public String nodeId() {
            return nodeId;
        }

        public DockAxis axis() {
            return axis;
        }

        public double ratio() {
            return ratio;
        }

        public NodeDto first() {
            return first;
        }

        public NodeDto second() {
            return second;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SplitDto other = (SplitDto) o;
            // record 语义: double 分量按 Double.compare 比较（NaN 等于自身，-0.0 不等于 0.0）
            return Double.compare(ratio, other.ratio) == 0
                    && Objects.equals(nodeId, other.nodeId)
                    && axis == other.axis
                    && Objects.equals(first, other.first)
                    && Objects.equals(second, other.second);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + Objects.hashCode(nodeId);
            result = 31 * result + Objects.hashCode(axis);
            result = 31 * result + Double.hashCode(ratio);
            result = 31 * result + Objects.hashCode(first);
            result = 31 * result + Objects.hashCode(second);
            return result;
        }

        @Override
        public String toString() {
            return "SplitDto[nodeId=" + nodeId + ", axis=" + axis + ", ratio=" + ratio
                    + ", first=" + first + ", second=" + second + "]";
        }
    }
}
