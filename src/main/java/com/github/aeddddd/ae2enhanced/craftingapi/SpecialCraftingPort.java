package com.github.aeddddd.ae2enhanced.craftingapi;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** CPU 服务调用特殊计划、原生回落和执行门控的中性端口。 */
public interface SpecialCraftingPort {

    boolean isEnabled();
    Future<ICraftingJob> beginJob(ExecutorService pool, World world, IGrid grid, IActionSource source,
            IAEItemStack output, ICraftingCallback callback, boolean fallback);
    IAEItemStack canon(IAEItemStack stack);
    Object buildPatternIndex(ICraftingGrid grid);
    boolean isSpecial(ICraftingJob job);
    void onJobComputed(CraftingJob job, long elapsedNanos);
    void checkDeadline(CraftingJob job) throws InterruptedException;
    void snapshot(CraftingCpuState context, Map<ICraftingPatternDetails, Long> totals);
    Map<ICraftingPatternDetails, Long> totals(CraftingCpuState context);
    void clear(CraftingCpuState context);
    boolean hasQuota(CraftingCpuState context, IAEItemStack finalOutput);
    Set<ICraftingPatternDetails> vetoedSet(CraftingCpuState context,
            Map<ICraftingPatternDetails, Long> remaining, IAEItemStack finalOutput);
    long pushAllowance(CraftingCpuState context, ICraftingPatternDetails details, IAEItemStack finalOutput,
            Map<ICraftingPatternDetails, Long> remaining);
    CraftingGateResult handleInsert(CraftingCpuPort cpu, IAEItemStack input, Actionable type, IActionSource source);
    void tickSettle(CraftingCpuPort cpu, IActionSource source);
    boolean isSpecial(CraftingCpuState context);
}
