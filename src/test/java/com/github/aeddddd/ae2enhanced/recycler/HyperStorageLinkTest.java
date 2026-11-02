package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.me.helpers.AENetworkProxy;
import com.github.aeddddd.ae2enhanced.storage.FluidStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class HyperStorageLinkTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static IGrid grid() {
        return mock(IGrid.class);
    }

    private static appeng.api.util.IReadOnlyCollection<IGridNode> nodes(IGridNode node) {
        appeng.api.util.IReadOnlyCollection<IGridNode> nodes = mock(appeng.api.util.IReadOnlyCollection.class);
        when(nodes.iterator()).thenAnswer(invocation -> Collections.singleton(node).iterator());
        return nodes;
    }

    private static IGridNode node(TileHyperdimensionalController controller) {
        IGridNode node = mock(IGridNode.class);
        when(node.getMachine()).thenReturn(controller);
        return node;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void itemCacheRejectsDisassemblyGridChangeAndAdapterGenerationChange() throws Exception {
        AENetworkProxy proxy = mock(AENetworkProxy.class);
        IGrid firstGrid = grid();
        IGrid secondGrid = grid();
        TileHyperdimensionalController controller = mock(TileHyperdimensionalController.class);
        ItemStorageAdapter firstAdapter = mock(ItemStorageAdapter.class);
        ItemStorageAdapter secondAdapter = mock(ItemStorageAdapter.class);
        IGridNode firstNode = node(controller);
        IGridNode secondNode = node(controller);
        appeng.api.util.IReadOnlyCollection<IGridNode> firstNodes = nodes(firstNode);
        appeng.api.util.IReadOnlyCollection<IGridNode> secondNodes = nodes(secondNode);
        when(firstGrid.getNodes()).thenReturn(firstNodes);
        when(secondGrid.getNodes()).thenReturn(secondNodes);
        when(proxy.getGrid()).thenReturn(firstGrid);
        when(controller.isFormed()).thenReturn(true);
        when(controller.getItemAdapter()).thenReturn(firstAdapter);
        HyperStorageLink link = new HyperStorageLink();

        assertThat(link.find(proxy, 100)).isSameAs(firstAdapter);
        assertThat(link.find(proxy, 101)).isSameAs(firstAdapter);

        when(controller.isFormed()).thenReturn(false);
        assertThat(link.find(proxy, 102)).isNull();

        when(controller.isFormed()).thenReturn(true);
        when(controller.getItemAdapter()).thenReturn(secondAdapter);
        assertThat(link.find(proxy, 120)).isSameAs(secondAdapter);

        when(proxy.getGrid()).thenReturn(secondGrid);
        when(controller.getItemAdapter()).thenReturn(firstAdapter);
        assertThat(link.find(proxy, 121)).isNull();
        assertThat(link.find(proxy, 140)).isSameAs(firstAdapter);
    }

    @Test
    void fluidCacheRejectsDisassemblyGridChangeAndAdapterGenerationChange() throws Exception {
        AENetworkProxy proxy = mock(AENetworkProxy.class);
        IGrid firstGrid = grid();
        IGrid secondGrid = grid();
        TileHyperdimensionalController controller = mock(TileHyperdimensionalController.class);
        FluidStorageAdapter firstAdapter = mock(FluidStorageAdapter.class);
        FluidStorageAdapter secondAdapter = mock(FluidStorageAdapter.class);
        appeng.api.util.IReadOnlyCollection<IGridNode> firstNodes = nodes(node(controller));
        appeng.api.util.IReadOnlyCollection<IGridNode> secondNodes = nodes(node(controller));
        when(firstGrid.getNodes()).thenReturn(firstNodes);
        when(secondGrid.getNodes()).thenReturn(secondNodes);
        when(proxy.getGrid()).thenReturn(firstGrid);
        when(controller.isFormed()).thenReturn(true);
        when(controller.getFluidAdapter()).thenReturn(firstAdapter);
        HyperStorageLinkFluid link = new HyperStorageLinkFluid();

        assertThat(link.find(proxy, 100)).isSameAs(firstAdapter);
        assertThat(link.find(proxy, 101)).isSameAs(firstAdapter);

        when(controller.isFormed()).thenReturn(false);
        assertThat(link.find(proxy, 102)).isNull();

        when(controller.isFormed()).thenReturn(true);
        when(controller.getFluidAdapter()).thenReturn(secondAdapter);
        assertThat(link.find(proxy, 120)).isSameAs(secondAdapter);

        when(proxy.getGrid()).thenReturn(secondGrid);
        when(controller.getFluidAdapter()).thenReturn(firstAdapter);
        assertThat(link.find(proxy, 121)).isNull();
        assertThat(link.find(proxy, 140)).isSameAs(firstAdapter);
    }

    @Test
    void inactiveRecyclerCannotUseCachedDirectInjection() throws Exception {
        TileMENetworkRecycler tile = mock(TileMENetworkRecycler.class);
        when(tile.isActive()).thenReturn(false);
        RecyclerNetworkHandler itemHandler = new RecyclerNetworkHandler(tile);
        RecyclerFluidNetworkHandler fluidHandler = new RecyclerFluidNetworkHandler(tile);
        HyperStorageLink itemLink = mock(HyperStorageLink.class);
        HyperStorageLinkFluid fluidLink = mock(HyperStorageLinkFluid.class);
        set(itemHandler, "hyperStorageLink", itemLink);
        set(fluidHandler, "hyperStorageLink", fluidLink);
        ItemStack items = new ItemStack(Items.APPLE, 5);
        FluidStack fluid = new FluidStack(FluidRegistry.WATER, 500);

        assertThat(itemHandler.tryInjectMachineOutput(items)).isSameAs(items);
        assertThat(fluidHandler.tryInjectMachineOutput(fluid)).isSameAs(fluid);
        verifyZeroInteractions(itemLink);
        verifyZeroInteractions(fluidLink);
    }
}
