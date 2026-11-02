package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

import java.util.Map;

/** 普通 Java 服务使用的 CPU 状态适配器；测试可显式提供未织入的 fake access。 */
public final class CraftingCpuAdapter implements com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuPort {

    private final ISpecialCpuAccess access;
    private final CraftingCpuExecutionContext context;

    public CraftingCpuAdapter(ISpecialCpuAccess access, CraftingCpuExecutionContext context) {
        this.access = access;
        this.context = context;
    }

    public static CraftingCpuAdapter of(Object cpu) {
        ISpecialCpuAccess access = (ISpecialCpuAccess) cpu;
        return new CraftingCpuAdapter(access, access.ae2e$context());
    }

    public ISpecialCpuAccess access() {
        return access;
    }

    public CraftingCpuExecutionContext context() {
        return context;
    }

    public Map<ICraftingPatternDetails, Object> tasks() {
        return access.ae2e$tasks();
    }

    public IItemList<IAEItemStack> waitingFor() {
        return access.ae2e$waitingFor();
    }

    public IAEItemStack finalOutput() {
        return access.ae2e$finalOutput();
    }

    public void clearFinalOutput() {
        access.ae2e$clearFinalOutput();
    }

    public boolean isComplete() {
        return access.ae2e$isComplete();
    }

    public ICraftingLink lastLink() {
        return access.ae2e$myLastLink();
    }

    public IMEInventory<IAEItemStack> inventory() {
        return access.ae2e$inventory();
    }

    public IActionSource machineSource() {
        return access.ae2e$machineSource();
    }

    public int remainingOperations() {
        return access.ae2e$remainingOperations();
    }

    public void remainingOperations(int value) {
        access.ae2e$setRemainingOperations(value);
    }

    public long taskRemaining(ICraftingPatternDetails details) {
        Object progress = tasks().get(details);
        return progress == null ? 0L : access.ae2e$taskValue(progress);
    }

    public void correctTaskRemaining(ICraftingPatternDetails details, long extra) {
        Object progress = tasks().get(details);
        if (progress != null) {
            access.ae2e$setTaskValue(progress, Math.max(0L, access.ae2e$taskValue(progress) - extra));
        }
    }

    public void setTaskRemaining(Object progress, long value) {
        access.ae2e$setTaskValue(progress, value);
    }

    public void postChange(IAEItemStack diff, IActionSource source) {
        access.ae2e$postChange(diff, source);
    }

    public void postCraftingStatusChange(IAEItemStack diff) {
        access.ae2e$postCraftingStatusChange(diff);
    }

    public net.minecraft.world.World world() {
        return access.ae2e$world();
    }

    public void completeJob() {
        access.ae2e$completeJob();
    }

    public void updateCPU() {
        access.ae2e$updateCPU();
    }
}
