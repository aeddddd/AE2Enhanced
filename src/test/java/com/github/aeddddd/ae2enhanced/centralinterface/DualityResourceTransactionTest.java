package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.item.AEItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/** 网络部分注入与拆方块的资源守恒测试，直接执行 Duality/VirtualBatchEngine 生产路径。 */
class DualityResourceTransactionTest {

    private DualityCentralInterface owner;
    private IGridConnection grid;
    private World world;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        if (ItemRegistry.FLUID_DROP == null) {
            ItemRegistry.FLUID_DROP = new ItemFluidDrop();
        }
    }

    private static void assertStack(ItemStack stack, Item item, int count) {
        assertThat(stack.getItem()).isEqualTo(item);
        assertThat(stack.getCount()).isEqualTo(count);
    }

    @BeforeEach
    void setUp() {
        ICentralInterfaceHost host = mock(ICentralInterfaceHost.class);
        TileEntity tile = mock(TileEntity.class);
        this.world = mock(World.class);
        when(host.getTileEntity()).thenReturn(tile);
        when(tile.getWorld()).thenReturn(this.world);
        when(tile.getPos()).thenReturn(BlockPos.ORIGIN);
        when(host.getProxy()).thenReturn(mock(AENetworkProxy.class));
        when(this.world.spawnEntity(any())).thenReturn(true);
        this.owner = spy(new DualityCentralInterface(host));
        this.grid = mock(IGridConnection.class);
    }

    @Test
    void batchInjectionKeepsOnlyPerStackRemainderAfterPartialSuccessAndException() {
        stubNetworkInjection();
        List<ItemStack> input = Arrays.asList(
                new ItemStack(Blocks.STONE, 3),
                new ItemStack(Blocks.DIRT, 4),
                new ItemStack(Blocks.GRASS, 5));

        List<ItemStack> remaining = this.owner.injectItemsToNetwork(this.grid, this.world, input);

        assertThat(remaining).hasSize(2);
        assertStack(remaining.get(0), Item.getItemFromBlock(Blocks.STONE), 1);
        assertStack(remaining.get(1), Item.getItemFromBlock(Blocks.GRASS), 5);
    }

    @Test
    void virtualTickStashesOnlyActualRemainderInsteadOfWholeDrainedBatch() {
        stubNetworkInjection();
        this.owner.pendingProducts.addAll(Arrays.asList(
                new ItemStack(Blocks.STONE, 3),
                new ItemStack(Blocks.DIRT, 4),
                new ItemStack(Blocks.GRASS, 5)));
        doNothing().when(this.owner).stashItemsToStorage(any(), anyList());
        VirtualBatchEngine engine = new VirtualBatchEngine(this.owner);

        assertThat(engine.tick(this.grid)).isTrue();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(this.owner).stashItemsToStorage(any(), captor.capture());
        List<ItemStack> stashed = captor.getValue();
        assertThat(stashed).hasSize(2);
        assertStack(stashed.get(0), Item.getItemFromBlock(Blocks.STONE), 1);
        assertStack(stashed.get(1), Item.getItemFromBlock(Blocks.GRASS), 5);
        assertThat(this.owner.pendingProducts.isEmpty()).isTrue();
    }

    @Test
    void clearContentsDropsPendingProductsAndBufferedFluidWithoutActiveGrid() {
        this.owner.pendingProducts.addAll(Collections.singletonList(new ItemStack(Items.DIAMOND, 2)));
        this.owner.fluidBuffer().add(new FluidStack(FluidRegistry.WATER, 750));
        when(this.grid.isActive()).thenReturn(false);
        doAnswer(invocation -> this.grid).when(this.owner).gridConnection();
        List<ItemStack> drops = new ArrayList<>();
        doAnswer(invocation -> {
            List<ItemStack> batch = invocation.getArgument(2);
            for (ItemStack stack : batch) {
                drops.add(stack.copy());
            }
            return null;
        }).when(this.owner).dropItems(any(), any(), anyList());

        this.owner.clearContents();

        assertThat(this.owner.pendingProducts.isEmpty()).isTrue();
        assertThat(this.owner.fluidBuffer().isEmpty()).isTrue();
        assertThat(drops.stream().filter(s -> s.getItem() == Items.DIAMOND).mapToInt(ItemStack::getCount).sum())
                .isEqualTo(2);
        assertThat(drops.stream().filter(ItemFluidDrop::isFluidDrop)
                .mapToInt(s -> ItemFluidDrop.getFluidStack(s).amount).sum()).isEqualTo(750);
    }

    private void stubNetworkInjection() {
        try {
            doAnswer(invocation -> {
                IAEItemStack stack = invocation.getArgument(1);
                Item item = stack.getItem();
                if (item == Item.getItemFromBlock(Blocks.STONE)) {
                    return AEItemStack.fromItemStack(new ItemStack(Blocks.STONE, 1));
                }
                if (item == Item.getItemFromBlock(Blocks.GRASS)) {
                    throw new IllegalStateException("network fault after earlier stacks committed");
                }
                return null;
            }).when(this.owner).insertItemToNetwork(any(), any());
        } catch (appeng.me.GridAccessException e) {
            throw new AssertionError(e);
        }
    }
}
