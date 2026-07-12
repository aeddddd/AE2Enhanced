package com.github.aeddddd.ae2enhanced.mixin.accessor;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link ElapsedTimeTracker} 方法调用器，替代运行时反射调用包级私有方法。
 */
@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface ElapsedTimeTrackerAccessor {
    @Invoker("decrementItems")
    void invokeDecrementItems(long amount, AEKeyType type);
}
