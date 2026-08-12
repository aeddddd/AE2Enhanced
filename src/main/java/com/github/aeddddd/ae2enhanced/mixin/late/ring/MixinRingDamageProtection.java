package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.ring.RingProtection;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 飞升指环绝对防护(与项目强制击杀同层级)：
 * 1. damageEntity：拦截绕过 Forge 事件的直接伤害(如物质炮共形处决)
 * 2. setHealth：拦截外部直接扣血写入
 * 3. onDeath：拦截强制死亡回调
 * MC 原生类,remap=true.
 */
@Mixin(value = EntityLivingBase.class, remap = true)
public class MixinRingDamageProtection {

    @Inject(method = "damageEntity", at = @At("HEAD"), cancellable = true)
    private void ae2e$blockBypassDamage(DamageSource source, float amount, CallbackInfo ci) {
        if (RingProtection.isAbsoluteProtectionActive((EntityLivingBase) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void ae2e$blockHealthWrite(float health, CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (!(self instanceof EntityPlayer)) return;
        if (RingProtection.isHealthBypassed((EntityPlayer) self)) return;
        if (health < self.getHealth()
                && RingProtection.isAbsoluteProtectionActive(self)) {
            ci.cancel();
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private void ae2e$blockForcedDeath(DamageSource cause, CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (RingProtection.isAbsoluteProtectionActive(self)) {
            ci.cancel();
            if (self instanceof EntityPlayer && self.getHealth() <= 0.0f) {
                RingProtection.setHealthInternal((EntityPlayer) self, 1.0f);
            }
        }
    }
}
