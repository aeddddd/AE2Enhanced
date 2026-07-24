package com.github.aeddddd.ae2enhanced.mixin.compat.advancedae;

import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyHubBatchCrafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AdvancedAE 量子计算机（{@code AdvCraftingCPULogic}）的装配枢纽批量合成兼容.
 * <p>AdvancedAE 完全自写了 CPU 逻辑,不走 AE2 原版 {@code CraftingCpuLogic},
 * 因此枢纽的批量 Mixin 对其无效,需要在其 {@code executeCrafting} 头部同样注入.</p>
 * <p>针对发布版 1.3.5 的结构：其 {@code ExecutingCraftingJob}/{@code TaskProgress}/
 * {@code ElapsedTimeTracker} 均为自带副本（与 forge/1.20.1 分支 HEAD 不同,HEAD 已改回
 * 复用 AE2 的同名类）,因此使用专用访问器,进度统计经
 * {@link AssemblyHubBatchCrafting.BatchTimeTracker} lambda 适配.</p>
 * <p>仅在 advanced_ae 加载时由 Mixin Plugin 启用；@Pseudo 保证目标缺失时不报错.</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public class MixinAdvCraftingCPULogic {

    @Unique
    private static final AssemblyHubBatchCrafting.TaskProgressAccess ae2e$ADV_TASK_ACCESS =
            new AssemblyHubBatchCrafting.TaskProgressAccess() {
                @Override
                public long get(Object progress) {
                    return ((AdvTaskProgressAccessor) progress).getValue();
                }

                @Override
                public void set(Object progress, long value) {
                    ((AdvTaskProgressAccessor) progress).setValue(value);
                }
            };

    @Inject(method = "executeCrafting", at = @At("HEAD"), remap = false)
    private void ae2e$batchProcessAssemblyHubTasks(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        AdvCraftingCPULogicAccessor logic = (AdvCraftingCPULogicAccessor) this;
        ExecutingCraftingJob job = logic.getJob();
        if (job == null) {
            return;
        }
        AdvExecutingCraftingJobAccessor jobAccessor = (AdvExecutingCraftingJobAccessor) job;
        AdvCraftingCPUInvoker cpu = (AdvCraftingCPUInvoker) logic.getCpu();
        AdvElapsedTimeTrackerInvoker timeTracker = (AdvElapsedTimeTrackerInvoker) jobAccessor.getTimeTracker();
        AssemblyHubBatchCrafting.processHubBatches(jobAccessor.getTasks(), ae2e$ADV_TASK_ACCESS,
                logic.getInventory(), jobAccessor.getWaitingFor(), timeTracker::invokeDecrementItems,
                jobAccessor.getFinalOutput(), cpu.invokeGetSrc(), cpu::invokeMarkDirty, craftingService, level, null);
    }
}
