package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import java.util.Iterator;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.pathing.ControllerState;
import appeng.me.cache.PathGridCache;
import appeng.me.pathfinding.ControllerChannelUpdater;
import appeng.me.pathfinding.PathSegment;
import appeng.tile.networking.TileController;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.pathing.EnhancedPathingCalculation;

/**
 * 将 PR #8285 的快速频道路径算法接入 PathGridCache。
 *
 * <p>当 {@link AE2EnhancedConfig.ChannelPathing#fastPathing} 开启且网络有合法 Controller 时，
 * 直接执行 O(N) 的 EnhancedPathingCalculation，不再使用原版的 PathSegment 多 tick 扩散。</p>
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

    /**
     * 在 onUpdateTick 起始处拦截：若开启快速算法且当前 tick 需要重算 Controller 网络，
     * 直接完成全部路径计算并取消原版方法。
     */
    @Inject(method = "onUpdateTick", at = @At("HEAD"), remap = false, cancellable = true)
    private void ae2enhanced$fastPathing(CallbackInfo ci) {
        if (!AE2EnhancedConfig.channelPathing.fastPathing) {
            return;
        }

        // 先确定 Controller 状态。
        if (this.recalculateControllerNextTick) {
            this.recalcController();
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
