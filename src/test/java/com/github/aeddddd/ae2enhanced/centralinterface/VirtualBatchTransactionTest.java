package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 提取完成后的每个失败出口都必须回滚同一份实际提取清单。 */
class VirtualBatchTransactionTest {

    private static final BlockPos TARGET_POS = new BlockPos(7, 64, 9);

    private DualityCentralInterface owner;
    private VirtualBatchEngine engine;
    private IVirtualBatchCraftingHandler handler;
    private ICraftingPatternDetails pattern;
    private IGridConnection grid;
    private IMEInventory<IAEItemStack> itemSource;
    private TargetBinding target;
    private InventoryCrafting table;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.startServer();
        ICentralInterfaceHost host = mock(ICentralInterfaceHost.class);
        TileEntity tile = mock(TileEntity.class);
        World world = mock(World.class);
        WorldProvider provider = mock(WorldProvider.class);
        java.lang.reflect.Field providerField = World.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        providerField.set(world, provider);
        when(provider.getDimension()).thenReturn(0);
        when(host.getTileEntity()).thenReturn(tile);
        when(tile.getWorld()).thenReturn(world);
        when(tile.getPos()).thenReturn(BlockPos.ORIGIN);
        when(host.getProxy()).thenReturn(mock(AENetworkProxy.class));
        this.owner = spy(new DualityCentralInterface(host));
        this.engine = new VirtualBatchEngine(this.owner);

        this.grid = mock(IGridConnection.class);
        IStorageGrid storage = mock(IStorageGrid.class);
        IEnergySource energy = mock(IEnergySource.class);
        when(this.grid.storage()).thenReturn(storage);
        when(this.grid.energy()).thenReturn(energy);
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(invocation -> {
            double requested = invocation.getArgument(0);
            return requested == Double.MAX_VALUE ? 1.0e9 : requested;
        });

        this.itemSource = mock(IMEInventory.class);
        when(this.itemSource.extractItems(any(), any(), any())).thenAnswer(invocation -> {
            IAEItemStack request = invocation.getArgument(0);
            long size = request.getStackSize() == Long.MAX_VALUE ? 1 : request.getStackSize();
            return AEItemStack.fromItemStack(new ItemStack(Blocks.STONE, (int) size));
        });
        this.owner.setVirtualItemSource(this.itemSource);

        this.handler = mock(IVirtualBatchCraftingHandler.class);
        when(this.handler.isValidTarget(any(), any())).thenReturn(true);
        when(this.handler.canCraftVirtually(any(), any(), any(), any(), any())).thenReturn(true);
        when(this.handler.getVirtualCost(any(), any(), any(), any(), anyLong(), any())).thenAnswer(invocation -> {
            long count = invocation.getArgument(4);
            return Collections.singletonList(AEItemStack.fromItemStack(
                    new ItemStack(Blocks.STONE, (int) count)));
        });
        when(this.handler.getVirtualCraftingParticles(any(), any())).thenReturn(Collections.emptyList());
        when(this.handler.skipCooldownOnSingleBatch()).thenReturn(true);

        this.pattern = mock(ICraftingPatternDetails.class);
        when(this.pattern.getOutputs()).thenReturn(new IAEItemStack[0]);
        this.target = new TargetBinding(TARGET_POS, 0, "ae2e-test:virtual-transaction");
        this.table = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer playerIn) {
                return false;
            }
        }, 3, 3);
        this.table.setInventorySlotContents(0, new ItemStack(Blocks.STONE, 1));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.stopServer();
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.closeServer();
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.releaseServer();
    }

    @Test
    void virtualCraftExceptionRollsBackExtractedItems() {
        when(this.handler.virtualCraftBatch(any(), any(), any(), any(), anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("craft fault"));

        assertFailureRollsBack();
    }

    @Test
    void productMergeExceptionRollsBackExtractedItems() {
        when(this.handler.virtualCraftBatch(any(), any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(Arrays.asList(new ItemStack(Blocks.STONE, 1), null));

        assertFailureRollsBack();
    }

    @Test
    void particleExceptionRollsBackBeforeProductsBecomeVisible() {
        when(this.handler.virtualCraftBatch(any(), any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(Collections.singletonList(new ItemStack(Blocks.STONE, 1)));
        when(this.handler.getVirtualCraftingParticles(any(), any()))
                .thenThrow(new IllegalStateException("particle fault"));

        assertFailureRollsBack();
    }

    @Test
    void emptyProductsAlsoRollBackExtractedItems() {
        when(this.handler.virtualCraftBatch(any(), any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        assertFailureRollsBack();
    }

    @Test
    void rejectedRollbackRemainderIsPreservedAsPendingProduct() {
        when(this.handler.virtualCraftBatch(any(), any(), any(), any(), anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("craft fault"));
        when(this.itemSource.injectItems(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        long result = execute();

        assertThat(result).isZero();
        List<ItemStack> pending = this.owner.pendingProducts.drainAll();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getCount()).isEqualTo(1);
        assertThat(pending.get(0).getItem()).isEqualTo(net.minecraft.item.Item.getItemFromBlock(Blocks.STONE));
    }

    @Test
    void outputOverflowIsRejectedBeforeAnyCostExtraction() {
        IAEItemStack hugeOutput = AEItemStack.fromItemStack(new ItemStack(Blocks.STONE, 1));
        hugeOutput.setStackSize(Long.MAX_VALUE);
        when(this.pattern.getOutputs()).thenReturn(new IAEItemStack[]{hugeOutput});
        when(this.handler.getVirtualCost(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(Collections.singletonList(AEItemStack.fromItemStack(new ItemStack(Blocks.STONE, 1))));

        long result = this.engine.execute(this.grid, this.pattern, this.table, this.target,
                this.handler, Long.MAX_VALUE);

        assertThat(result).isZero();
        verify(this.itemSource, never()).extractItems(any(), eq(Actionable.MODULATE), any());
        assertThat(this.owner.pendingProducts.isEmpty()).isTrue();
    }

    @Test
    void unavailableCostIsRejectedBeforeAnyCostExtraction() {
        when(this.handler.getVirtualCost(any(), any(), any(), any(), anyLong(), any())).thenReturn(null);

        long result = execute();

        assertThat(result).isZero();
        verify(this.itemSource, never()).extractItems(any(), eq(Actionable.MODULATE), any());
        assertThat(this.owner.pendingProducts.isEmpty()).isTrue();
    }

    private void assertFailureRollsBack() {
        long result = execute();

        assertThat(result).isZero();
        verify(this.itemSource).injectItems(any(IAEItemStack.class),
                any(Actionable.class), any());
        assertThat(this.owner.pendingProducts.isEmpty()).isTrue();
    }

    private long execute() {
        return this.engine.execute(this.grid, this.pattern, this.table, this.target, this.handler, 2);
    }
}
