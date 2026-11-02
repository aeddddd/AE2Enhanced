package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.fluids.util.AEFluidStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.storage.FluidDescriptor;
import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecyclerConservationTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static TargetManager.TargetRef target(int pos) {
        return new TargetManager.TargetRef(0, new BlockPos(pos, 0, 0), EnumFacing.UP);
    }

    private static TargetAdapter itemAdapter(List<Long> requests) {
        return new TargetAdapter() {
            @Override
            public List<ItemStack> scan(boolean simulate) {
                return java.util.Collections.emptyList();
            }

            @Override
            public ItemStack extract(IAEItemStack requested, boolean simulate) {
                long amount = Math.min(6, requested.getStackSize());
                requests.add(requested.getStackSize());
                return new ItemStack(Items.APPLE, (int) amount);
            }
        };
    }

    private static FluidTargetAdapter fluidAdapter(List<Long> requests) {
        return new FluidTargetAdapter() {
            @Override
            public List<FluidStack> scan(boolean simulate) {
                return java.util.Collections.emptyList();
            }

            @Override
            public FluidStack extract(IAEFluidStack requested, boolean simulate) {
                long amount = Math.min(600, requested.getStackSize());
                requests.add(requested.getStackSize());
                return new FluidStack(FluidRegistry.WATER, (int) amount);
            }
        };
    }

    private static IAEItemStack remainder(IAEItemStack input, long amount) {
        IAEItemStack result = input.copy();
        result.setStackSize(amount);
        return result;
    }

    private static IAEFluidStack remainder(IAEFluidStack input, long amount) {
        IAEFluidStack result = input.copy();
        result.setStackSize(amount);
        return result;
    }

    private static void flush(Object handler, String method) throws Exception {
        Method flush = handler.getClass().getDeclaredMethod(method);
        flush.setAccessible(true);
        flush.invoke(handler);
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object target, String field) throws Exception {
        Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        return (T) declared.get(target);
    }

    @Test
    void itemNetworkPartialFailureAndRecoveryRetainExactRemainder() throws Exception {
        Fixture fixture = new Fixture();
        boolean oldForce = AE2EnhancedConfig.recycler.forceHyperdimensionalStorage;
        AE2EnhancedConfig.recycler.forceHyperdimensionalStorage = false;
        try {
            RecyclerNetworkHandler handler = new RecyclerNetworkHandler(fixture.tile);
            BulkCollector collector = get(handler, "collector");
            collector.add(new ItemStack(Items.APPLE, 10));
            when(fixture.itemMonitor.injectItems(any(), eq(Actionable.MODULATE), any()))
                    .thenAnswer(invocation -> remainder((IAEItemStack) invocation.getArgument(0), 6))
                    .thenThrow(new IllegalStateException("injected fault"))
                    .thenReturn(null);

            flush(handler, "flushCollector");
            assertThat(fixture.itemNotifications).containsExactly(Arrays.asList(4L));
            assertThat(collector.drain()).extracting(IAEItemStack::getStackSize).containsExactly(6L);
            collector.add(new ItemStack(Items.APPLE, 6));

            flush(handler, "flushCollector");
            assertThat(fixture.itemNotifications).containsExactly(Arrays.asList(4L));
            assertThat(collector.drain()).extracting(IAEItemStack::getStackSize).containsExactly(6L);
            collector.add(new ItemStack(Items.APPLE, 6));

            flush(handler, "flushCollector");
            assertThat(fixture.itemNotifications).containsExactly(
                    Arrays.asList(4L), Arrays.asList(6L));
            assertThat(collector.isEmpty()).isTrue();
        } finally {
            AE2EnhancedConfig.recycler.forceHyperdimensionalStorage = oldForce;
        }
    }

    @Test
    void fluidNetworkPartialFailureAndRecoveryRetainExactRemainder() throws Exception {
        Fixture fixture = new Fixture();
        boolean oldForce = AE2EnhancedConfig.recycler.forceHyperdimensionalStorage;
        AE2EnhancedConfig.recycler.forceHyperdimensionalStorage = false;
        try {
            RecyclerFluidNetworkHandler handler = new RecyclerFluidNetworkHandler(fixture.tile);
            FluidBulkCollector collector = get(handler, "collector");
            FluidStack water = new FluidStack(FluidRegistry.WATER, 1000);
            collector.add(water);
            when(fixture.fluidMonitor.injectItems(any(), eq(Actionable.MODULATE), any()))
                    .thenAnswer(invocation -> remainder((IAEFluidStack) invocation.getArgument(0), 600))
                    .thenThrow(new IllegalStateException("injected fault"))
                    .thenReturn(null);

            flush(handler, "flushCollector");
            assertThat(fixture.fluidNotifications).containsExactly(Arrays.asList(400L));
            assertThat(collector.drain()).extracting(IAEFluidStack::getStackSize).containsExactly(600L);
            collector.add(new FluidStack(FluidRegistry.WATER, 600));

            flush(handler, "flushCollector");
            assertThat(fixture.fluidNotifications).containsExactly(Arrays.asList(400L));
            assertThat(collector.drain()).extracting(IAEFluidStack::getStackSize).containsExactly(600L);
            collector.add(new FluidStack(FluidRegistry.WATER, 600));

            flush(handler, "flushCollector");
            assertThat(fixture.fluidNotifications).containsExactly(
                    Arrays.asList(400L), Arrays.asList(600L));
            assertThat(collector.isEmpty()).isTrue();
        } finally {
            AE2EnhancedConfig.recycler.forceHyperdimensionalStorage = oldForce;
        }
    }

    @Test
    void itemCrossTargetExtractionUsesDecreasingRemainingRequest() throws Exception {
        RecyclerNetworkHandler handler = new RecyclerNetworkHandler(mock(TileMENetworkRecycler.class));
        RecyclerIndex index = get(handler, "index");
        Map<TargetManager.TargetRef, TargetAdapter> adapters = get(handler, "adapters");
        ItemDescriptor descriptor = new ItemDescriptor(new ItemStack(Items.APPLE));
        TargetManager.TargetRef first = target(1);
        TargetManager.TargetRef second = target(2);
        index.add(descriptor, first);
        index.add(descriptor, second);
        List<Long> requests = new ArrayList<>();
        TargetAdapter adapter = itemAdapter(requests);
        adapters.put(first, adapter);
        adapters.put(second, adapter);

        IAEItemStack request = AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 10));
        IAEItemStack extracted = handler.extractItems(request, Actionable.MODULATE, null);

        assertThat(extracted.getStackSize()).isEqualTo(10);
        assertThat(requests).containsExactly(10L, 4L);
    }

    @Test
    void fluidCrossTargetExtractionUsesDecreasingRemainingRequest() throws Exception {
        RecyclerFluidNetworkHandler handler = new RecyclerFluidNetworkHandler(mock(TileMENetworkRecycler.class));
        RecyclerFluidIndex index = get(handler, "index");
        Map<TargetManager.TargetRef, FluidTargetAdapter> adapters = get(handler, "adapters");
        FluidDescriptor descriptor = new FluidDescriptor(new FluidStack(FluidRegistry.WATER, 1));
        TargetManager.TargetRef first = target(1);
        TargetManager.TargetRef second = target(2);
        index.add(descriptor, first);
        index.add(descriptor, second);
        List<Long> requests = new ArrayList<>();
        FluidTargetAdapter adapter = fluidAdapter(requests);
        adapters.put(first, adapter);
        adapters.put(second, adapter);

        IAEFluidStack request = AEFluidStack.fromFluidStack(new FluidStack(FluidRegistry.WATER, 1000));
        IAEFluidStack extracted = handler.extractItems(request, Actionable.MODULATE, null);

        assertThat(extracted.getStackSize()).isEqualTo(1000);
        assertThat(requests).containsExactly(1000L, 400L);
    }

    @Test
    void breakingRecyclerDropsItemAndFluidBuffers() throws Exception {
        Fixture fixture = new Fixture();
        RecyclerNetworkHandler items = new RecyclerNetworkHandler(fixture.tile);
        RecyclerFluidNetworkHandler fluids = new RecyclerFluidNetworkHandler(fixture.tile);
        BulkCollector itemCollector = get(items, "collector");
        FluidBulkCollector fluidCollector = get(fluids, "collector");
        itemCollector.add(new ItemStack(Items.APPLE, 7));
        fluidCollector.add(new FluidStack(FluidRegistry.WATER, 800));
        ItemFluidDrop oldDrop = ItemRegistry.FLUID_DROP;
        ItemRegistry.FLUID_DROP = new ItemFluidDrop();
        try {
            items.dropBufferedContents(fixture.world, BlockPos.ORIGIN);
            fluids.dropBufferedContents(fixture.world, BlockPos.ORIGIN);
        } finally {
            ItemRegistry.FLUID_DROP = oldDrop;
        }

        ArgumentCaptor<net.minecraft.entity.Entity> spawned = ArgumentCaptor.forClass(net.minecraft.entity.Entity.class);
        org.mockito.Mockito.verify(fixture.world, org.mockito.Mockito.times(2)).spawnEntity(spawned.capture());
        assertThat(spawned.getAllValues()).allMatch(EntityItem.class::isInstance);
        assertThat(((EntityItem) spawned.getAllValues().get(0)).getItem().getCount()).isEqualTo(7);
        FluidStack recovered = ItemFluidDrop.getFluidStack(((EntityItem) spawned.getAllValues().get(1)).getItem());
        assertThat(recovered.amount).isEqualTo(800);
        assertThat(itemCollector.isEmpty()).isTrue();
        assertThat(fluidCollector.isEmpty()).isTrue();
    }

    private static final class Fixture {
        final TileMENetworkRecycler tile = mock(TileMENetworkRecycler.class);
        final World world = mock(World.class);
        final AENetworkProxy proxy = mock(AENetworkProxy.class);
        final IGrid grid = mock(IGrid.class);
        final IStorageGrid storage = mock(IStorageGrid.class);
        final IMEMonitor<IAEItemStack> itemMonitor = mock(IMEMonitor.class);
        final IMEMonitor<IAEFluidStack> fluidMonitor = mock(IMEMonitor.class);
        final List<List<Long>> itemNotifications = new ArrayList<>();
        final List<List<Long>> fluidNotifications = new ArrayList<>();

        Fixture() throws Exception {
            net.minecraft.world.WorldProvider provider = mock(net.minecraft.world.WorldProvider.class);
            when(provider.getDimension()).thenReturn(0);
            Field worldProvider = World.class.getDeclaredField("provider");
            worldProvider.setAccessible(true);
            worldProvider.set(world, provider);
            when(tile.getProxy()).thenReturn(proxy);
            when(tile.getWorld()).thenReturn(world);
            when(proxy.getGrid()).thenReturn(grid);
            when(grid.getCache(IStorageGrid.class)).thenReturn(storage);
            when(storage.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)))
                    .thenReturn(itemMonitor);
            when(storage.getInventory(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)))
                    .thenReturn(fluidMonitor);
            doAnswer(invocation -> {
                Iterable<IAEItemStack> changes = invocation.getArgument(1);
                List<Long> amounts = new ArrayList<>();
                changes.forEach(change -> amounts.add(change.getStackSize()));
                itemNotifications.add(amounts);
                return null;
            }).when(storage).postAlterationOfStoredItems(
                    eq(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)),
                    any(Iterable.class), any(IActionSource.class));
            doAnswer(invocation -> {
                Iterable<IAEFluidStack> changes = invocation.getArgument(1);
                List<Long> amounts = new ArrayList<>();
                changes.forEach(change -> amounts.add(change.getStackSize()));
                fluidNotifications.add(amounts);
                return null;
            }).when(storage).postAlterationOfStoredItems(
                    eq(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)),
                    any(Iterable.class), any(IActionSource.class));
        }
    }
}
