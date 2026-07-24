package com.github.aeddddd.ae2enhanced.mixin.compat.neoecoae;

import appeng.api.stacks.AEKeyType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * NeoECOAE 自带 {@code ElapsedTimeTracker} 的方法调用器（decrementItems 为包私有）.
 * <p>NeoECOAE 仅以 modCompileOnly 引入（不打包）.</p>
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ElapsedTimeTracker", remap = false)
public interface EcoElapsedTimeTrackerInvoker {
    @Invoker("decrementItems")
    void invokeDecrementItems(long amount, AEKeyType type);
}
