/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleDefaults;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleLayoutPolicy;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.NodeIds;

/** Deterministic default roots for registered modules. */
public final class DockWorkspaceDefaults {
    private static final Logger LOGGER = LogManager.getLogger(DockWorkspaceDefaults.class);

    // TODO(AE2E): 适配 AE2Enhanced 资源命名空间与模块集；本 mod 尚未提供该内置布局资源，
    // getResourceAsStream 返回 null 时会退化为下面的"按 catalog 顺序级联摆放"路径。
    private static final String BUNDLED_LAYOUT = "/assets/mesplicedterminal/layouts/default.json";

    // TODO(AE2E): 适配 AE2Enhanced 模块集；network_toolkit 是 MEST 的模块 id，
    // 仅用于 v3/v4 版本的历史摆放位置补偿，机制本身由 ModuleCatalog 驱动。
    public static final String NETWORK_TOOLKIT_MODULE = "network_toolkit";
    /**
     * Placement deltas by source layout version: index 3 is the v3→v4 shift, index 4 the v4→v5 one.
     * {@link #NETWORK_TOOLKIT_SHIFT_X} is their sum, i.e. the offset new placements use.
     */
    private static final int[] SHIFT_BY_FROM_VERSION = { 0, 0, 0, 4, 2 };
    /** The network-tool panel sits 6px further left than the generic cascade placement. */
    public static final int NETWORK_TOOLKIT_SHIFT_X = 6;

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

    /** @param shellFit see {@link DockLayoutCodec}; the bundled layout predates the shell-only format */
    public static DockWorkspace create(ModuleCatalog catalog, LegacyMigrationContext geometry,
            UnaryOperator<DockWorkspace> shellFit) {
        DockWorkspace bundled = loadBundled(catalog, geometry, shellFit);
        if (bundled != null) {
            return bundled;
        }
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

    private static DockWorkspace loadBundled(ModuleCatalog catalog, LegacyMigrationContext geometry,
            UnaryOperator<DockWorkspace> shellFit) {
        try (InputStream in = DockWorkspaceDefaults.class.getResourceAsStream(BUNDLED_LAYOUT)) {
            if (in == null) {
                return null;
            }
            String json = new String(readAllBytes(in), StandardCharsets.UTF_8);
            return new DockLayoutCodec(catalog, geometry, shellFit).decode(json).workspace();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to load bundled default layout", e);
            return null;
        }
    }

    /** Java 8 等价实现：{@code InputStream.readAllBytes()}。 */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, in.available()));
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    public static String leafNodeId(String moduleId) {
        return NodeIds.deterministic("leaf", moduleId);
    }

    public static String rootId(String moduleId) {
        return NodeIds.deterministic("root", moduleId);
    }
}
