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

import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;
import com.github.aeddddd.ae2enhanced.specialcrafting.RoutingDecision;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;

/**
 * 特殊配方路由（路由点 B:任务提交分流）.
 * <p>特殊计划（{@link SpecialPlanMarker} 标记）<b>独占路由</b>到本项目虚拟 CPU
 * （测试 CPU / 超因果计算核心）,不回落普通 CPU,防止语义错误的执行;
 * 普通计划直接放行（返回 null 约定,参考 NeoECOAE 的软路由注入面）.</p>
 */
@Mixin(value = CraftingService.class, remap = false)
public class MixinCraftingServiceSubmit {

    @Shadow(remap = false)
    @Final
    private IGrid grid;

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ae2e$routeSpecialJob(ICraftingPlan job, ICraftingRequester requestingMachine,
            ICraftingCPU target, boolean prioritizePower, IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // 与原生相同的先序校验:模拟(缺料)计划一律拒绝,且不得进入我方路由
        // (特殊求解器产出的失败计划也带标记,若直接路由会被 CPU 当完整计划执行)
        if (job.simulation()) {
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        if (!SpecialPlanMarker.isSpecial(job)) {
            return;
        }

        // 玩家/机器手动指定了 CPU:只接受我方虚拟 CPU,否则拒绝
        if (target != null) {
            if (!RoutingDecision.isOurVirtualCpu(target)) {
                cir.setReturnValue(
                        CraftingSubmitResult.simpleError(CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND));
                return;
            }
            cir.setReturnValue(
                    ((CraftingCPUCluster) target).submitJob(this.grid, job, src, requestingMachine));
            return;
        }

        // 自动分配:仅从本项目虚拟 CPU 中选择（同网格、在线、空闲、容量足够）
        for (CraftingCPUCluster cluster : VirtualCraftingCPURegistry.getClusters()) {
            if (cluster.isDestroyed() || !cluster.isActive() || cluster.isBusy()) {
                continue;
            }
            if (cluster.getGrid() != this.grid) {
                continue;
            }
            if (cluster.getAvailableStorage() < job.bytes()) {
                continue;
            }
            cir.setReturnValue(cluster.submitJob(this.grid, job, src, requestingMachine));
            return;
        }
        cir.setReturnValue(CraftingSubmitResult.NO_CPU_FOUND);
    }
}
