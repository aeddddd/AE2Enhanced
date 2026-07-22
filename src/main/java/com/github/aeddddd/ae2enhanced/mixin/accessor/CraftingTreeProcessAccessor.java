package com.github.aeddddd.ae2enhanced.mixin.accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 访问 {@link CraftingTreeProcess} 的样板定义与包私有的 request 方法.
 */
@Mixin(value = CraftingTreeProcess.class, remap = false)
public interface CraftingTreeProcessAccessor {

    @Accessor("details")
    IPatternDetails getDetails();

    @Invoker("request")
    void invokeRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException;

    @Invoker("limitsQuantity")
    boolean invokeLimitsQuantity();
}
