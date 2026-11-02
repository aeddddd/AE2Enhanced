package com.github.aeddddd.ae2enhanced.tile;

import appeng.me.helpers.AENetworkProxy;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TileDelegatedProxyBaseTest {
    private static final BlockPos CONTROLLER_POS = new BlockPos(4, 65, -8);

    @org.junit.jupiter.api.BeforeAll
    static void boot() {
        com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap.boot();
        if (TileEntity.getKey(TestProxy.class) == null) {
            TileEntity.register("ae2enhanced:test_proxy", TestProxy.class);
        }
    }

    private static TestController controller(World world) {
        TestController controller = new TestController();
        controller.setWorld(world);
        controller.setPos(CONTROLLER_POS);
        return controller;
    }

    @Test
    void cacheChecksLoadedStatePositionAndCurrentTileIdentity() {
        World world = mock(World.class);
        when(world.getBlockState(org.mockito.ArgumentMatchers.any()))
                .thenReturn(net.minecraft.init.Blocks.STONE.getDefaultState());
        AtomicReference<TileEntity> current = new AtomicReference<>();
        when(world.isBlockLoaded(CONTROLLER_POS)).thenReturn(true);
        when(world.getTileEntity(CONTROLLER_POS)).thenAnswer(invocation -> current.get());
        TestProxy proxy = new TestProxy();
        proxy.setWorld(world);
        proxy.setControllerPos(CONTROLLER_POS);
        TestController first = controller(world);
        TestController second = controller(world);
        current.set(first);

        assertThat(proxy.getController()).isSameAs(first);
        assertThat(proxy.getController()).isSameAs(first);
        current.set(second);
        assertThat(proxy.getController()).isSameAs(second);
        when(world.isBlockLoaded(CONTROLLER_POS)).thenReturn(false);
        assertThat(proxy.getController()).isNull();
        when(world.isBlockLoaded(CONTROLLER_POS)).thenReturn(true);
        proxy.onChunkUnload();
        assertThat(proxy.getController()).isSameAs(second);
        proxy.securityBreak();
        assertThat(second.disassembleCalls).isEqualTo(1);
    }

    @Test
    void missingControllerNbtClearsPreviousBindingAndCachedIdentity() {
        TestProxy proxy = new TestProxy();
        NBTTagCompound bound = new NBTTagCompound();
        bound.setInteger("controllerX", 1);
        bound.setInteger("controllerY", 2);
        bound.setInteger("controllerZ", 3);
        proxy.readFromNBT(bound);
        assertThat(proxy.getControllerPos()).isEqualTo(new BlockPos(1, 2, 3));

        proxy.readFromNBT(new NBTTagCompound());
        assertThat(proxy.getControllerPos()).isNull();
        NBTTagCompound reused = bound.copy();
        proxy.writeToNBT(reused);
        assertThat(reused.hasKey("controllerX")).isFalse();
        assertThat(reused.hasKey("controllerY")).isFalse();
        assertThat(reused.hasKey("controllerZ")).isFalse();
    }

    private static final class TestProxy extends TileDelegatedProxyBase<TestController> {
        @Override
        protected Class<TestController> getControllerClass() {
            return TestController.class;
        }

        @Override
        protected boolean isControllerFormed(TestController controller) {
            return true;
        }
    }

    private static final class TestController extends TileAENetworkBase {
        private int disassembleCalls;

        @Override
        protected String getProxyName() {
            return "test";
        }

        @Override
        protected ItemStack getProxyRepresentation() {
            return ItemStack.EMPTY;
        }

        @Override
        public AENetworkProxy getProxy() {
            return null;
        }

        @Override
        public void disassemble() {
            disassembleCalls++;
        }

        @Override
        public void securityBreak() {
            disassemble();
        }

        @Override
        public appeng.api.util.AECableType getCableConnectionType(appeng.api.util.AEPartLocation dir) {
            return appeng.api.util.AECableType.NONE;
        }
    }
}
