package com.github.aeddddd.ae2enhanced.util.network;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import com.github.aeddddd.ae2enhanced.runtime.ServerScope;
import com.github.aeddddd.ae2enhanced.tile.network.WirelessChannelConnectionRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WirelessChannelConnectionRegistryTest {

    @Test
    void disconnectingOneNetworkDoesNotTouchAnotherNetwork() {
        WirelessChannelConnectionRegistry registry = new WirelessChannelConnectionRegistry();
        IGridNode firstNode = mock(IGridNode.class);
        IGridNode secondNode = mock(IGridNode.class);
        IGridConnection firstConnection = mock(IGridConnection.class);
        IGridConnection secondConnection = mock(IGridConnection.class);
        registry.register(firstNode, firstConnection);
        registry.register(secondNode, secondConnection);

        assertThat(registry.destroy(firstNode)).isTrue();

        verify(firstConnection).destroy();
        verify(secondConnection, never()).destroy();
        assertThat(registry.get(firstNode)).isNull();
        assertThat(registry.get(secondNode)).isSameAs(secondConnection);
        assertThat(registry.connectionCount()).isEqualTo(1);
        registry.close();
    }

    @Test
    void destroyFailureIsUnregisteredAndCannotBlockReconnect() {
        WirelessChannelConnectionRegistry registry = new WirelessChannelConnectionRegistry();
        IGridNode node = mock(IGridNode.class);
        IGridConnection broken = mock(IGridConnection.class);
        doThrow(new IllegalStateException("disconnect failed")).when(broken).destroy();
        registry.register(node, broken);

        assertThat(registry.destroy(node)).isFalse();
        assertThat(registry.get(node)).isNull();
        assertThat(registry.connectionCount()).isZero();

        IGridConnection replacement = mock(IGridConnection.class);
        registry.register(node, replacement);
        assertThat(registry.get(node)).isSameAs(replacement);
        verify(broken).destroy();
        verify(replacement, never()).destroy();
        registry.close();
    }

    @Test
    void closeAttemptsEveryConnectionAndClearsTheIndex() {
        WirelessChannelConnectionRegistry registry = new WirelessChannelConnectionRegistry();
        IGridConnection broken = mock(IGridConnection.class);
        IGridConnection healthy = mock(IGridConnection.class);
        doThrow(new IllegalStateException("shutdown failure")).when(broken).destroy();
        registry.register(mock(IGridNode.class), broken);
        registry.register(mock(IGridNode.class), healthy);

        registry.close();

        verify(broken).destroy();
        verify(healthy).destroy();
        assertThat(registry.connectionCount()).isZero();
    }

    @Test
    void serverScopesAndABAReopenNeverReuseAnOldConnection() {
        ServerScope firstScope = new ServerScope();
        WirelessChannelConnectionRegistry first = firstScope.service(
                WirelessChannelConnectionRegistry.class, WirelessChannelConnectionRegistry::new);
        IGridNode oldNode = mock(IGridNode.class);
        IGridConnection oldConnection = mock(IGridConnection.class);
        first.register(oldNode, oldConnection);

        ServerScope secondScope = new ServerScope();
        WirelessChannelConnectionRegistry second = secondScope.service(
                WirelessChannelConnectionRegistry.class, WirelessChannelConnectionRegistry::new);
        assertThat(second.connectionCount()).isZero();
        assertThat(second.get(oldNode)).isNull();
        firstScope.close();
        verify(oldConnection).destroy();
        assertThat(first.connectionCount()).isZero();

        ServerScope reopenedScope = new ServerScope();
        WirelessChannelConnectionRegistry reopened = reopenedScope.service(
                WirelessChannelConnectionRegistry.class, WirelessChannelConnectionRegistry::new);
        assertThat(reopened.get(oldNode)).isNull();
        assertThat(reopened.connectionCount()).isZero();
        IGridConnection newConnection = mock(IGridConnection.class);
        reopened.register(oldNode, newConnection);
        assertThat(reopened.get(oldNode)).isSameAs(newConnection);

        secondScope.close();
        reopenedScope.close();
    }
}
