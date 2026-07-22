package com.github.aeddddd.ae2enhanced.mixin;

import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 将创造能源元件的储电显示上限从 Long.MAX_VALUE / 10000（约 9.2e14）
 * 提升到完整的 Long.MAX_VALUE（9.2E）.
 */
@Mixin(value = CreativeEnergyCellBlockEntity.class, remap = false)
public class MixinCreativeEnergyCell {

    @Inject(method = { "getAEMaxPower", "getAECurrentPower" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$fullCreativePower(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue((double) Long.MAX_VALUE);
    }
}
