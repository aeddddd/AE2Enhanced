package com.github.aeddddd.ae2enhanced.tile;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TileComputationCoreClientSyncTest {
    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        if (net.minecraft.tileentity.TileEntity.getKey(TileComputationCore.class) == null) {
            net.minecraft.tileentity.TileEntity.register("ae2enhanced:computation_core", TileComputationCore.class);
        }
    }

    @Test
    void updateTagContainsOnlyLightweightRealTaskSummaries() {
        TileComputationCore server = new TileComputationCore();
        CraftingCPUCluster primary = mock(CraftingCPUCluster.class);
        when(primary.isBusy()).thenReturn(false);
        when(primary.getName()).thenReturn("primary");
        when(primary.getFinalOutput()).thenReturn(AEItemStack.fromItemStack(new ItemStack(Items.STICK, 8)));
        when(primary.getStartItemCount()).thenReturn(20L);
        when(primary.getRemainingItemCount()).thenReturn(12L);
        when(primary.getElapsedTime()).thenReturn(345L);
        CraftingCPUCluster extra = mock(CraftingCPUCluster.class);
        when(extra.isBusy()).thenReturn(true);
        when(extra.getName()).thenReturn("extra");
        when(extra.getStartItemCount()).thenReturn(5L);
        when(extra.getRemainingItemCount()).thenReturn(5L);
        CraftingCPUCluster idle = mock(CraftingCPUCluster.class);
        server.getCpuPool().add(primary);
        server.getCpuPool().add(extra);
        server.getCpuPool().add(idle);

        NBTTagCompound update = server.getUpdateTag();
        assertThat(update.hasKey("cpuPool")).isFalse();
        NBTTagList tasks = update.getTagList("clientTasks", 10);
        assertThat(tasks.tagCount()).isEqualTo(2);
        assertThat(tasks.getCompoundTagAt(0).getString("name")).isEqualTo("primary");
        assertThat(tasks.getCompoundTagAt(0).getLong("remaining")).isEqualTo(12);
        assertThat(new ItemStack(tasks.getCompoundTagAt(0).getCompoundTag("output")).getCount()).isEqualTo(8);
        assertThat(server.getActiveOrderCount()).isEqualTo(2);
        assertThat(server.writeToNBT(new NBTTagCompound()).getTagList("cpuPool", 10).tagCount())
                .isEqualTo(3);
    }

    @Test
    void repeatedClientSyncClearsOldSummariesWithoutCreatingCpuPool() throws Exception {
        World clientWorld = mock(World.class);
        java.lang.reflect.Field remote = World.class.getDeclaredField("isRemote");
        remote.setAccessible(true);
        java.lang.reflect.Field modifiers = java.lang.reflect.Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(remote, remote.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
        remote.set(clientWorld, true);
        TileComputationCore client = new TileComputationCore();
        client.proxy = mock(appeng.me.helpers.AENetworkProxy.class);
        client.setWorld(clientWorld);
        NBTTagCompound first = new NBTTagCompound();
        first.setBoolean("formed", true);
        first.setInteger("parallelLimit", 4096);
        NBTTagList tasks = new NBTTagList();
        tasks.appendTag(new ComputationCoreTaskSummary("one", new ItemStack(Items.APPLE), 4, 3, 2).writeToNBT());
        tasks.appendTag(new ComputationCoreTaskSummary("two", ItemStack.EMPTY, 2, 2, 1).writeToNBT());
        first.setTag("clientTasks", tasks);

        client.handleUpdateTag(first);
        assertThat(client.isFormed()).isTrue();
        assertThat(client.getParallelLimit()).isEqualTo(4096);
        assertThat(client.getActiveOrderCount()).isEqualTo(2);
        assertThat(client.getCpuPool()).isEmpty();

        NBTTagCompound second = new NBTTagCompound();
        second.setBoolean("formed", false);
        second.setInteger("parallelLimit", 0);
        second.setTag("clientTasks", new NBTTagList());
        client.onDataPacket(null, new net.minecraft.network.play.server.SPacketUpdateTileEntity(
                net.minecraft.util.math.BlockPos.ORIGIN, 0, second));
        assertThat(client.isFormed()).isFalse();
        assertThat(client.getTaskSummaries()).isEmpty();
        assertThat(client.getActiveOrderCount()).isZero();
        assertThat(client.getCpuPool()).isEmpty();
    }
}
