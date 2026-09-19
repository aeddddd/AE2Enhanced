package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.pathing.ControllerState;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.me.cache.PathGridCache;
import appeng.me.pathfinding.ControllerChannelUpdater;
import appeng.me.pathfinding.PathSegment;
import appeng.tile.networking.TileController;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.pathing.EnhancedPathingCalculation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

/**
 * 将 PR #8285 的快速频道路径算法接入 PathGridCache.
 * fastPathing 开启且网络有合法 Controller 时, 直接执行 O(N) 的 EnhancedPathingCalculation,
 * 不再使用原版的 PathSegment 多 tick 扩散.
 * skipRecalcWhenChannelsDisabled 开启且频道特性被禁用时, usedChannels 不影响设备激活,
 * 跳过原版的频道分配 BFS、逐节点 MENetworkChannelChanged 事件与 ticksUntilReady 倒计时.
 */
@Mixin(value = PathGridCache.class, remap = false)
public abstract class MixinPathGridCache {

    @Shadow
    private IGrid myGrid;

    @Shadow
    private List<PathSegment> active;

    @Shadow
    private boolean booting;

    @Shadow
    private boolean updateNetwork;

    @Shadow
    private int ticksUntilReady;

    @Shadow
    private ControllerState controllerState;

    @Shadow
    private boolean recalculateControllerNextTick;

    @Shadow
    private void recalcController() {
    }

    @Shadow
    private void achievementPost() {
    }

    @Shadow
    private void setChannelPowerUsage(double channelPowerUsage) {
    }

    @Shadow
    public abstract int getChannelsInUse();

    @Shadow
    public abstract void setChannelsInUse(int channelsInUse);

    @Shadow
    public abstract int getChannelsByBlocks();

    @Shadow
    public abstract void setChannelsByBlocks(int channelsByBlocks);

    @Invoker("calculateRequiredChannels")
    abstract int ae2enhanced$calculateRequiredChannels();

    /**
     * 在 onUpdateTick 起始处拦截：若开启快速算法且当前 tick 需要重算 Controller 网络，
     * 直接完成全部路径计算并取消原版方法。
     */
    @Inject(method = "onUpdateTick", at = @At("HEAD"), remap = false, cancellable = true)
    private void ae2enhanced$fastPathing(CallbackInfo ci) {
        // 先确定 Controller 状态（两条快速路径都依赖最新的 controllerState）。
        // recalcController 由 recalculateControllerNextTick 守卫，即使之后落入原版方法体也不会重复执行。
        if (this.recalculateControllerNextTick) {
            this.recalcController();
        }

        // 无限频道（CHANNELS 特性禁用）：usedChannels 对设备激活没有任何影响，
        // 原版的频道分配 BFS（无控制器时为 AdHocChannelUpdater，有控制器时为
        // PathSegment/ControllerChannelUpdater）、逐节点频道事件与 20+ tick 倒计时均为纯开销。
        // 仅维护统计数值（频道功耗统计与网络工具显示保持一致），跳过两次
        // MENetworkBootingStatusChange 广播以避免数千台机器的无意义 markForUpdate。
        // Controller 状态检测由上方 recalcController 正常执行，控制器外观不受影响。
        if (AE2EnhancedConfig.channelPathing.skipRecalcWhenChannelsDisabled
                && this.updateNetwork
                && !AEConfig.instance().isFeatureEnabled(AEFeature.CHANNELS)) {
            this.updateNetwork = false;
            this.active.clear();
            int used = this.ae2enhanced$calculateRequiredChannels();
            int nodes = this.myGrid.getNodes().size();
            this.setChannelsInUse(used);
            this.setChannelsByBlocks(nodes * used);
            this.setChannelPowerUsage((double) this.getChannelsByBlocks() / 128.0);
            this.ticksUntilReady = 0;
            this.booting = false;
            ci.cancel();
            return;
        }

        if (!AE2EnhancedConfig.channelPathing.fastPathing) {
            return;
        }

        if (!this.updateNetwork || this.controllerState != ControllerState.CONTROLLER_ONLINE) {
            return;
        }

        PathGridCache self = (PathGridCache) (Object) this;

        if (!this.booting) {
            this.myGrid.postEvent(new MENetworkBootingStatusChange());
        }
        this.booting = true;
        this.updateNetwork = false;
        this.setChannelsInUse(0);

        // 清除旧 PathSegment，避免后续逻辑继续处理。
        this.active.clear();

        int nodes = this.myGrid.getNodes().size();
        this.ticksUntilReady = 0; // 即时完成

        EnhancedPathingCalculation calc = new EnhancedPathingCalculation(this.myGrid);
        calc.compute();

        this.setChannelsInUse(calc.getChannelsInUse());
        this.setChannelsByBlocks(calc.getChannelsByBlocks());

        // 触发 finalizeChannels。
        Iterator<IGridNode> it = this.myGrid.getMachines(TileController.class).iterator();
        if (it.hasNext()) {
            IGridNode controllerNode = it.next();
            if (controllerNode != null) {
                controllerNode.beginVisit(new ControllerChannelUpdater());
            }
        }

        this.achievementPost();
        this.booting = false;
        this.setChannelPowerUsage((double) this.getChannelsByBlocks() / 128.0);
        this.myGrid.postEvent(new MENetworkBootingStatusChange());

        ci.cancel();
    }
}
