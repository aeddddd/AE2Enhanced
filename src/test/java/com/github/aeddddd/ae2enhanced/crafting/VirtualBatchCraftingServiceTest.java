package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.DualityCentralInterface;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialCpuAccess;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VirtualBatchCraftingServiceTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @SuppressWarnings("unchecked")
    private static IItemList<IAEItemStack> itemList() {
        return mock(IItemList.class);
    }

    @SuppressWarnings("unchecked")
    private static IMEInventory<IAEItemStack> inventory() {
        return mock(IMEInventory.class);
    }

    private static CraftingCpuAdapter cpu(ISpecialCpuAccess access, IItemList<IAEItemStack> waitingFor,
            long remaining, Object progress) {
        if (access == null) {
            access = mock(ISpecialCpuAccess.class);
        }
        when(access.ae2e$waitingFor()).thenReturn(waitingFor);
        when(access.ae2e$tasks()).thenReturn(Collections.singletonMap(null, progress));
        when(access.ae2e$taskValue(progress)).thenReturn(remaining);
        return new CraftingCpuAdapter(access, new CraftingCpuExecutionContext());
    }

    @Test
    void nonCentralPushIsTransparentAndCallsOriginalOnce() {
        CraftingCpuAdapter cpu = cpu(null, null, 0L, null);
        VirtualBatchCraftingService.PushCall original = mock(VirtualBatchCraftingService.PushCall.class);
        when(original.call()).thenReturn(true);

        assertThat(VirtualBatchCraftingService.dispatch(cpu, null, null, original)).isTrue();

        verify(original).call();
    }

    @Test
    void singleBatchKeepsTaskAndWaitingForUntouched() {
        DualityCentralInterface duality = mock(DualityCentralInterface.class);
        when(duality.getLastVirtualBatchSize()).thenReturn(1L);
        ISpecialCpuAccess access = mock(ISpecialCpuAccess.class);
        IItemList<IAEItemStack> waitingFor = itemList();
        Object progress = new Object();
        CraftingCpuAdapter cpu = cpu(access, waitingFor, 1L, progress);
        IMEInventory<IAEItemStack> inventory = inventory();
        when(access.ae2e$inventory()).thenReturn(inventory);
        when(access.ae2e$taskValue(progress)).thenReturn(1L);
        VirtualBatchCraftingService.PushCall original = mock(VirtualBatchCraftingService.PushCall.class);
        when(original.call()).thenReturn(true);

        assertThat(VirtualBatchCraftingService.dispatch(cpu, duality, null, original)).isTrue();

        InOrder order = inOrder(duality, original);
        order.verify(duality).setNextVirtualBatchLimit(1L);
        order.verify(duality).setVirtualItemSource(inventory);
        order.verify(original).call();
        order.verify(duality).setVirtualItemSource(null);
        order.verify(duality).getLastVirtualBatchSize();
        verify(original).call();
        verify(access, never()).ae2e$setTaskValue(any(), org.mockito.ArgumentMatchers.anyLong());
        verify(waitingFor, never()).add(any());
        verify(access, never()).ae2e$updateRemainingItemCount(any());
    }

    @Test
    void multiBatchAddsOnlyVerifiedPatternOutputsAndLeavesRemainingCountToReturnFlow() {
        DualityCentralInterface duality = mock(DualityCentralInterface.class);
        when(duality.getLastVirtualBatchSize()).thenReturn(3L);
        ISpecialCpuAccess access = mock(ISpecialCpuAccess.class);
        IItemList<IAEItemStack> waitingFor = itemList();
        Object progress = new Object();
        CraftingCpuAdapter cpu = cpu(access, waitingFor, 5L, progress);
        IMEInventory<IAEItemStack> inventory = inventory();
        when(access.ae2e$inventory()).thenReturn(inventory);
        AtomicLong remaining = new AtomicLong(5L);
        when(access.ae2e$taskValue(progress)).thenAnswer(invocation -> remaining.get());
        IAEItemStack output = AEItemStack.fromItemStack(new ItemStack(Blocks.STONE, 2));
        appeng.api.networking.crafting.ICraftingPatternDetails details =
                mock(appeng.api.networking.crafting.ICraftingPatternDetails.class);
        when(details.getCondensedOutputs()).thenReturn(new IAEItemStack[]{output});
        when(access.ae2e$tasks()).thenReturn(Collections.singletonMap(details, progress));
        VirtualBatchCraftingService.PushCall original = mock(VirtualBatchCraftingService.PushCall.class);
        when(original.call()).thenReturn(true);

        assertThat(VirtualBatchCraftingService.dispatch(cpu, duality, details, original)).isTrue();

        verify(access).ae2e$setTaskValue(progress, 3L);
        ArgumentCaptor<IAEItemStack> expected = ArgumentCaptor.forClass(IAEItemStack.class);
        verify(waitingFor).add(expected.capture());
        assertThat(expected.getValue().getStackSize()).isEqualTo(4L);
        assertThat(expected.getValue().isSameType(output)).isTrue();
        verify(access).ae2e$postCraftingStatusChange(any(IAEItemStack.class));
        verify(access, never()).ae2e$updateRemainingItemCount(any());
        verify(original).call();
    }

    @Test
    void failedPushResetsItemSourceWithoutAccounting() {
        DualityCentralInterface duality = mock(DualityCentralInterface.class);
        ISpecialCpuAccess access = mock(ISpecialCpuAccess.class);
        CraftingCpuAdapter cpu = cpu(access, itemList(), 4L, new Object());
        when(access.ae2e$inventory()).thenReturn(inventory());
        when(access.ae2e$taskValue(any())).thenReturn(4L);
        VirtualBatchCraftingService.PushCall original = mock(VirtualBatchCraftingService.PushCall.class);
        when(original.call()).thenReturn(false);

        assertThat(VirtualBatchCraftingService.dispatch(cpu, duality, null, original)).isFalse();

        verify(duality).setVirtualItemSource(null);
        verify(duality, never()).getLastVirtualBatchSize();
        verify(access, never()).ae2e$updateRemainingItemCount(any());
        verify(original).call();
    }

    @Test
    void originalExceptionStillResetsItemSourceAndIsNotSwallowed() {
        DualityCentralInterface duality = mock(DualityCentralInterface.class);
        ISpecialCpuAccess access = mock(ISpecialCpuAccess.class);
        CraftingCpuAdapter cpu = cpu(access, itemList(), 4L, new Object());
        when(access.ae2e$inventory()).thenReturn(inventory());
        when(access.ae2e$taskValue(any())).thenReturn(4L);
        IllegalStateException failure = new IllegalStateException("push failed");

        assertThatThrownBy(() -> VirtualBatchCraftingService.dispatch(cpu, duality, null, () -> {
            throw failure;
        })).isSameAs(failure);

        verify(duality).setVirtualItemSource(null);
        verify(duality, never()).getLastVirtualBatchSize();
        verify(access, never()).ae2e$updateRemainingItemCount(any());
    }
}
