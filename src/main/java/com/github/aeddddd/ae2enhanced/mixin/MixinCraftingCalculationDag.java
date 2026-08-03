package com.github.aeddddd.ae2enhanced.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.craftingplan.dag.DagPlanAttempt;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingCalculation;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;

/**
 * DAG 计划引擎的尝试级挂接点(阶段 4.5,参照 Thunderbolt-Core 设计):
 * 拦截原生 {@code runCraftAttempt(simulate, amount)},由 DAG 引擎执行每次按量尝试,
 * 原生 {@code computePlan} 的策略循环(REPORT_MISSING_ITEMS 的失败重试、
 * CRAFT_LESS 的二分搜索)原样保留——CRAFT_LESS 因此自动获得 DAG 支持.
 * <p>仅 DEFAULT 模式生效;特殊配方求解器({@link SpecialCraftingCalculation})
 * 自驱动,不在此拦截;任何异常放行原生.</p>
 */
@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class MixinCraftingCalculationDag {

    @Shadow
    @Final
    private AEKey output;

    /** CraftingCalculation 不存 grid,构造时捕获 craftingService 供尝试级拦截使用. */
    @Unique
    private ICraftingService ae2e$craftingService;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void ae2e$captureCraftingService(Level level, IGrid grid,
            ICraftingSimulationRequester simRequester, GenericStack output,
            CalculationStrategy strategy, CallbackInfo ci) {
        this.ae2e$craftingService = grid.getCraftingService();
    }

    @Inject(method = "runCraftAttempt", at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2e$dagAttempt(boolean simulate, long amount,
            CallbackInfoReturnable<CraftingPlan> cir) {
        try {
            if (AE2EnhancedConfig.COMMON.dagPlannerMode.get() != AE2EnhancedConfig.DagPlannerMode.DEFAULT) {
                return;
            }
            CraftingCalculation self = (CraftingCalculation) (Object) this;
            if (self instanceof SpecialCraftingCalculation || ae2e$craftingService == null) {
                return; // 特殊求解器自驱动;服务未捕获时放行原生
            }
            var result = DagPlanAttempt.tryPlan(self, ae2e$craftingService, output, amount, simulate);
            switch (result.outcome()) {
                case SUCCESS -> {
                    if (result.hasCycleBoundary() && result.plan() != null) {
                        // 含循环内容的计划硬路由到模组虚拟 CPU 执行
                        SpecialPlanMarker.mark(result.plan());
                    }
                    cir.setReturnValue(result.plan());
                }
                case INFEASIBLE -> cir.setReturnValue(null); // 契约:非模拟尝试失败返回 null
                case FALLBACK -> {
                    // 放行原生尝试
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("DAG 尝试级拦截异常,放行原生: {}", t.toString());
        }
    }
}
