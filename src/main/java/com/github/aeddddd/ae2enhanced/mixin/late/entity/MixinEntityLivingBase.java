package com.github.aeddddd.ae2enhanced.mixin.late.entity;

import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.util.termination.TerminationRegistry;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 通用禁疗与强制死亡注入：
 * 1. setHealth：终止窗口内阻止任何血量增加（同时兼容旧的禁疗标记）
 * 2. getHealth：仅终止窗口内返回 0，出窗口恢复真实血量
 * 3. onDeath：终止窗口内强制标记 isDead，防止 LivingDeathEvent 被取消后目标继续存活
 */
@Mixin(value = EntityLivingBase.class, remap = true)
public class MixinEntityLivingBase {

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void ae2e$onSetHealth(float health, CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (health > self.getHealth()
                && (OmniToolUpgrades.hasAntiHeal(self) || TerminationRegistry.isTerminating(self))) {
            ci.cancel();
        }
    }

    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void ae2e$onGetHealth(CallbackInfoReturnable<Float> cir) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (TerminationRegistry.isTerminating(self)) {
            cir.setReturnValue(0.0f);
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void ae2e$onDeathHead(DamageSource cause, CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (OmniToolUpgrades.hasAntiHeal(self) || TerminationRegistry.isTerminating(self)) {
            self.setDead();
        }
    }
}
