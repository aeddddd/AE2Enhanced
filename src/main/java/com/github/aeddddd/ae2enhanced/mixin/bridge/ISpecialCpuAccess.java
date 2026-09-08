package com.github.aeddddd.ae2enhanced.mixin.bridge;

import java.util.Map;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

/**
 * CraftingCPUCluster 内部成员的访问接口（特殊配方执行层专用）.
 * 由 MixinCraftingCPUCluster 实现,供 SelfRefOutputGate / RoundQuotaScheduler 使用.
 */
public interface ISpecialCpuAccess {

    Map<ICraftingPatternDetails, Object> ae2e$tasks();

    /**
     * 本趟 executeCrafting 的 remaining 快照（nullable）.
     * 仅在有配额门控且本趟首次否决判定后非空;供聚合发配的配额余量核算复用,
     * 避免同一趟内重复构建.
     */
    @javax.annotation.Nullable
    Map<ICraftingPatternDetails, Long> ae2e$remainingSnapshot();

    IItemList<IAEItemStack> ae2e$waitingFor();

    IAEItemStack ae2e$finalOutput();

    ICraftingLink ae2e$myLastLink();

    void ae2e$postChange(IAEItemStack diff, IActionSource src);

    void ae2e$postCraftingStatusChange(IAEItemStack diff);

    void ae2e$updateRemainingItemCount(IAEItemStack is);

    void ae2e$markDirtyCluster();

    void ae2e$updateCPU();

    void ae2e$completeJob();
}
