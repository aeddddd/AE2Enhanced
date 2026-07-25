package com.github.aeddddd.ae2enhanced.mixin.accessor;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.inv.NetworkCraftingSimulationState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 访问 {@link CraftingCalculation} 的模拟请求者与包私有方法.
 */
@Mixin(value = CraftingCalculation.class, remap = false)
public interface CraftingCalculationAccessor {

    @Accessor("simRequester")
    ICraftingSimulationRequester getSimRequester();

    @Accessor("networkInv")
    NetworkCraftingSimulationState getNetworkInv();

    @Invoker("handlePausing")
    void invokeHandlePausing() throws InterruptedException;

    @Invoker("addMissing")
    void invokeAddMissing(AEKey what, long amount);

    /**
     * 原生兜底计算（私有方法,特殊求解失败时回落）.
     */
    @Invoker("computePlan")
    ICraftingPlan invokeComputePlan() throws InterruptedException;

    /**
     * 原生收尾（私有方法,唤醒时间片等待方）.
     */
    @Invoker("finish")
    void invokeFinish();
}
