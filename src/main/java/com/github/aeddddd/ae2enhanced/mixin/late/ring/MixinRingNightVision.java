package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import com.github.aeddddd.ae2enhanced.ring.RingLocator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 凭证夜视(非药水形式)：isPotionActive(NIGHT_VISION) 对佩戴凭证并开启夜视的玩家返回 true,
 * 复用原版夜视渲染管线,但不进入药水列表(无 HUD 图标/无粒子/不可被牛奶清除).
 * getActivePotionEffect 同步返回虚拟实例(新建,不缓存不共享,不进效果 map):
 * 原版渲染与其他模组(如血魔法)存在 "isPotionActive 后直接取实例 getDuration" 的
 * 惯用模式,返回 null 会 NPE.
 * MC 原生类; remap=false + MCP/SRG 双名数组(规避 jar 内 refmap 滞后问题).
 */
@Mixin(value = EntityLivingBase.class, remap = false)
public class MixinRingNightVision {

    @Inject(method = {"isPotionActive", "func_70644_a"}, at = @At("HEAD"), cancellable = true)
    private void ae2e$ringNightVision(Potion potion, CallbackInfoReturnable<Boolean> cir) {
        if (potion != MobEffects.NIGHT_VISION) return;
        if (ae2e$hasRingNightVision((EntityLivingBase) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = {"getActivePotionEffect", "func_70660_b"}, at = @At("HEAD"), cancellable = true)
    private void ae2e$ringNightVisionEffect(Potion potion, CallbackInfoReturnable<PotionEffect> cir) {
        if (potion != MobEffects.NIGHT_VISION) return;
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        // 真实效果优先(不干预);仅当效果 map 中不存在且凭证夜视开启时提供虚拟实例
        if (self.getActivePotionMap().get(potion) != null) return;
        if (ae2e$hasRingNightVision(self)) {
            cir.setReturnValue(new PotionEffect(MobEffects.NIGHT_VISION, 6000, 0, true, false));
        }
    }

    private static boolean ae2e$hasRingNightVision(EntityLivingBase self) {
        if (!(self instanceof EntityPlayer)) return false;
        ItemStack ring = RingLocator.findRing((EntityPlayer) self);
        return !ring.isEmpty() && RingNBT.isNightVisionEnabled(ring);
    }
}
