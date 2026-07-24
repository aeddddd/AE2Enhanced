package com.github.aeddddd.ae2enhanced.mixin.compat.neoecoae;

import appeng.api.networking.security.IActionSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * NeoECOAE {@code ECOCraftingCPU} 方法调用器.
 * <p>目标类为第三方可选依赖,通过字符串 target 指定,避免编译期依赖.</p>
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU", remap = false)
public interface EcoCraftingCPUInvoker {
    @Invoker("markDirty")
    void invokeMarkDirty();

    @Invoker("getActionSource")
    IActionSource invokeGetActionSource();
}
