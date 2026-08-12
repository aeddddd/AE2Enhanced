package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.ring.RingProtection;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 飞升指环强制药水免疫：在药水效果注入列表之前拦截取消.
 * 覆盖其他模组的 PotionEffect 直加(最终都走 addPotionEffect).
 * MC 原生类,remap=true.
 */
@Mixin(value = EntityLivingBase.class, remap = true)
public class MixinRingPotionImmunity {

    @Inject(method = "addPotionEffect", at = @At("HEAD"), cancellable = true)
    private void ae2e$suppressPotion(PotionEffect effect, CallbackInfo ci) {
        if (RingProtection.isPotionSuppressed((EntityLivingBase) (Object) this, effect)) {
            ci.cancel();
        }
    }
}
