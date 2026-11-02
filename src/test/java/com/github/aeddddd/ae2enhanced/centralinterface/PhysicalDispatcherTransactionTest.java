package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.AENetworkProxy;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PUSHING 与已提交中止使用不同的材料返还所有权，防止 CPU 与 Central 双重返还。 */
class PhysicalDispatcherTransactionTest {

    private static final BlockPos TARGET_POS = new BlockPos(3, 64, 5);
    private static final String BLOCK_ID = "ae2e-test:transaction-machine";

    private DualityCentralInterface owner;
    private PhysicalDispatcher dispatcher;
    private IRemoteHandler handler;
    private ICraftingPatternDetails pattern;
    private IGridConnection grid;
    private World world;
    private TargetBinding target;

    @BeforeAll
    static void boot() throws Exception {
        AE2TestBootstrap.boot();
        HandlerRegistryTest.stubLoader();
        if (ItemRegistry.FLUID_DROP == null) {
            ItemRegistry.FLUID_DROP = new ItemFluidDrop();
        }
    }

    private static InventoryCrafting crafting(ItemStack stack) {
        InventoryCrafting table = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer playerIn) {
                return false;
            }
        }, 3, 3);
        table.setInventorySlotContents(0, stack);
        return table;
    }

    @BeforeEach
    void setUp() throws Exception {
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.startServer();
        ICentralInterfaceHost host = mock(ICentralInterfaceHost.class);
        TileEntity tile = mock(TileEntity.class);
        this.world = mock(World.class);
        WorldProvider provider = mock(WorldProvider.class);
        java.lang.reflect.Field providerField = World.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        providerField.set(this.world, provider);
        when(provider.getDimension()).thenReturn(0);
        when(this.world.isBlockLoaded(TARGET_POS)).thenReturn(true);
        when(this.world.getTotalWorldTime()).thenReturn(100L);
        when(host.getTileEntity()).thenReturn(tile);
        when(tile.getWorld()).thenReturn(this.world);
        when(tile.getPos()).thenReturn(BlockPos.ORIGIN);
        when(host.getProxy()).thenReturn(mock(AENetworkProxy.class));

        this.owner = spy(new DualityCentralInterface(host));
        this.dispatcher = new PhysicalDispatcher(this.owner);
        this.grid = mock(IGridConnection.class);
        doReturn(this.grid).when(this.owner).gridConnection();
        doReturn(Collections.emptyList()).when(this.owner)
                .injectItemsToNetwork(any(), any(), anyList());

        this.handler = mock(IRemoteHandler.class);
        when(this.handler.canHandle(BLOCK_ID)).thenReturn(true);
        when(this.handler.isValidTarget(any(), eq(TARGET_POS))).thenReturn(true);
        when(this.handler.canStart(any(), eq(TARGET_POS), any(), any())).thenReturn(true);
        when(this.handler.pushMaterials(any(), eq(TARGET_POS), any(), any(), any())).thenReturn(true);
        when(this.handler.startProcess(any(), eq(TARGET_POS), any(), any())).thenReturn(true);
        when(this.handler.revertMaterials(any(), eq(TARGET_POS), any(), any()))
                .thenReturn(Collections.singletonList(new ItemStack(Blocks.STONE, 1)));
        HandlerRegistry.instance().register(this.handler);
        this.pattern = mock(ICraftingPatternDetails.class);
        when(this.pattern.getOutputs()).thenReturn(new IAEItemStack[0]);
        this.target = new TargetBinding(TARGET_POS, 0, BLOCK_ID);
    }

    @AfterEach
    void tearDown() {
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.stopServer();
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.closeServer();
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.releaseServer();
    }

    @Test
    void canStartFailureRestoresTableAndDoesNotReturnMachineItemsToNetwork() {
        when(this.handler.canStart(any(), eq(TARGET_POS), any(), any())).thenReturn(false);

        assertPushingFailureConserved();
    }

    @Test
    void partialPushFailureRevertsMachineButDoesNotDuplicateIntoNetwork() {
        when(this.handler.pushMaterials(any(), eq(TARGET_POS), any(), any(), any())).thenReturn(false);

        assertPushingFailureConserved();
        verify(this.handler).revertMaterials(any(), eq(TARGET_POS), any(), any());
    }

    @Test
    void startProcessFailureDoesNotInjectRevertedItems() {
        when(this.handler.startProcess(any(), eq(TARGET_POS), any(), any())).thenReturn(false);

        assertPushingFailureConserved();
    }

    @Test
    void handlerExceptionDoesNotInjectRevertedItems() {
        when(this.handler.pushMaterials(any(), eq(TARGET_POS), any(), any(), any()))
                .thenThrow(new IllegalStateException("fault after partial push"));

        assertPushingFailureConserved();
    }

    @Test
    void fluidFakeItemIsRestoredToOriginalTableWhenRealPushFails() {
        InventoryCrafting original = crafting(ItemFluidDrop.createStack(
                new FluidStack(FluidRegistry.WATER, 1000)));
        doReturn(original).when(this.owner).copyInventoryCrafting(original);
        when(this.world.getTileEntity(TARGET_POS)).thenReturn(mock(TileEntity.class));

        boolean pushed = this.dispatcher.dispatch(this.grid, this.pattern, original, this.target, this.handler);

        assertThat(pushed).isFalse();
        assertThat(original.getStackInSlot(0).getCount()).isEqualTo(1000);
        assertThat(ItemFluidDrop.getFluidStack(original.getStackInSlot(0)).amount).isEqualTo(1000);
        verifyNoNetworkReturn();
    }

    @Test
    void committedAbortStillReturnsRevertedMaterialsToNetwork() throws Exception {
        TargetSession session = this.owner.getOrCreateSession(this.target);
        session.beginPush(new ArrayList<>());
        session.commitPush(new IAEItemStack[0],
                Collections.singletonList(new ItemStack(Blocks.STONE, 1)), Collections.emptyList(), 1L);
        Method revert = PhysicalDispatcher.class.getDeclaredMethod(
                "revertSession", TargetSession.class, String.class, IActionSource.class);
        revert.setAccessible(true);

        revert.invoke(this.dispatcher, session, "COMMITTED_ABORT", null);

        verify(this.owner).injectItemsToNetwork(eq(this.grid), eq(this.world), anyList());
        verify(this.owner, never()).stashItemsToStorage(any(), anyList());
    }

    @Test
    void recoveryKeepsStableIdAndOnlyActualRemainder() {
        TargetSession session = this.owner.getOrCreateSession(this.target);
        session.beginPush(new ArrayList<>());
        session.commitPush(new IAEItemStack[0], Collections.emptyList(), Collections.emptyList(), 1L);
        java.util.UUID id = session.getSessionId();
        this.owner.deferPhysicalSession(session);
        assertThat(this.owner.inflightSessions).containsKey(id);
        when(this.handler.collectProducts(any(), eq(TARGET_POS), any(), anyList(), any(), any()))
                .thenReturn(Collections.singletonList(new ItemStack(Items.DIAMOND, 10)))
                .thenReturn(Collections.emptyList());
        when(this.handler.revertMaterials(any(), eq(TARGET_POS), any(), any())).thenReturn(Collections.emptyList());
        org.mockito.Mockito.doAnswer(invocation -> Collections.singletonList(new ItemStack(Items.DIAMOND, 3)))
                .when(this.owner).injectItemsToNetwork(any(), any(), anyList());

        assertThat(this.dispatcher.recoverPending(this.grid, this.world)).isTrue();
        assertThat(this.owner.inflightSessions).doesNotContainKey(id);
        assertThat(this.owner.recoveryOutbox.get(id).items.get(0).getCount()).isEqualTo(3);
        assertThat(this.owner.getOrCreateSession(this.target).isIdle()).isTrue();

        assertThat(this.dispatcher.recoverPending(this.grid, this.world)).isFalse();
        verify(this.owner, org.mockito.Mockito.times(1)).injectItemsToNetwork(any(), any(), anyList());

        org.mockito.Mockito.doAnswer(invocation -> Collections.emptyList())
                .when(this.owner).injectItemsToNetwork(any(), any(), anyList());
        this.dispatcher.flushRecoveryOutbox(this.grid, this.world);
        assertThat(this.owner.recoveryOutbox.isEmpty()).isTrue();
    }

    @Test
    void failedOrUnloadedRecoveryKeepsCommittedRecord() {
        TargetSession session = this.owner.getOrCreateSession(this.target);
        session.beginPush(new ArrayList<>());
        session.commitPush(new IAEItemStack[0], Collections.emptyList(), Collections.emptyList(), 1L);
        java.util.UUID id = session.getSessionId();
        this.owner.deferPhysicalSession(session);
        when(this.handler.collectProducts(any(), eq(TARGET_POS), any(), anyList(), any(), any()))
                .thenThrow(new IllegalStateException("machine unavailable"));

        assertThat(this.dispatcher.recoverPending(this.grid, this.world)).isFalse();
        assertThat(this.owner.inflightSessions).containsKey(id);
        assertThat(this.owner.recoveryOutbox.isEmpty()).isTrue();
        assertThat(this.owner.getOrCreateSession(this.target).isIdle()).isTrue();

        when(this.world.isBlockLoaded(TARGET_POS)).thenReturn(false);
        assertThat(this.dispatcher.recoverPending(this.grid, this.world)).isFalse();
        assertThat(this.owner.inflightSessions).containsKey(id);
    }

    @Test
    void normalCompletionDeletesCommittedRecord() {
        TargetSession session = this.owner.getOrCreateSession(this.target);
        session.beginPush(new ArrayList<>());
        session.commitPush(new IAEItemStack[0], Collections.emptyList(), Collections.emptyList(), 1L);
        java.util.UUID id = session.getSessionId();
        assertThat(this.owner.inflightSessions).containsKey(id);

        session.reset();

        assertThat(this.owner.inflightSessions).doesNotContainKey(id);
    }

    private void assertPushingFailureConserved() {
        InventoryCrafting original = crafting(new ItemStack(Blocks.STONE, 2));

        boolean pushed = this.dispatcher.dispatch(this.grid, this.pattern, original, this.target, this.handler);

        assertThat(pushed).isFalse();
        assertThat(original.getStackInSlot(0).getCount()).isEqualTo(2);
        assertThat(this.owner.getOrCreateSession(this.target).isIdle()).isTrue();
        verifyNoNetworkReturn();
    }

    private void verifyNoNetworkReturn() {
        verify(this.owner, never()).injectItemsToNetwork(any(), any(), anyList());
        verify(this.owner, never()).stashItemsToStorage(any(), anyList());
    }
}
