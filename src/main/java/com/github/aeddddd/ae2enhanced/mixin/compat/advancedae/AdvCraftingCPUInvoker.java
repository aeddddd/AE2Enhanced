package com.github.aeddddd.ae2enhanced.mixin.compat.advancedae;

import appeng.api.networking.security.IActionSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * AdvancedAE {@code AdvCraftingCPU} 方法调用器.
 * <p>目标类为第三方可选依赖,通过字符串 target 指定,避免编译期依赖.</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU", remap = false)
public interface AdvCraftingCPUInvoker {
    @Invoker("markDirty")
    void invokeMarkDirty();

    @Invoker("getSrc")
    IActionSource invokeGetSrc();
}
