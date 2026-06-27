package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import java.util.List;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;
import appeng.tile.networking.TileController;
import com.github.aeddddd.ae2enhanced.pathing.IEnhancedPathItem;

/**
 * 为 GridNode 添加 PR #8285 快速频道路径算法所需的额外状态与方法。
 */
@Mixin(value = GridNode.class, remap = false)
public abstract class MixinGridNode implements IEnhancedPathItem {

    @Shadow
    private int usedChannels;

    @Shadow
    private List<IGridConnection> connections;

    @Shadow
    private int getMaxChannels() {
        return 0;
    }

    @Unique
    private GridNode ae2enhanced$highestSimilarAncestor = null;

    @Unique
    private int ae2enhanced$subtreeMaxChannels;

    @Unique
    private boolean ae2enhanced$subtreeAllowsCompressedChannels;

    @Override
    public void ae2enhanced$setAdHocChannels(int channels) {
        this.usedChannels = channels;
    }

    @Override
    @Nullable
    public GridNode ae2enhanced$getHighestSimilarAncestor() {
        return this.ae2enhanced$highestSimilarAncestor;
    }

    @Override
    public boolean ae2enhanced$getSubtreeAllowsCompressedChannels() {
        return this.ae2enhanced$subtreeAllowsCompressedChannels;
    }

    @Override
    public int ae2enhanced$propagateChannelsUpwards(boolean consumesChannel) {
        this.usedChannels = 0;
        for (IGridConnection connection : this.connections) {
            if (((IPathItem) connection).getControllerRoute() == (IPathItem) this) {
                this.usedChannels += ((IEnhancedPathItem) connection).ae2enhanced$getUsedChannels();
            }
        }
        if (consumesChannel) {
            this.usedChannels++;
        }
        return this.usedChannels;
    }

    /**
     * 在设置 Controller 路由时维护“最高同类祖先”链。
     */
    @Inject(method = "setControllerRoute", at = @At("HEAD"), remap = false)
    private void ae2enhanced$onSetControllerRoute(IPathItem fast, boolean zeroOut, CallbackInfo ci) {
        if (fast == null) {
            return;
        }

        GridNode self = (GridNode) (Object) this;
        GridNode parent = (GridNode) fast.getControllerRoute();

        if (parent == null || parent.getMachine() instanceof TileController) {
            // 父节点直接连接到 Controller，本节点是该子树的瓶颈起点。
            this.ae2enhanced$highestSimilarAncestor = null;
            this.ae2enhanced$subtreeMaxChannels = this.getMaxChannels();
            this.ae2enhanced$subtreeAllowsCompressedChannels = !self.hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED);
        } else {
            GridNode parentAncestor = ((IEnhancedPathItem) parent).ae2enhanced$getHighestSimilarAncestor();
            if (parentAncestor == null) {
                // 父节点直接连 Controller，父节点就是瓶颈。
                this.ae2enhanced$highestSimilarAncestor = parent;
            } else if (((IEnhancedPathItem) parent).ae2enhanced$getSubtreeMaxChannels() ==
                    ((IEnhancedPathItem) parentAncestor).ae2enhanced$getSubtreeMaxChannels()) {
                // 父节点不限制频道数，继续向上找。
                this.ae2enhanced$highestSimilarAncestor = parentAncestor;
            } else {
                // 父节点是新的瓶颈。
                this.ae2enhanced$highestSimilarAncestor = parent;
            }

            this.ae2enhanced$subtreeMaxChannels = Math.min(
                    ((IEnhancedPathItem) parent).ae2enhanced$getSubtreeMaxChannels(),
                    this.getMaxChannels());
            this.ae2enhanced$subtreeAllowsCompressedChannels =
                    ((IEnhancedPathItem) parent).ae2enhanced$getSubtreeAllowsCompressedChannels()
                            && !self.hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED);
        }
    }

    @Override
    public int ae2enhanced$getSubtreeMaxChannels() {
        return this.ae2enhanced$subtreeMaxChannels;
    }

    @Override
    public int ae2enhanced$getMaxChannels() {
        return this.getMaxChannels();
    }

    @Override
    public int ae2enhanced$getUsedChannels() {
        return this.usedChannels;
    }
}
