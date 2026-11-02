package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGrid;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuAdapter;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuExecutionContext;
import com.github.aeddddd.ae2enhanced.crafting.VirtualCraftingCpuService;
import com.github.aeddddd.ae2enhanced.crafting.access.IComputationCoreAccess;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 计算核心虚拟集群注入壳。 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public class MixinCraftingCPUCluster implements IComputationCoreAccess {

    @Shadow
    private String myName;

    private CraftingCpuExecutionContext ae2enhanced$virtualContext() {
        return CraftingCpuAdapter.of(this).context();
    }

    @Override
    public void ae2enhanced$setComputationCore(Object core) {
        ae2enhanced$virtualContext().computationCore((TileComputationCore) core);
    }

    @Override
    public Object ae2enhanced$getComputationCore() {
        return ae2enhanced$virtualContext().computationCore();
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void onIsActive(CallbackInfoReturnable<Boolean> cir) {
        Boolean active = VirtualCraftingCpuService.activeOverride(ae2enhanced$virtualContext());
        if (active != null) {
            cir.setReturnValue(active);
        }
    }

    @Inject(method = "markDirty", at = @At("HEAD"), cancellable = true)
    private void onMarkDirty(CallbackInfo ci) {
        if (VirtualCraftingCpuService.cancelMarkDirty(ae2enhanced$virtualContext())) {
            ci.cancel();
        }
    }

    @Inject(method = "updateCraftingLogic", at = @At("HEAD"), order = 900)
    private void ae2enhanced$resetDirtyFlag(CallbackInfo ci) {
        ae2enhanced$virtualContext().beginTick();
    }

    @Inject(method = "getGrid", at = @At("HEAD"), cancellable = true)
    private void onGetGrid(CallbackInfoReturnable<IGrid> cir) {
        if (VirtualCraftingCpuService.isVirtual(ae2enhanced$virtualContext())) {
            cir.setReturnValue(VirtualCraftingCpuService.gridOverride(ae2enhanced$virtualContext()));
        }
    }

    @Inject(method = "getWorld", at = @At("HEAD"), cancellable = true)
    private void onGetWorld(CallbackInfoReturnable<World> cir) {
        if (VirtualCraftingCpuService.isVirtual(ae2enhanced$virtualContext())) {
            cir.setReturnValue(VirtualCraftingCpuService.worldOverride(ae2enhanced$virtualContext()));
        }
    }

    @Inject(method = "getCore", at = @At("HEAD"), cancellable = true)
    private void onGetCore(CallbackInfoReturnable<TileCraftingTile> cir) {
        if (VirtualCraftingCpuService.isVirtual(ae2enhanced$virtualContext())) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "updateName", at = @At("HEAD"), cancellable = true)
    public void onUpdateName(CallbackInfo ci) {
        String name = VirtualCraftingCpuService.localizedName(ae2enhanced$virtualContext());
        if (name != null) {
            myName = name;
            ci.cancel();
        }
    }

    @Inject(method = "breakCluster", at = @At("HEAD"), cancellable = true)
    public void onBreakCluster(CallbackInfo ci) {
        if (VirtualCraftingCpuService.isVirtual(ae2enhanced$virtualContext())) {
            ci.cancel();
        }
    }

    @Inject(method = "done", at = @At("HEAD"), cancellable = true)
    void onDone(CallbackInfo ci) {
        if (VirtualCraftingCpuService.isVirtual(ae2enhanced$virtualContext())) {
            ci.cancel();
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    public void onDestroy(CallbackInfo ci) {
        if (VirtualCraftingCpuService.isVirtual(ae2enhanced$virtualContext())) {
            ci.cancel();
        }
    }

    @Inject(method = "updateStatus", at = @At("HEAD"), cancellable = true)
    public void onUpdateStatus(boolean updateGrid, CallbackInfo ci) {
        if (VirtualCraftingCpuService.isVirtual(ae2enhanced$virtualContext())) {
            ci.cancel();
        }
    }

    @Redirect(method = "updateCraftingLogic", at = @At(value = "INVOKE",
            target = "Lappeng/tile/crafting/TileCraftingTile;isActive()Z"))
    private boolean redirectIsActive(TileCraftingTile instance) {
        return VirtualCraftingCpuService.redirectTileActive(ae2enhanced$virtualContext(), instance);
    }
}
