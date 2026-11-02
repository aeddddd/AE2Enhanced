package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.AENetworkProxy;
import com.github.aeddddd.ae2enhanced.collector.CollectorRegistry;
import com.github.aeddddd.ae2enhanced.collector.TileAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectorOutboxPersistenceTest {

    private TileAdvancedMECollector tile;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        if (net.minecraft.tileentity.TileEntity.getKey(TileAdvancedMECollector.class) == null) {
            net.minecraft.tileentity.TileEntity.register(
                    "ae2enhanced:advanced_me_collector", TileAdvancedMECollector.class);
        }
    }

    private static void flushBuffer(TileAdvancedMECollector tile) throws Exception {
        Method flush = TileAdvancedMECollector.class.getDeclaredMethod("flushBuffer");
        flush.setAccessible(true);
        flush.invoke(tile);
    }

    @BeforeEach
    void startScope() {
        RuntimeScopes.currentServer().ifPresent(scope -> {
            scope.close();
            RuntimeScopes.releaseServer();
        });
        RuntimeScopes.startServer();
    }

    @AfterEach
    void unregister() {
        if (tile != null) {
            CollectorRegistry.unregister(tile);
        }
        RuntimeScopes.closeServer();
        RuntimeScopes.releaseServer();
    }

    @Test
    void existingBufferIsThePersistentOutboxAcrossUnloadPartialFlushAndBreak() throws Exception {
        TileAdvancedMECollector original = new TileAdvancedMECollector();
        new NetworkFixture(original);
        ItemStack config = new ItemStack(Items.APPLE);
        original.getConfig().setStackInSlot(0, config);
        ItemStack buffered = new ItemStack(Items.APPLE, 10);
        buffered.setTagInfo("quality", new NBTTagString("persistent"));
        original.getBuffer().setStackInSlot(0, buffered);
        NBTTagCompound firstSave = original.writeToNBT(new NBTTagCompound());
        assertThat(firstSave.hasKey("buffer")).isTrue();
        assertThat(firstSave.hasKey("RecyclerOutbox")).isFalse();

        tile = new TileAdvancedMECollector();
        NetworkFixture restoredFixture = new NetworkFixture(tile);
        tile.readFromNBT(firstSave);
        tile.onChunkUnload();
        tile.onLoad();
        assertThat(tile.getConfig().getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(tile.getBuffer().getStackInSlot(0).getCount()).isEqualTo(10);
        assertThat(tile.getBuffer().getStackInSlot(0).getTagCompound().getString("quality"))
                .isEqualTo("persistent");
        when(restoredFixture.itemMonitor.injectItems(any(), eq(Actionable.MODULATE), any()))
                .thenAnswer(invocation -> {
                    IAEItemStack input = invocation.getArgument(0);
                    IAEItemStack remainder = input.copy();
                    remainder.setStackSize(6);
                    return remainder;
                });

        flushBuffer(tile);

        assertThat(tile.getBuffer().getStackInSlot(0).getCount()).isEqualTo(6);
        NBTTagCompound secondSave = tile.writeToNBT(new NBTTagCompound());
        TileAdvancedMECollector reloadedAgain = new TileAdvancedMECollector();
        new NetworkFixture(reloadedAgain);
        reloadedAgain.readFromNBT(secondSave);
        assertThat(reloadedAgain.getBuffer().getStackInSlot(0).getCount()).isEqualTo(6);
        assertThat(reloadedAgain.getConfig().getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);

        tile.dropContents();

        ArgumentCaptor<Entity> spawned = ArgumentCaptor.forClass(Entity.class);
        verify(restoredFixture.world).spawnEntity(spawned.capture());
        ItemStack dropped = ((EntityItem) spawned.getValue()).getItem();
        assertThat(dropped.getCount()).isEqualTo(6);
        assertThat(dropped.getTagCompound().getString("quality")).isEqualTo("persistent");
        assertThat(tile.getBuffer().getStackInSlot(0).isEmpty()).isTrue();
        assertThat(tile.getConfig().getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(tile.isActive()).isFalse();
        assertThat(CollectorRegistry.findBestCollector(restoredFixture.world, BlockPos.ORIGIN)).isNull();
    }

    private static final class NetworkFixture {
        final World world = mock(World.class);
        final IMEMonitor<IAEItemStack> itemMonitor = mock(IMEMonitor.class);

        NetworkFixture(TileAdvancedMECollector tile) throws Exception {
            WorldProvider provider = mock(WorldProvider.class);
            when(provider.getDimension()).thenReturn(0);
            Field worldProvider = World.class.getDeclaredField("provider");
            worldProvider.setAccessible(true);
            worldProvider.set(world, provider);
            when(world.getBlockState(any(BlockPos.class)))
                    .thenReturn(net.minecraft.init.Blocks.STONE.getDefaultState());
            tile.setWorld(world);
            tile.setPos(BlockPos.ORIGIN);
            AENetworkProxy proxy = mock(AENetworkProxy.class);
            when(proxy.getMachineRepresentation()).thenReturn(new ItemStack(Items.BUCKET));
            Field proxyField = TileAENetworkBase.class.getDeclaredField("proxy");
            proxyField.setAccessible(true);
            proxyField.set(tile, proxy);
            IGrid grid = mock(IGrid.class);
            IStorageGrid storage = mock(IStorageGrid.class);
            when(proxy.getGrid()).thenReturn(grid);
            appeng.api.networking.energy.IEnergyGrid energy =
                    mock(appeng.api.networking.energy.IEnergyGrid.class);
            when(energy.extractAEPower(org.mockito.ArgumentMatchers.anyDouble(), any(Actionable.class),
                    any(appeng.api.config.PowerMultiplier.class))).thenReturn(Double.MAX_VALUE);
            when(proxy.getEnergy()).thenReturn(energy);
            when(proxy.getStorage()).thenReturn(storage);
            when(grid.getCache(IStorageGrid.class)).thenReturn(storage);
            IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            when(itemMonitor.getChannel()).thenReturn(channel);
            when(storage.getInventory(channel)).thenReturn(itemMonitor);
        }
    }
}
