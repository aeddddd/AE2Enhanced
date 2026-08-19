package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.ring.RingManager;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 飞升凭证永久饱食：拦截饥饿消耗累积(覆盖其他模组的强制饥饿效果).
 * 能量状态由服务端 tick 管理器维护,能量不足时不拦截.
 * MC 原生类; remap=false + MCP/SRG 双名数组(规避 jar 内 refmap 滞后问题).
 */
@Mixin(value = EntityPlayer.class, remap = false)
public class MixinRingExhaustion {

    @Inject(method = {"addExhaustion", "func_71020_j"}, at = @At("HEAD"), cancellable = true)
    private void ae2e$blockExhaustion(float exhaustion, CallbackInfo ci) {
        EntityPlayer self = (EntityPlayer) (Object) this;
        if (!self.world.isRemote && RingManager.isSaturationActive(self)) {
            ci.cancel();
        }
    }
}
