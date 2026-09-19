package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.me.cache.TickManagerCache;
import com.github.aeddddd.ae2enhanced.diag.DiagSwitch;
import com.github.aeddddd.ae2enhanced.diag.perf.NodeTimingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 每节点 tick 耗时采集. UEL 删除了 TickTracker 的计时字段,
 * LastFiveTicksTime 恒为 0, 本 Mixin 包装 onUpdateTick 中的
 * tickingRequest 调用, 自行记录到 {@link NodeTimingRegistry}.
 *
 * <p>开关: /ae2e debug perf off 时直通原调用, 零计时开销.</p>
 */
@Mixin(value = TickManagerCache.class, remap = false)
public abstract class MixinTickManagerCache {

    @WrapOperation(method = "onUpdateTick", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/ticking/IGridTickable;tickingRequest(Lappeng/api/networking/IGridNode;I)Lappeng/api/networking/ticking/TickRateModulation;"))
    private TickRateModulation ae2e$timeTickingRequest(IGridTickable tickable, IGridNode node, int diff,
            Operation<TickRateModulation> original) {
        if (!DiagSwitch.isEnabled(DiagSwitch.PERF)) {
            return original.call(tickable, node, diff);
        }
        long start = System.nanoTime();
        try {
            return original.call(tickable, node, diff);
        } finally {
            NodeTimingRegistry.record(node, System.nanoTime() - start);
        }
    }

    /** 节点移除时同步清理计时注册表, 属于 WeakHashMap 之外的主动回收. */
    @Inject(method = "removeNode", at = @At("HEAD"), require = 0)
    private void ae2e$onRemoveNode(IGridNode gridNode, IGridHost machine, CallbackInfo ci) {
        NodeTimingRegistry.remove(gridNode);
    }
}
