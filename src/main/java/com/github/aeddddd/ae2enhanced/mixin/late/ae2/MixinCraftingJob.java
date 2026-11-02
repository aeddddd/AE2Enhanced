package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.crafting.CraftingJob;
import com.github.aeddddd.ae2enhanced.crafting.CraftingJobService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CraftingJob 的普通服务注入壳。 */
@Mixin(value = CraftingJob.class, remap = false)
public class MixinCraftingJob {

    @Unique
    private long ae2enhanced$runStartNanos = -1L;

    @Inject(method = "run", at = @At("HEAD"))
    private void ae2enhanced$runHead(CallbackInfo ci) {
        ae2enhanced$runStartNanos = CraftingJobService.runHead();
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void ae2enhanced$sendCallCounts(CallbackInfo ci) {
        CraftingJobService.runReturn((CraftingJob) (Object) this, ae2enhanced$runStartNanos);
    }

    @Inject(method = "handlePausing", at = @At("HEAD"))
    private void ae2enhanced$nativeCalcBudgetCheck(CallbackInfo ci) throws InterruptedException {
        CraftingJobService.handlePausing((CraftingJob) (Object) this);
    }
}
