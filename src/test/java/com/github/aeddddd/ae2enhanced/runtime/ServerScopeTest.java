package com.github.aeddddd.ae2enhanced.runtime;

import com.github.aeddddd.ae2enhanced.centralinterface.DualityCentralInterface;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetBinding;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetOwnershipTracker;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerScopeTest {

    private static DualityCentralInterface liveOwner() {
        DualityCentralInterface owner = mock(DualityCentralInterface.class);
        when(owner.isAlive()).thenReturn(true);
        return owner;
    }

    @AfterEach
    void cleanGlobalScopes() {
        RuntimeScopes.disconnectClient();
        RuntimeScopes.currentServer().ifPresent(scope -> {
            scope.close();
            RuntimeScopes.releaseServer();
        });
    }

    @Test
    void ownershipIsIsolatedBetweenIndependentScopes() {
        ServerScope first = new ServerScope();
        ServerScope second = new ServerScope();
        TargetOwnershipTracker firstTracker = first.service(
                TargetOwnershipTracker.class, TargetOwnershipTracker::new);
        TargetOwnershipTracker secondTracker = second.service(
                TargetOwnershipTracker.class, TargetOwnershipTracker::new);
        DualityCentralInterface firstOwner = liveOwner();
        DualityCentralInterface secondOwner = liveOwner();
        TargetBinding binding = new TargetBinding(new BlockPos(1, 2, 3), 0, "minecraft:furnace");

        assertThat(firstTracker.tryAcquire(binding, firstOwner)).isTrue();
        assertThat(secondTracker.isOwner(binding, firstOwner)).isFalse();
        assertThat(secondTracker.tryAcquire(binding, secondOwner)).isTrue();
        assertThat(firstTracker.isOwner(binding, firstOwner)).isTrue();

        first.close();
        assertThat(firstTracker.trackedTargetCount()).isZero();
        assertThat(secondTracker.isOwner(binding, secondOwner)).isTrue();
        second.close();
    }

    @Test
    void stoppingRejectsServicesAndCloseDropsSaveRootAndOwnership() {
        ServerScope scope = new ServerScope();
        scope.bindSaveRoot(new java.io.File("build/test-world").toPath());
        TargetOwnershipTracker tracker = scope.service(TargetOwnershipTracker.class, TargetOwnershipTracker::new);
        DualityCentralInterface owner = liveOwner();
        TargetBinding binding = new TargetBinding(BlockPos.ORIGIN, 0, "minecraft:furnace");
        tracker.tryAcquire(binding, owner);

        scope.stopAcceptingWork();
        assertThatThrownBy(() -> scope.service(TargetOwnershipTracker.class, TargetOwnershipTracker::new))
                .isInstanceOf(IllegalStateException.class);
        scope.close();

        assertThat(scope.closed()).isTrue();
        assertThat(scope.saveRoot()).isEmpty();
        assertThat(scope.serviceCount()).isZero();
        assertThat(tracker.trackedTargetCount()).isZero();
    }

    @Test
    void serverShutdownDoesNotClearClientLayoutPreferences() {
        ClientSessionScope client = RuntimeScopes.connectClient();
        client.putLayoutPreference("terminal.dock", "left");
        client.putTransient("page", 3);
        RuntimeScopes.startServer();

        RuntimeScopes.stopServer();
        RuntimeScopes.closeServer();
        RuntimeScopes.releaseServer();

        assertThat(RuntimeScopes.currentClient()).containsSame(client);
        assertThat(client.layoutPreference("terminal.dock")).contains("left");
        RuntimeScopes.disconnectClient();
        ClientSessionScope reconnected = RuntimeScopes.connectClient();
        assertThat(reconnected.layoutPreference("terminal.dock")).contains("left");
        assertThat(reconnected.transientValue("page")).isEmpty();
    }
}
