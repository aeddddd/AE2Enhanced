package com.github.aeddddd.ae2enhanced.collector;

import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.runtime.ServerScope;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollectorRegistryLifecycleTest {

    private static World world(int dimension) throws Exception {
        World world = mock(World.class);
        WorldProvider provider = mock(WorldProvider.class);
        when(provider.getDimension()).thenReturn(dimension);
        Field field = World.class.getDeclaredField("provider");
        field.setAccessible(true);
        field.set(world, provider);
        return world;
    }

    @BeforeEach
    void startScope() {
        closeScope();
        RuntimeScopes.startServer();
    }

    @AfterEach
    void closeScope() {
        RuntimeScopes.currentServer().ifPresent(scope -> {
            scope.close();
            RuntimeScopes.releaseServer();
        });
    }

    @Test
    void abaReopenCannotFindThePreviousWorldsCollector() throws Exception {
        World oldWorld = world(0);
        TileAdvancedMECollector oldCollector = mock(TileAdvancedMECollector.class);
        when(oldCollector.getWorld()).thenReturn(oldWorld);
        CollectorRegistry.register(oldCollector);
        ServerScope first = RuntimeScopes.requireServer();
        CollectorRegistry firstRegistry = first.service(CollectorRegistry.class, CollectorRegistry::new);
        assertThat(firstRegistry.registeredCollectorCount()).isEqualTo(1);
        assertThat(CollectorRegistry.hasCollectors(oldWorld)).isTrue();
        RuntimeScopes.closeServer();
        RuntimeScopes.releaseServer();
        assertThat(firstRegistry.registeredCollectorCount()).isZero();
        assertThat(CollectorRegistry.hasCollectors(oldWorld)).isFalse();

        RuntimeScopes.startServer();
        assertThat(CollectorRegistry.hasCollectors(oldWorld)).isFalse();
        assertThat(CollectorRegistry.findBestCollector(oldWorld, BlockPos.ORIGIN)).isNull();
        RuntimeScopes.closeServer();
        RuntimeScopes.releaseServer();

        RuntimeScopes.startServer();
        CollectorRegistry reopened = RuntimeScopes.requireServer().service(
                CollectorRegistry.class, CollectorRegistry::new);
        assertThat(reopened.registeredCollectorCount()).isZero();
        assertThat(CollectorRegistry.hasCollectors(oldWorld)).isFalse();
    }
}
