package com.github.aeddddd.ae2enhanced.mixin.accessor;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;

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

    @Invoker("handlePausing")
    void invokeHandlePausing() throws InterruptedException;

    @Invoker("addMissing")
    void invokeAddMissing(AEKey what, long amount);
}
