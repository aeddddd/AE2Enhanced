package com.github.aeddddd.ae2enhanced.mixin;

import com.google.common.collect.ImmutableSet;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;

import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;

/**
 * 本项目虚拟 CPU（测试 CPU / 超因果计算核心）与 AE2 合成服务的集成.
 * <p><b>注入面参考 NeoECOAE 的汇聚点模式（返回值叠加/入口拦截）,不再注入
 * {@code craftingCPUClusters} 字段集合</b>,行为差异：</p>
 * <ul>
 * <li>虚拟 CPU 出现在终端 CPU 列表({@code getCpus} 返回值追加,priority=900
 * 让其他兼容 mod 先跑),可被手动选择（原生对手动 target 不校验集合成员）;</li>
 * <li>原生自动分配（{@code findSuitableCraftingCPU}）<b>不会</b>把普通计划派给虚拟 CPU
 * ——普通合成与特殊配方测试完全隔离,特殊计划由
 * {@link MixinCraftingServiceSubmit} 独占硬路由;</li>
 * <li>合成逻辑 tick、在途物品送达、"正在合成"统计由本类在对应汇聚点补投.</li>
 * </ul>
 * 已知小缺口：监视器（watcher）的"正在合成"实时通知不含虚拟 CPU 任务（每 tick
 * 由原生体重建的 {@code currentlyCrafting} 不叠加）,对测试用途影响可忽略.
 */
@Mixin(value = CraftingService.class, priority = 900, remap = false)
public class MixinCraftingService {

    @Shadow(remap = false)
    @Final
    private IGrid grid;

    /**
     * 每 tick:清理失效注册 + 驱动本网格虚拟 CPU 的合成逻辑（字段注入移除后,
     * 原生 tick 循环不再覆盖虚拟 CPU,必须在此补投,否则执行中任务停摆）.
     */
    @Inject(method = "onServerEndTick", at = @At("TAIL"), remap = false)
    private void ae2e$tickVirtualCpus(CallbackInfo ci) {
        var clusters = VirtualCraftingCPURegistry.getClusters();
        if (clusters.isEmpty()) {
            return;
        }
        var energyService = this.grid.getEnergyService();
        var self = (CraftingService) (Object) this;
        for (CraftingCPUCluster cluster : clusters) {
            if (cluster.isDestroyed() || !cluster.isActive()) {
                VirtualCraftingCPURegistry.unregister(cluster);
                continue;
            }
            if (cluster.getGrid() == this.grid) {
                cluster.craftingLogic.tickCraftingLogic(energyService, self);
            }
        }
    }

    /**
     * CPU 列表：在原生返回值上追加本网格在线虚拟 CPU（不改写字段集合）.
     */
    @Inject(method = "getCpus", at = @At("RETURN"), cancellable = true, remap = false)
    private void ae2e$appendVirtualCpus(CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir) {
        var clusters = VirtualCraftingCPURegistry.getClusters();
        if (clusters.isEmpty()) {
            return;
        }
        var builder = ImmutableSet.<ICraftingCPU>builder().addAll(cir.getReturnValue());
        boolean added = false;
        for (CraftingCPUCluster cluster : clusters) {
            if (!cluster.isDestroyed() && cluster.isActive() && cluster.getGrid() == this.grid) {
                builder.add(cluster);
                added = true;
            }
        }
        if (added) {
            cir.setReturnValue(builder.build());
        }
    }

    /**
     * 识别虚拟 CPU（GUI/状态查询）.
     */
    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$hasVirtualCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (VirtualCraftingCPURegistry.getClusters().contains(cpu)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 在途物品送达：原生插入后的剩余量补投虚拟 CPU（机器产出回流到 CPU 库存,
     * 执行中作业才能推进）.
     */
    @Inject(method = "insertIntoCpus", at = @At("RETURN"), cancellable = true, remap = false)
    private void ae2e$insertIntoVirtualCpus(AEKey what, long amount, Actionable type,
            CallbackInfoReturnable<Long> cir) {
        long inserted = cir.getReturnValue();
        if (inserted >= amount) {
            return;
        }
        for (CraftingCPUCluster cluster : VirtualCraftingCPURegistry.getClusters()) {
            if (inserted >= amount) {
                break;
            }
            if (cluster.isDestroyed() || !cluster.isActive() || cluster.getGrid() != this.grid) {
                continue;
            }
            inserted += cluster.craftingLogic.insert(what, amount - inserted, type);
        }
        if (inserted > cir.getReturnValue()) {
            cir.setReturnValue(inserted);
        }
    }

    /**
     * "正在合成"数量统计：叠加虚拟 CPU 的 waitingFor.
     */
    @Inject(method = "getRequestedAmount", at = @At("RETURN"), cancellable = true, remap = false)
    private void ae2e$getVirtualRequestedAmount(AEKey what, CallbackInfoReturnable<Long> cir) {
        long extra = 0;
        for (CraftingCPUCluster cluster : VirtualCraftingCPURegistry.getClusters()) {
            if (!cluster.isDestroyed() && cluster.isActive() && cluster.getGrid() == this.grid) {
                extra += cluster.craftingLogic.getWaitingFor(what);
            }
        }
        if (extra > 0) {
            cir.setReturnValue(cir.getReturnValue() + extra);
        }
    }
}
