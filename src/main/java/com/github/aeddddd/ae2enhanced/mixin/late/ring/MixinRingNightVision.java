package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import com.github.aeddddd.ae2enhanced.ring.RingLocator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 指环夜视(非药水形式)：isPotionActive(NIGHT_VISION) 对佩戴指环并开启夜视的玩家返回 true,
 * 复用原版夜视渲染管线,但不进入药水列表(无 HUD 图标/无粒子/不可被牛奶清除).
 * MC 原生类,remap=true.
 */
@Mixin(value = EntityLivingBase.class, remap = true)
public class MixinRingNightVision {

    @Inject(method = "isPotionActive", at = @At("HEAD"), cancellable = true)
    private void ae2e$ringNightVision(Potion potion, CallbackInfoReturnable<Boolean> cir) {
        if (potion != MobEffects.NIGHT_VISION) return;
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (!(self instanceof EntityPlayer)) return;
        ItemStack ring = RingLocator.findRing((EntityPlayer) self);
        if (!ring.isEmpty() && RingNBT.isNightVisionEnabled(ring)) {
            cir.setReturnValue(true);
        }
    }
}
