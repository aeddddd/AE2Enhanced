package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.me.helpers.AENetworkProxy;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.CompatibilityFixtures;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;

/** Central 静态旧 NBT 的 Tile/Duality 双层兼容测试。 */
class CentralLegacyCompatibilityTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static void assertRestored(DualityCentralInterface duality) {
        assertThat(duality.getConfig().getStackInSlot(2).getItem()).isEqualTo(Items.STICK);
        assertThat(duality.getConfig().getStackInSlot(2).getCount()).isEqualTo(5);
        assertThat(duality.getPatterns().getStackInSlot(1).getItem()).isEqualTo(Items.PAPER);
        assertThat(duality.getStorage().getStackInSlot(4).getItem()).isEqualTo(Items.COAL);
        assertThat(duality.getStorage().getStackInSlot(4).getCount()).isEqualTo(9);
        assertThat(duality.getBindings()).hasSize(1);
        assertThat(duality.getBindings().get(0).pos).isEqualTo(new BlockPos(10, 64, 20));
        List<ItemStack> pending = duality.pendingProducts.drainAll();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getItem()).isEqualTo(Items.DIAMOND);
        assertThat(pending.get(0).getCount()).isEqualTo(2);
        assertThat(pending.get(0).getTagCompound().getString("origin")).isEqualTo("legacy");

        CentralFluidBuffer fluidBuffer = fluidBuffer(duality);
        assertThat(fluidBuffer.entries()).hasSize(1);
        CentralFluidBuffer.Entry fluid = fluidBuffer.entries().get(0);
        assertThat(fluid.fluid().getFluid()).isEqualTo(FluidRegistry.WATER);
        assertThat(fluid.fluid().amount).isEqualTo(2500);
        assertThat(fluid.attempts()).isEqualTo(3);
    }

    @SuppressWarnings("unchecked")
    private static CentralFluidBuffer fluidBuffer(DualityCentralInterface duality) {
        try {
            Field field = DualityCentralInterface.class.getDeclaredField("fluidBuffer");
            field.setAccessible(true);
            return (CentralFluidBuffer) field.get(duality);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void tileRestoresLegacyInventoriesQueuesFluidsUpgradesButNotRuntimeSession() {
        NBTTagCompound legacy = CompatibilityFixtures.readNbt("central/legacy-v0.nbt");
        TileCentralMEInterface tile = new TileCentralMEInterface();
        tile.readFromNBT(legacy);
        DualityCentralInterface duality = tile.getInterfaceDuality();

        assertRestored(duality);
        assertThat(tile.getInventoryByName("upgrades").getStackInSlot(0).getItem()).isEqualTo(Items.STICK);
        assertThat(duality.sessions).hasSize(1);
        assertThat(duality.sessions.values().iterator().next().getState()).isEqualTo(TargetState.IDLE);
    }

    @Test
    void dualityReadsSameFixtureIndependentlyOfTile() {
        ICentralInterfaceHost host = mock(ICentralInterfaceHost.class);
        TileEntity tile = mock(TileEntity.class);
        when(host.getTileEntity()).thenReturn(tile);
        when(tile.getWorld()).thenReturn(mock(World.class));
        when(host.getProxy()).thenReturn(mock(AENetworkProxy.class));
        DualityCentralInterface duality = new DualityCentralInterface(host);

        duality.readFromNBT(CompatibilityFixtures.readNbt("central/legacy-v0.nbt"));

        assertRestored(duality);
        assertThat(duality.sessions.values()).allMatch(TargetSession::isIdle);
    }
}
