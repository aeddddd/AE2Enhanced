package com.github.aeddddd.ae2enhanced.mixin;

import java.util.concurrent.Future;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.service.CraftingService;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingServiceAccessor;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingCalculation;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialRecipeDetector;

/**
 * 特殊配方路由（路由点 A:计算请求分流）.
 * <p>参考 NeoECOAE 的 CraftingServiceMixin 汇聚点注入模式：仅拦截入口,
 * detector 命中才提交自有 {@link SpecialCraftingCalculation} 并复用原生
 * {@code CRAFTING_POOL} 线程池;未命中/detector 异常时直接放行,
 * 原生 {@code beginCraftingCalculation} 行为零改动.</p>
 * <p>{@code require = 0}:该方法若被其他附属 mod 改写,本注入静默失效,
 * 功能退化为"无特殊配方支持"而非崩溃.</p>
 */
@Mixin(value = CraftingService.class, remap = false)
public class MixinCraftingServiceRouting {

    @Shadow(remap = false)
    @Final
    private IGrid grid;

    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ae2e$routeSpecialCalculation(Level level, ICraftingSimulationRequester simRequester,
            AEKey what, long amount, CalculationStrategy strategy,
            CallbackInfoReturnable<Future<ICraftingPlan>> cir) {
        try {
            boolean hit = SpecialRecipeDetector.mayInvolveSpecialRecipes(this.grid.getCraftingService(), what);
            if (!hit) {
                // 诊断:仅当请求物可合成但未命中时记录(排查"无法识别循环"类问题)
                if (!this.grid.getCraftingService().getCraftingFor(what).isEmpty()) {
                    AE2Enhanced.LOGGER.info("[特殊配方] 路由未命中,走原生计算: {}×{}", what, amount);
                }
                return;
            }
            AE2Enhanced.LOGGER.info("[特殊配方] 路由命中,提交专用求解器: {}×{}", what, amount);
            var job = new SpecialCraftingCalculation(level, this.grid, simRequester,
                    new GenericStack(what, amount), strategy);
            cir.setReturnValue(CraftingServiceAccessor.getCraftingPool().submit(job::run));
        } catch (Throwable t) {
            // 宁可漏判不可误判:路由层任何异常都放行原生
            AE2Enhanced.LOGGER.warn("特殊配方路由判定异常,放行原生计算: {}", t.toString());
        }
    }
}
