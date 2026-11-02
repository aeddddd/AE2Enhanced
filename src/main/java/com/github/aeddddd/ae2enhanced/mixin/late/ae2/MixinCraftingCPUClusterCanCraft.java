package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.crafting.CanCraftCacheService;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuAdapter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** canCraft 失败缓存注入壳；priority 900 保持 Special 在外层。 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 900)
public abstract class MixinCraftingCPUClusterCanCraft {

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;canCraft(Lappeng/api/networking/crafting/ICraftingPatternDetails;[Lappeng/api/storage/data/IAEItemStack;)Z"))
    private boolean ae2enhanced$cacheCanCraftFailure(CraftingCPUCluster self, ICraftingPatternDetails details,
            IAEItemStack[] condensedInputs, Operation<Boolean> original) {
        return CanCraftCacheService.canCraft(CraftingCpuAdapter.of(this), details,
                () -> original.call(self, details, condensedInputs),
                () -> original.call(self, details, condensedInputs));
    }

    @Inject(method = "storeItems", at = @At("HEAD"))
    private void ae2enhanced$clearFailCacheOnStoreItems(CallbackInfo ci) {
        CanCraftCacheService.invalidate(CraftingCpuAdapter.of(this).context());
    }

    @Inject(method = "submitJob", at = @At("RETURN"))
    private void ae2enhanced$clearFailCacheOnSubmitJob(IGrid grid, ICraftingJob job, IActionSource src,
            ICraftingRequester requestingMachine, CallbackInfoReturnable<ICraftingLink> cir) {
        CanCraftCacheService.invalidate(CraftingCpuAdapter.of(this).context());
    }
}
