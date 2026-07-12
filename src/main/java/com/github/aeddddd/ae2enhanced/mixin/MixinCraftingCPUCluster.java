package com.github.aeddddd.ae2enhanced.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.computation.cpu.IVirtualCraftingCPU;

/**
 * 增强 {@link CraftingCPUCluster}，使由超因果计算核心托管的虚拟集群
 * 将关键操作重定向到宿主控制器，避免访问未放入世界的虚假合成单元。
 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public class MixinCraftingCPUCluster implements IVirtualCraftingCPU {

    @Shadow
    @Nullable
    private Component myName;

    @Unique
    private ComputationCoreBlockEntity ae2enhanced$host;

    @Override
    public void ae2enhanced$setHost(ComputationCoreBlockEntity host) {
        this.ae2enhanced$host = host;
    }

    @Override
    @Nullable
    public ComputationCoreBlockEntity ae2enhanced$getHost() {
        return this.ae2enhanced$host;
    }

    @Override
    public boolean ae2enhanced$isVirtual() {
        return this.ae2enhanced$host != null;
    }

    @Inject(method = "markDirty", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2enhanced$onMarkDirty(CallbackInfo ci) {
        if (this.ae2enhanced$host != null) {
            this.ae2enhanced$host.setChanged();
            ci.cancel();
        }
    }

    @Inject(method = "getGrid", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2enhanced$onGetGrid(CallbackInfoReturnable<IGrid> cir) {
        if (this.ae2enhanced$host != null) {
            IGridNode node = this.ae2enhanced$host.getActionSourceNode();
            cir.setReturnValue(node != null ? node.getGrid() : null);
        }
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2enhanced$onIsActive(CallbackInfoReturnable<Boolean> cir) {
        if (this.ae2enhanced$host != null) {
            IGridNode node = this.ae2enhanced$host.getActionSourceNode();
            cir.setReturnValue(node != null && node.isActive());
        }
    }

    @Inject(method = "breakCluster", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2enhanced$onBreakCluster(CallbackInfo ci) {
        if (this.ae2enhanced$host != null) {
            ci.cancel();
        }
    }

    @Inject(method = "done", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2enhanced$onDone(CallbackInfo ci) {
        if (this.ae2enhanced$host != null) {
            ci.cancel();
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2enhanced$onDestroy(CallbackInfo ci) {
        if (this.ae2enhanced$host != null) {
            ci.cancel();
        }
    }

    @Inject(method = "updateName", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2enhanced$onUpdateName(CallbackInfo ci) {
        if (this.ae2enhanced$host != null) {
            this.myName = Component.translatable("block.ae2enhanced.computation_core");
            ci.cancel();
        }
    }

    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2enhanced$onGetLevel(CallbackInfoReturnable<Level> cir) {
        if (this.ae2enhanced$host != null) {
            cir.setReturnValue(this.ae2enhanced$host.getLevel());
        }
    }

    @Inject(method = "updateStatus(Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2enhanced$onUpdateStatus(boolean updateGrid, CallbackInfo ci) {
        if (this.ae2enhanced$host != null) {
            ci.cancel();
        }
    }
}
