package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.ring.RingLocator;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 凭证夜视亮度供给(客户端).
 * MixinRingNightVision 让 isPotionActive(NIGHT_VISION) 对凭证夜视返回 true,
 * 但原版 getNightVisionBrightness 内部会取真实药水实例的 getDuration(),
 * 无真实效果时 NPE.此处直接返回满亮度 1.0,跳过原版取值逻辑.
 * MC 原生类; remap=false + MCP/SRG 双名数组(规避 jar 内 refmap 滞后问题).
 */
@Mixin(value = EntityRenderer.class, remap = false)
public class MixinRingNightVisionRenderer {

    @Inject(method = {"getNightVisionBrightness", "func_180438_a"}, at = @At("HEAD"), cancellable = true)
    private void ae2e$ringNightVisionBrightness(EntityLivingBase entity, float partialTicks,
                                                CallbackInfoReturnable<Float> cir) {
        if (!(entity instanceof EntityPlayer)) return;
        ItemStack ring = RingLocator.findRing((EntityPlayer) entity);
        if (!ring.isEmpty() && RingNBT.isNightVisionEnabled(ring)) {
            cir.setReturnValue(1.0F);
        }
    }
}
