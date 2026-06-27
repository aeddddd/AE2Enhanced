package com.github.aeddddd.ae2enhanced.mixin.late.entity;

import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 通用禁疗与强制死亡注入：
 * 1. setHealth：阻止任何血量增加
 * 2. getHealth：返回 0（让所有系统认为目标已死）
 * 3. onDeath：强制标记 dead=true，防止 LivingDeathEvent 被取消后目标继续存活
 */
@Mixin(value = EntityLivingBase.class, remap = true)
public class MixinEntityLivingBase {

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void ae2e$onSetHealth(float health, CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (health > self.getHealth() && OmniToolUpgrades.hasAntiHeal(self)) {
            ci.cancel();
        }
    }

    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void ae2e$onGetHealth(CallbackInfoReturnable<Float> cir) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (OmniToolUpgrades.hasAntiHeal(self)) {
            cir.setReturnValue(0.0f);
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void ae2e$onDeathHead(DamageSource cause, CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (OmniToolUpgrades.hasAntiHeal(self)) {
            self.setDead();
        }
    }
}
