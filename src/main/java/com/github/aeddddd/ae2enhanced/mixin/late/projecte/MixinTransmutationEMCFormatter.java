package com.github.aeddddd.ae2enhanced.mixin.late.projecte;

import com.github.aeddddd.ae2enhanced.client.util.BigEmcFormatter;
import moze_intel.projecte.utils.TransmutationEMCFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

/**
 * 让 ProjectE 的 EMC 格式化器在 long 值 &ge; 1e18 时也使用科学记数法。
 */
@Mixin(value = TransmutationEMCFormatter.class, remap = false)
public class MixinTransmutationEMCFormatter {

    @Inject(method = "EMCFormat(J)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ae2e$onEmcFormat(long emc, CallbackInfoReturnable<String> cir) {
        if (emc >= 1_000_000_000_000_000_000L) {
            cir.setReturnValue(BigEmcFormatter.format(BigInteger.valueOf(emc)));
        }
    }
}
