package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.pathing.IPathingGrid;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.IGridNodeAccessor;
import com.github.aeddddd.ae2enhanced.pathing.GridValidationBatcher;
import com.github.aeddddd.ae2enhanced.pathing.IEnhancedPathItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为 GridConnection 添加 PR #8285 快速频道路径算法所需的额外方法.
 */
@Mixin(value = GridConnection.class, remap = false)
public abstract class MixinGridConnection implements IEnhancedPathItem {

    @Shadow
    private int usedChannels;

    @Shadow
    private int lastUsedChannels;

    @Shadow
    private GridNode sideA;

    @Shadow
    private GridNode sideB;

    @Override
    public void ae2enhanced$setAdHocChannels(int channels) {
        this.usedChannels = channels;
        this.lastUsedChannels = channels;
    }

    @Override
    public GridNode ae2enhanced$getHighestSimilarAncestor() {
        return null;
    }

    @Override
    public boolean ae2enhanced$getSubtreeAllowsCompressedChannels() {
        return false;
    }

    @Override
    public int ae2enhanced$propagateChannelsUpwards(boolean consumesChannel) {
        // 对 GridConnection 忽略 consumesChannel 参数，使用无参语义。
        // sideA 总是朝向 Controller 的一侧（setControllerRoute 会翻转）。
        // 必须写入 lastUsedChannels，让原版 finalizeChannels() 把值同步到 usedChannels，
        // 否则 TheOneProbe / WAILA 等读取 usedChannels 的模组会显示 0。
        if (this.sideB.getControllerRoute() == (IPathItem) this) {
            this.lastUsedChannels = ((IEnhancedPathItem) this.sideB).ae2enhanced$getUsedChannels();
        } else {
            this.lastUsedChannels = 0;
        }
        return this.lastUsedChannels;
    }

    @Override
    public int ae2enhanced$getSubtreeMaxChannels() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int ae2enhanced$getMaxChannels() {
        return AEConfig.instance().isFeatureEnabled(AEFeature.CHANNELS)
                ? AEConfig.instance().getDenseChannelCapacity()
                : Integer.MAX_VALUE;
    }

    @Override
    public int ae2enhanced$getUsedChannels() {
        return this.lastUsedChannels;
    }

    /**
     * 当设置 Controller 路由时清空工作态频道数（高版本行为）。
     */
    @Inject(method = "setControllerRoute", at = @At("HEAD"), remap = false)
    private void ae2enhanced$onSetControllerRoute(IPathItem fast, boolean zeroOut, CallbackInfo ci) {
        if (zeroOut) {
            this.usedChannels = 0;
            this.lastUsedChannels = 0;
        }
    }

    /**
     * GridNode.destroy() 批处理期间：跳过每条连接销毁时的双侧 validateGrid 全图 BFS，
     * 改为登记到 GridValidationBatcher，待全部连接销毁后统一做一次分裂检测。
     * 非批处理场景（如变色断开的单条连接）保持原版行为。
     */
    @Inject(method = "destroy", at = @At("HEAD"), remap = false, cancellable = true)
    private void ae2enhanced$deferValidation(CallbackInfo ci) {
        if (!GridValidationBatcher.isBatching()) {
            return;
        }
        GridConnection self = (GridConnection) (Object) this;
        IPathingGrid p = (IPathingGrid) this.sideA.getInternalGrid().getCache(IPathingGrid.class);
        p.repath();
        ((IGridNodeAccessor) this.sideA).ae2e$removeConnection(self);
        ((IGridNodeAccessor) this.sideB).ae2e$removeConnection(self);
        GridValidationBatcher.defer(this.sideA);
        GridValidationBatcher.defer(this.sideB);
        ci.cancel();
    }
}
