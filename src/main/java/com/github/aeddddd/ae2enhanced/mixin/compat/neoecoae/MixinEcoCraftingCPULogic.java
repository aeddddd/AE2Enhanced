package com.github.aeddddd.ae2enhanced.mixin.compat.neoecoae;

import java.util.List;

import cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob;

import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyHubBatchCrafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoECOAEExtension ECO 计算系统（{@code ECOCraftingCPULogic}）的装配枢纽批量合成兼容.
 * <p>NeoECO 完全自写了 CPU 逻辑与 {@code ExecutingCraftingJob}/{@code TaskProgress},
 * 不走 AE2 原版 {@code CraftingCpuLogic},因此枢纽的批量 Mixin 对其无效,
 * 需要在其私有 {@code executeCrafting} 头部同样注入.</p>
 * <p>与原版/AdvancedAE 的差异：</p>
 * <ul>
 * <li>任务进度为其自带的 {@code TaskProgress},需专用访问器;</li>
 * <li>登记 waitingFor 时同步调用 {@code addInFlightOutputs},维持其
 * inFlightOutputs 在途记账（最终产物限量与依赖阻塞判定均依赖该计数）,
 * 由其 {@code insert} 中的 removeInFlightOutput 对称核销.</li>
 * </ul>
 * <p>仅在 neoecoae 加载时由 Mixin Plugin 启用；@Pseudo 保证目标缺失时不报错.</p>
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic", remap = false)
public class MixinEcoCraftingCPULogic {

    @Unique
    private static final AssemblyHubBatchCrafting.TaskProgressAccess ae2e$ECO_TASK_ACCESS =
            new AssemblyHubBatchCrafting.TaskProgressAccess() {
                @Override
                public long get(Object progress) {
                    return ((EcoTaskProgressAccessor) progress).getValue();
                }

                @Override
                public void set(Object progress, long value) {
                    ((EcoTaskProgressAccessor) progress).setValue(value);
                }
            };

    @Inject(method = "executeCrafting", at = @At("HEAD"), remap = false)
    private void ae2e$batchProcessAssemblyHubTasks(int slowPatternBudget, int totalPatternBudget,
            CraftingService craftingService, IEnergyService energyService, Level level,
            @Coerce Object batchBudget, CallbackInfoReturnable<Integer> cir) {
        EcoCraftingCPULogicAccessor logic = (EcoCraftingCPULogicAccessor) this;
        ExecutingCraftingJob job = logic.getJob();
        if (job == null) {
            return;
        }
        EcoExecutingCraftingJobAccessor jobAccessor = (EcoExecutingCraftingJobAccessor) job;
        EcoCraftingCPUInvoker cpu = (EcoCraftingCPUInvoker) logic.getCpu();
        EcoElapsedTimeTrackerInvoker timeTracker = (EcoElapsedTimeTrackerInvoker) jobAccessor.getTimeTracker();
        AssemblyHubBatchCrafting.processHubBatches(jobAccessor.getTasks(), ae2e$ECO_TASK_ACCESS,
                logic.getInventory(), jobAccessor.getWaitingFor(), timeTracker::invokeDecrementItems,
                jobAccessor.getFinalOutput(), cpu.invokeGetActionSource(), cpu::invokeMarkDirty, craftingService,
                level,
                (key, amount) -> jobAccessor.invokeAddInFlightOutputs(List.of(new GenericStack(key, amount)), 1));
    }
}
