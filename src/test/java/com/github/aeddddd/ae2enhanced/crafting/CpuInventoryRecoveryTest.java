package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.WorldCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CpuInventoryRecoveryTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static CraftingCPUCluster cpuWith(net.minecraft.item.Item item, long count) {
        CraftingCPUCluster cpu = new CraftingCPUCluster(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 0));
        IAEItemStack stack = AEItemStack.fromItemStack(new ItemStack(item));
        stack.setStackSize(count);
        cpu.addStorage(stack);
        return cpu;
    }

    private static long available(IMEInventory<IAEItemStack> inventory, net.minecraft.item.Item item) {
        IAEItemStack request = AEItemStack.fromItemStack(new ItemStack(item));
        request.setStackSize(Long.MAX_VALUE);
        IAEItemStack result = inventory.extractItems(request, Actionable.SIMULATE, null);
        return result == null ? 0 : result.getStackSize();
    }

    @Test
    void cancelledCpuRemainderReturnsToNetwork() {
        CraftingCPUCluster cpu = cpuWith(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 12);
        IMEMonitor<IAEItemStack> network = mock(IMEMonitor.class);
        when(network.injectItems(any(), any(), any())).thenReturn(null);

        assertThat(CpuInventoryRecovery.drain(cpu.getInventory(), network, null, null)).isTrue();
        assertThat(available(cpu.getInventory(), net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE))).isZero();
    }

    @Test
    void cancellationCompletesBeforeCpuRemainderRecovery() {
        CraftingCPUCluster cpu = cpuWith(Items.EMERALD, 9);
        List<String> events = new ArrayList<>();
        IMEMonitor<IAEItemStack> network = mock(IMEMonitor.class);
        when(network.injectItems(any(), any(), any())).thenAnswer(invocation -> {
            events.add("recover");
            return null;
        });

        assertThat(CpuInventoryRecovery.cancelAndDrain(() -> events.add("cancel"),
                cpu.getInventory(), network, null, null)).isTrue();
        assertThat(events).containsExactly("cancel", "recover");
        assertThat(available(cpu.getInventory(), Items.EMERALD)).isZero();
    }

    @Test
    void rejectedCpuRemainderDropsAndUnavailableDestinationsRetainIt() {
        CraftingCPUCluster droppedCpu = cpuWith(Items.DIAMOND, 7);
        IMEMonitor<IAEItemStack> rejecting = mock(IMEMonitor.class);
        when(rejecting.injectItems(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        List<ItemStack> drops = new ArrayList<>();

        assertThat(CpuInventoryRecovery.drain(droppedCpu.getInventory(), rejecting, null, stack -> {
            drops.add(stack);
            return true;
        })).isTrue();
        assertThat(drops).hasSize(1);
        assertThat(drops.get(0).getItem()).isEqualTo(Items.DIAMOND);
        assertThat(drops.get(0).getCount()).isEqualTo(7);

        CraftingCPUCluster retainedCpu = cpuWith(Items.COAL, 5);
        assertThat(CpuInventoryRecovery.drain(retainedCpu.getInventory(), null, null, null)).isFalse();
        assertThat(available(retainedCpu.getInventory(), Items.COAL)).isEqualTo(5);
    }
}
