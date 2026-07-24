package com.github.aeddddd.ae2enhanced.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyHubBatchCrafting;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCpuLogicAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ExecutingCraftingJobAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 {@link CraftingCpuLogic#executeCrafting} 头部注入装配枢纽的批量合成处理,
 * 复刻主分支 1.12 对装配枢纽任务的虚拟/真实轨道分批处理.
 * <p>具体批量逻辑见 {@link AssemblyHubBatchCrafting},此处仅做 AE2 原版 CPU 的字段适配.</p>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public class MixinCraftingCpuLogic {

    @Inject(method = "executeCrafting", at = @At("HEAD"), remap = false)
    private void ae2e$batchProcessAssemblyHubTasks(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        CraftingCpuLogic logic = (CraftingCpuLogic) (Object) this;
        CraftingCpuLogicAccessor logicAccessor = (CraftingCpuLogicAccessor) logic;
        CraftingCPUCluster cluster = logicAccessor.getCluster();
        ExecutingCraftingJob job = logicAccessor.getJob();
        if (job == null) {
            return;
        }
        ExecutingCraftingJobAccessor jobAccessor = (ExecutingCraftingJobAccessor) job;
        AssemblyHubBatchCrafting.processHubBatches(jobAccessor.getTasks(), AssemblyHubBatchCrafting.AE2_TASK_ACCESS,
                logic.getInventory(), jobAccessor.getWaitingFor(),
                AssemblyHubBatchCrafting.ae2TimeTracker(jobAccessor.getTimeTracker()),
                jobAccessor.getFinalOutput(), cluster.getSrc(), cluster::markDirty, craftingService, level, null);
    }
}
