package com.github.aeddddd.ae2enhanced.mixin.compat.advancedae;

import appeng.api.stacks.AEKeyType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * AdvancedAE 自带 {@code ElapsedTimeTracker} 的方法调用器（decrementItems 为包私有）.
 * <p>目标类为第三方可选依赖,通过字符串 target 指定,避免编译期依赖.</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker", remap = false)
public interface AdvElapsedTimeTrackerInvoker {
    @Invoker("decrementItems")
    void invokeDecrementItems(long amount, AEKeyType type);
}
