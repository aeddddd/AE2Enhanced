package com.github.aeddddd.ae2enhanced.tile.network;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;

import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 单个服务器生命周期内的无线接收节点连接索引。 */
public final class WirelessChannelConnectionRegistry implements AutoCloseable {

    private final Map<IGridNode, IGridConnection> connections = new HashMap<>();

    public WirelessChannelConnectionRegistry() {
    }

    @Nullable
    static WirelessChannelConnectionRegistry activeInstance() {
        com.github.aeddddd.ae2enhanced.runtime.ServerScope scope = RuntimeScopes.currentServer().orElse(null);
        if (scope == null || !scope.acceptingWork()) {
            return null;
        }
        return scope.service(WirelessChannelConnectionRegistry.class, WirelessChannelConnectionRegistry::new);
    }

    static boolean destroySafely(IGridConnection connection, String description) {
        try {
            connection.destroy();
            return true;
        } catch (Exception exception) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to destroy " + description, exception);
            return false;
        }
    }

    @Nullable
    public IGridConnection get(IGridNode node) {
        return connections.get(node);
    }

    public void register(IGridNode node, IGridConnection connection) {
        IGridConnection previous = connections.put(
                Objects.requireNonNull(node, "node"), Objects.requireNonNull(connection, "connection"));
        if (previous != null && previous != connection) {
            destroySafely(previous, "replaced wireless connection");
        }
    }

    @Nullable
    public IGridConnection remove(IGridNode node) {
        return connections.remove(node);
    }

    public boolean remove(IGridNode node, IGridConnection expected) {
        return connections.remove(node, expected);
    }

    public boolean destroy(IGridNode node) {
        IGridConnection removed = connections.remove(node);
        return removed != null && destroySafely(removed, "wireless grid connection");
    }

    public List<Map.Entry<IGridNode, IGridConnection>> snapshot() {
        List<Map.Entry<IGridNode, IGridConnection>> result = new ArrayList<>(connections.size());
        for (Map.Entry<IGridNode, IGridConnection> entry : connections.entrySet()) {
            result.add(new AbstractMap.SimpleImmutableEntry<>(entry));
        }
        return result;
    }

    public int connectionCount() {
        return connections.size();
    }

    @Override
    public void close() {
        List<IGridConnection> oldConnections = new ArrayList<>(connections.values());
        connections.clear();
        for (IGridConnection connection : oldConnections) {
            destroySafely(connection, "wireless connection during server shutdown");
        }
    }
}
