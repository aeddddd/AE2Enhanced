package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;
import com.github.aeddddd.ae2enhanced.mixin.bridge.IComputationCoreAccess;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 计算核心（TileComputationCore）虚拟集群支持.
 *
 * <p>当集群由计算核心托管时，接管集群的生命周期/网格查询方法，
 * 使 CraftingCPUCluster 脱离物理合成方块集群独立运作。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public class MixinCraftingCPUCluster implements IComputationCoreAccess {

    @Unique
    private TileComputationCore ae2enhanced$computationCore;

    @Override
    public void ae2enhanced$setComputationCore(TileComputationCore core) {
        this.ae2enhanced$computationCore = core;
    }

    @Override
    public TileComputationCore ae2enhanced$getComputationCore() {
        return this.ae2enhanced$computationCore;
    }

    @Shadow
    private String myName;

    // ==================== Overwrites for Virtual Clusters ====================

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void onIsActive(CallbackInfoReturnable<Boolean> cir) {
        if (ae2enhanced$computationCore != null) {
            IGridNode node = ae2enhanced$computationCore.getActionableNode();
            cir.setReturnValue(node != null && node.isActive());
        }
    }

    /** 每 tick 标脏合并标记: 本 tick 已真正执行过一次 markDirty 时为 true. */
    @Unique
    private boolean ae2enhanced$dirtyMarkedThisTick;

    /**
     * markDirty 每 tick 合并: executeCrafting 每次推送成功、injectItems 每次交付都会
     * markDirty → getCore().saveChanges() → 标 chunk 脏(spark 热点 ~2%).
     * chunk 脏标记在同 tick 内幂等,合并后持久化语义不变,至多损失不足 1 tick 的标脏时机.
     */
    @Inject(method = "markDirty", at = @At("HEAD"), cancellable = true)
    private void onMarkDirty(CallbackInfo ci) {
        if (ae2enhanced$dirtyMarkedThisTick) {
            ci.cancel();
            return;
        }
        ae2enhanced$dirtyMarkedThisTick = true;
        if (ae2enhanced$computationCore != null) {
            ae2enhanced$computationCore.markDirty();
            ci.cancel();
        }
    }

    /** updateCraftingLogic 每 tick 由 CraftingGridCache.onUpdateTick 对所有集群调用,在 tick 首重置标脏标记. */
    @Inject(method = "updateCraftingLogic", at = @At("HEAD"), require = 0)
    private void ae2enhanced$resetDirtyFlag(CallbackInfo ci) {
        ae2enhanced$dirtyMarkedThisTick = false;
    }

    @Inject(method = "getGrid", at = @At("HEAD"), cancellable = true)
    private void onGetGrid(CallbackInfoReturnable<IGrid> cir) {
        if (ae2enhanced$computationCore != null) {
            IGridNode node = ae2enhanced$computationCore.getActionableNode();
            cir.setReturnValue(node != null ? node.getGrid() : null);
        }
    }

    @Inject(method = "getWorld", at = @At("HEAD"), cancellable = true)
    private void onGetWorld(CallbackInfoReturnable<World> cir) {
        if (ae2enhanced$computationCore != null) {
            cir.setReturnValue(ae2enhanced$computationCore.getWorld());
        }
    }

    @Inject(method = "getCore", at = @At("HEAD"), cancellable = true)
    private void onGetCore(CallbackInfoReturnable<TileCraftingTile> cir) {
        if (ae2enhanced$computationCore != null) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "updateName", at = @At("HEAD"), cancellable = true)
    public void onUpdateName(CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            this.myName = net.minecraft.util.text.translation.I18n.translateToLocal("tile.ae2enhanced.computation_core.name");
            ci.cancel();
        }
    }

    @Inject(method = "breakCluster", at = @At("HEAD"), cancellable = true)
    public void onBreakCluster(CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            ci.cancel();
        }
    }

    @Inject(method = "done", at = @At("HEAD"), cancellable = true)
    void onDone(CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            ci.cancel();
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    public void onDestroy(CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            ci.cancel();
        }
    }

    @Inject(method = "updateStatus", at = @At("HEAD"), cancellable = true)
    public void onUpdateStatus(boolean updateGrid, CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            ci.cancel();
        }
    }

    @Redirect(
        method = "updateCraftingLogic",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/tile/crafting/TileCraftingTile;isActive()Z"
        )
    )
    private boolean redirectIsActive(TileCraftingTile instance) {
        if (ae2enhanced$computationCore != null) {
            IGridNode node = ae2enhanced$computationCore.getActionableNode();
            return node != null && node.isActive();
        }
        // 防御：某些情况下 getCore() 可能返回 null(如集群尚未完全初始化),
        // 此时应视为 inactive,避免 NPE.
        if (instance == null) {
            return false;
        }
        return instance.isActive();
    }
}
