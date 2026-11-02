package com.github.aeddddd.ae2enhanced.crafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.ContainerNull;
import com.github.aeddddd.ae2enhanced.mixin.bridge.IMeInventoryVersionAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialCpuAccess;
import com.github.aeddddd.ae2enhanced.specialcrafting.RoundQuotaScheduler;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CraftingCpuPipelineTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static ICraftingPatternDetails pattern(ItemStack stack) {
        ICraftingPatternDetails details = mock(ICraftingPatternDetails.class);
        when(details.getPattern()).thenReturn(stack);
        return details;
    }

    @Test
    void executeStateIsPerCpuAndClearedAtExplicitBoundaries() {
        CraftingCpuExecutionContext first = new CraftingCpuExecutionContext();
        CraftingCpuExecutionContext second = new CraftingCpuExecutionContext();
        first.remainingSnapshot(Collections.singletonMap(pattern(new ItemStack(Blocks.STONE)), 3L));
        first.vetoedThisTick(Collections.singleton(pattern(new ItemStack(Blocks.STONE))));
        first.specialMarked(true);

        first.beginExecute();
        assertThat(first.remainingSnapshot()).isNull();
        assertThat(first.vetoedThisTick()).isNull();
        assertThat(first.specialMarked()).isTrue();
        assertThat(second.specialMarked()).isFalse();
        first.specialMarked(false);
        assertThat(first.specialTotals()).isNull();
    }

    @Test
    void canCraftFailureCacheUsesInjectionVersionWithoutWeaving() {
        ISpecialCpuAccess access = mock(ISpecialCpuAccess.class);
        IMEInventory<IAEItemStack> inventory = mock(IMEInventory.class,
                org.mockito.Mockito.withSettings().extraInterfaces(IMeInventoryVersionAccess.class));
        when(access.ae2e$inventory()).thenReturn(inventory);
        when(((IMeInventoryVersionAccess) inventory).ae2e$getInjectVersion()).thenReturn(1L, 1L, 2L);
        CraftingCpuAdapter cpu = new CraftingCpuAdapter(access, new CraftingCpuExecutionContext());
        ICraftingPatternDetails details = pattern(new ItemStack(Blocks.STONE));
        AtomicInteger originalCalls = new AtomicInteger();

        assertThat(CanCraftCacheService.canCraft(cpu, details, () -> {
            originalCalls.incrementAndGet();
            return false;
        })).isFalse();
        assertThat(CanCraftCacheService.canCraft(cpu, details, () -> {
            originalCalls.incrementAndGet();
            return true;
        })).isFalse();
        assertThat(originalCalls).hasValue(1);
        assertThat(CanCraftCacheService.canCraft(cpu, details, () -> {
            originalCalls.incrementAndGet();
            return true;
        })).isTrue();
        assertThat(originalCalls).hasValue(2);
    }

    @Test
    void resizeKeepsCraftableTableIdentityAndExpandsProcessingTable() {
        InventoryCrafting original = new InventoryCrafting(new ContainerNull(), 4, 4);
        ICraftingPatternDetails details = pattern(new ItemStack(Blocks.STONE));
        when(details.isCraftable()).thenReturn(true);
        assertThat(CraftingBufferResizeService.resize(original, new ContainerNull(), details)).isSameAs(original);

        when(details.isCraftable()).thenReturn(false);
        appeng.api.storage.data.IAEItemStack[] inputs = new appeng.api.storage.data.IAEItemStack[17];
        when(details.getInputs()).thenReturn(inputs);
        InventoryCrafting resized = CraftingBufferResizeService.resize(original, new ContainerNull(), details);
        assertThat(resized.getSizeInventory()).isEqualTo(25);
    }

    @Test
    void specialNbtRoundTripRestoresTotalsInContext() {
        ICraftingPatternDetails details = pattern(new ItemStack(Blocks.STONE));
        Map<ICraftingPatternDetails, Object> tasks = new LinkedHashMap<>();
        Object progress = new Object();
        tasks.put(details, progress);
        ISpecialCpuAccess sourceAccess = mock(ISpecialCpuAccess.class);
        when(sourceAccess.ae2e$tasks()).thenReturn(tasks);
        CraftingCpuExecutionContext sourceContext = new CraftingCpuExecutionContext();
        sourceContext.specialMarked(true);
        Map<ICraftingPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(details, 7L);
        RoundQuotaScheduler.snapshot(sourceContext, totals);
        NBTTagCompound nbt = new NBTTagCompound();
        SpecialCraftingCpuService.writeNbt(new CraftingCpuAdapter(sourceAccess, sourceContext), nbt);

        ISpecialCpuAccess restoredAccess = mock(ISpecialCpuAccess.class);
        when(restoredAccess.ae2e$tasks()).thenReturn(tasks);
        CraftingCpuExecutionContext restoredContext = new CraftingCpuExecutionContext();
        SpecialCraftingCpuService.readNbt(new CraftingCpuAdapter(restoredAccess, restoredContext), nbt);

        assertThat(restoredContext.specialMarked()).isTrue();
        assertThat(restoredContext.specialTotals()).containsEntry(details, 7L);
    }
}
