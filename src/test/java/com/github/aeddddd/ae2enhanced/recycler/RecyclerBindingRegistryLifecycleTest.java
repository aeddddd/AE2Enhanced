package com.github.aeddddd.ae2enhanced.recycler;

import com.github.aeddddd.ae2enhanced.runtime.ServerScope;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RecyclerBindingRegistryLifecycleTest {

    @Test
    void exactAndPositionIndexesAreClosedWithTheirServerScope() {
        TargetManager.TargetRef ref = new TargetManager.TargetRef(0, BlockPos.ORIGIN, EnumFacing.UP);
        RecyclerNetworkHandler oldHandler = mock(RecyclerNetworkHandler.class);
        ServerScope firstScope = new ServerScope();
        RecyclerBindingRegistry first = firstScope.service(
                RecyclerBindingRegistry.class, RecyclerBindingRegistry::new);
        first.register(ref, oldHandler, null);
        assertThat(first.find(0, BlockPos.ORIGIN).getHandler()).isSameAs(oldHandler);

        ServerScope secondScope = new ServerScope();
        RecyclerBindingRegistry second = secondScope.service(
                RecyclerBindingRegistry.class, RecyclerBindingRegistry::new);
        assertThat(second.bindingCount()).isZero();
        assertThat(second.find(0, BlockPos.ORIGIN)).isNull();

        firstScope.close();
        assertThat(first.bindingCount()).isZero();
        assertThat(first.find(0, BlockPos.ORIGIN)).isNull();
        ServerScope reopenedScope = new ServerScope();
        RecyclerBindingRegistry reopened = reopenedScope.service(
                RecyclerBindingRegistry.class, RecyclerBindingRegistry::new);
        assertThat(reopened.find(0, BlockPos.ORIGIN)).isNull();
        RecyclerNetworkHandler newHandler = mock(RecyclerNetworkHandler.class);
        reopened.register(ref, newHandler, null);
        assertThat(reopened.find(0, BlockPos.ORIGIN).getHandler()).isSameAs(newHandler);

        secondScope.close();
        reopenedScope.close();
    }
}
