package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.tile.AssemblyBatchService;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Assembly 批量注入壳；事务、并行预算、能耗与在途状态由普通服务处理。 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public abstract class MixinCraftingCPUClusterBatch {

    @Inject(method = "updateCraftingLogic", at = @At("HEAD"))
    private void onUpdateCraftingLogicHead(IGrid grid, IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        AssemblyBatchService.updateCraftingLogic((CraftingCPUCluster) (Object) this);
    }

    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/me/cache/CraftingGridCache;getMediums(Lappeng/api/networking/crafting/ICraftingPatternDetails;)Ljava/util/List;"),
            require = 0)
    private List<ICraftingMedium> ae2enhanced$memoizeNativeGetMediums(CraftingGridCache cache,
            ICraftingPatternDetails details) {
        return ((ICraftingGridCacheAccess) cache).ae2enhanced$getMediumsMemo(details);
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), order = 1100)
    private void batchProcessVirtualTasks(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        AssemblyBatchService.executeCrafting((CraftingCPUCluster) (Object) this, cache);
    }
}
