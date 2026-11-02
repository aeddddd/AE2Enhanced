package com.github.aeddddd.ae2enhanced.craftingapi;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

import java.util.Map;

/** CPU 原生字段的中性访问端口。 */
public interface CraftingCpuAccess {

    CraftingCpuState ae2e$context();
    Map<ICraftingPatternDetails, Object> ae2e$tasks();
    IMEInventory<IAEItemStack> ae2e$inventory();
    IActionSource ae2e$machineSource();
    int ae2e$remainingOperations();
    void ae2e$setRemainingOperations(int value);
    long ae2e$taskValue(Object progress);
    void ae2e$setTaskValue(Object progress, long value);
    Map<ICraftingPatternDetails, Long> ae2e$remainingSnapshot();
    IItemList<IAEItemStack> ae2e$waitingFor();
    IAEItemStack ae2e$finalOutput();
    void ae2e$clearFinalOutput();
    boolean ae2e$isComplete();
    net.minecraft.world.World ae2e$world();
    ICraftingLink ae2e$myLastLink();
    void ae2e$postChange(IAEItemStack diff, IActionSource src);
    void ae2e$postCraftingStatusChange(IAEItemStack diff);
    void ae2e$updateRemainingItemCount(IAEItemStack stack);
    void ae2e$markDirtyCluster();
    void ae2e$updateCPU();
    void ae2e$completeJob();
}
