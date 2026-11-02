package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuPort;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuState;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingGateResult;
import com.github.aeddddd.ae2enhanced.craftingapi.SpecialCraftingPort;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** 特殊计划到中性 CPU 端口的适配器。 */
public final class SpecialCraftingPortImpl implements SpecialCraftingPort {

    @Override
    public boolean isEnabled() {
        return SpecialCraftingRuntime.isEnabled();
    }

    @Override
    public Future<ICraftingJob> beginJob(ExecutorService pool, World world, IGrid grid, IActionSource source,
            IAEItemStack output, ICraftingCallback callback, boolean fallback) {
        if (fallback) {
            FallbackLpCraftingJob job = new FallbackLpCraftingJob(world, grid, source, output, callback);
            return pool.submit(job, job);
        }
        LpCraftingJob job = new LpCraftingJob(world, grid, source, output, callback);
        return pool.submit(job, job);
    }

    @Override
    public IAEItemStack canon(IAEItemStack stack) {
        return RecursiveCraftingHelper.canon(stack);
    }

    @Override
    public Object buildPatternIndex(ICraftingGrid grid) {
        return NetworkPatternIndex.build(grid);
    }

    @Override
    public boolean isSpecial(ICraftingJob job) {
        return SpecialPlanMarker.isSpecial(job);
    }

    @Override
    public void onJobComputed(CraftingJob job, long elapsedNanos) {
        SpecialPlanDisplayHook.sendCallCounts(job);
        PlanObservationFactory.onJobComputed(job, elapsedNanos);
    }

    @Override
    public void checkDeadline(CraftingJob job) throws InterruptedException {
        NativeCalcBudget.checkDeadline(job);
    }

    @Override
    public void snapshot(CraftingCpuState context, Map<ICraftingPatternDetails, Long> totals) {
        RoundQuotaScheduler.snapshot(context, totals);
    }

    @Override
    public Map<ICraftingPatternDetails, Long> totals(CraftingCpuState context) {
        return RoundQuotaScheduler.totalsOf(context);
    }

    @Override
    public void clear(CraftingCpuState context) {
        RoundQuotaScheduler.clear(context);
    }

    @Override
    public boolean hasQuota(CraftingCpuState context, IAEItemStack finalOutput) {
        return RoundQuotaScheduler.hasQuota(context, finalOutput);
    }

    @Override
    public Set<ICraftingPatternDetails> vetoedSet(CraftingCpuState context,
            Map<ICraftingPatternDetails, Long> remaining, IAEItemStack finalOutput) {
        return RoundQuotaScheduler.vetoedSetForTick(context, remaining, finalOutput);
    }

    @Override
    public long pushAllowance(CraftingCpuState context, ICraftingPatternDetails details, IAEItemStack finalOutput,
            Map<ICraftingPatternDetails, Long> remaining) {
        return RoundQuotaScheduler.pushAllowance(context, details, finalOutput, remaining);
    }

    @Override
    public CraftingGateResult handleInsert(CraftingCpuPort cpu, IAEItemStack input, Actionable type,
            IActionSource source) {
        return SelfRefOutputGate.handleInsert(cpu, input, type, source);
    }

    @Override
    public void tickSettle(CraftingCpuPort cpu, IActionSource source) {
        SelfRefOutputGate.tickSettle(cpu, source);
    }

    @Override
    public boolean isSpecial(CraftingCpuState context) {
        return SpecialCraftingRuntime.isSpecialCluster(context);
    }
}
