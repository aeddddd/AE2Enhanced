package com.github.aeddddd.ae2enhanced.mixin.accessor;

import java.util.concurrent.ExecutorService;

import appeng.me.service.CraftingService;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@link CraftingService} 的私有静态合成计算线程池,
 * 供路由层以与原生一致的调度方式提交特殊配方计算器.
 */
@Mixin(value = CraftingService.class, remap = false)
public interface CraftingServiceAccessor {

    @Accessor("CRAFTING_POOL")
    static ExecutorService getCraftingPool() {
        throw new UnsupportedOperationException("mixin accessor");
    }
}
