package com.github.aeddddd.ae2enhanced.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;

import com.github.aeddddd.ae2enhanced.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.computation.cpu.IVirtualCraftingCPU;
import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPU;
import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;
import com.github.aeddddd.ae2enhanced.specialcrafting.RoutingDecision;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;

/**
 * 虚拟 CPU 任务提交路由（路由点 B:任务提交分流）.
 * <p>特殊计划（{@link SpecialPlanMarker} 标记）<b>独占路由</b>到本项目虚拟 CPU
 * （测试 CPU / 超因果计算核心）,不回落普通 CPU,防止语义错误的执行;
 * 普通计划优先分配给超因果计算核心的子 CPU,无空闲时<b>立即分裂</b>新子 CPU
 * （参考 AAE 量子计算机）,池满才回落原生分配.</p>
 */
@Mixin(value = CraftingService.class, remap = false)
public class MixinCraftingServiceSubmit {

    @Shadow(remap = false)
    @Final
    private IGrid grid;

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ae2e$routeJob(ICraftingPlan job, ICraftingRequester requestingMachine,
            ICraftingCPU target, boolean prioritizePower, IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // 与原生相同的先序校验:模拟(缺料)计划一律拒绝,且不得进入我方路由
        // (特殊求解器产出的失败计划也带标记,若直接路由会被 CPU 当完整计划执行)
        if (job.simulation()) {
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }

        boolean special = SpecialPlanMarker.isSpecial(job);

        // 玩家/机器手动指定了 CPU
        if (target != null) {
            if (RoutingDecision.isOurVirtualCpu(target)) {
                // 目标为我方虚拟 CPU:忙碌时分裂新子 CPU 承接（参考 AAE 量子计算机）
                cir.setReturnValue(ae2e$submitToOurs((CraftingCPUCluster) target, job, src, requestingMachine));
            } else if (special) {
                // 特殊计划只允许我方虚拟 CPU 执行,拒绝其他目标
                cir.setReturnValue(
                        CraftingSubmitResult.simpleError(CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND));
            }
            // 普通计划指定普通 CPU:放行原生
            return;
        }

        // 自动分配:优先从我方虚拟 CPU 中选择（同网格、在线、空闲、容量足够）
        for (CraftingCPUCluster cluster : VirtualCraftingCPURegistry.getClusters()) {
            if (cluster.isDestroyed() || !cluster.isActive() || cluster.isBusy()) {
                continue;
            }
            if (cluster.getGrid() != this.grid) {
                continue;
            }
            // 普通计划只派给超因果计算核心的子 CPU,不派给测试 CPU
            if (!special && ((IVirtualCraftingCPU) (Object) cluster).ae2enhanced$getHost() == null) {
                continue;
            }
            if (cluster.getAvailableStorage() < job.bytes()) {
                continue;
            }
            cir.setReturnValue(cluster.submitJob(this.grid, job, src, requestingMachine));
            return;
        }

        // 无空闲子 CPU:立即分裂一个新子 CPU 承接本任务（参考 AAE 量子计算机）
        VirtualCraftingCPU spawned = ae2e$spawnSubCpu();
        if (spawned != null) {
            cir.setReturnValue(spawned.getCluster().submitJob(this.grid, job, src, requestingMachine));
            return;
        }

        if (special) {
            // 特殊计划不回落普通 CPU
            cir.setReturnValue(CraftingSubmitResult.NO_CPU_FOUND);
        }
        // 普通计划池满:放行原生分配（返回 null 约定,参考 NeoECOAE 的软路由注入面）
    }

    /**
     * 向指定的我方虚拟 CPU 提交;若其忙碌则尝试分裂新子 CPU 承接.
     */
    private ICraftingSubmitResult ae2e$submitToOurs(CraftingCPUCluster target, ICraftingPlan job,
            IActionSource src, ICraftingRequester requestingMachine) {
        if (!target.isBusy()) {
            return target.submitJob(this.grid, job, src, requestingMachine);
        }
        VirtualCraftingCPU spawned = ae2e$spawnSubCpu();
        if (spawned != null) {
            return spawned.getCluster().submitJob(this.grid, job, src, requestingMachine);
        }
        return target.submitJob(this.grid, job, src, requestingMachine);
    }

    /**
     * 找到本网格内的计算核心宿主并分裂一个新子 CPU.
     */
    private VirtualCraftingCPU ae2e$spawnSubCpu() {
        for (CraftingCPUCluster cluster : VirtualCraftingCPURegistry.getClusters()) {
            if (cluster.isDestroyed() || cluster.getGrid() != this.grid) {
                continue;
            }
            ComputationCoreBlockEntity host = ((IVirtualCraftingCPU) (Object) cluster).ae2enhanced$getHost();
            if (host != null) {
                return host.spawnSubCpu();
            }
        }
        return null;
    }
}
