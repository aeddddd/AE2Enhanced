package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.ring.RingManager;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 飞升指环永久饱食：拦截饥饿消耗累积(覆盖其他模组的强制饥饿效果).
 * 能量状态由服务端 tick 管理器维护,能量不足时不拦截.
 * MC 原生类,remap=true.
 */
@Mixin(value = EntityPlayer.class, remap = true)
public class MixinRingExhaustion {

    @Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
    private void ae2e$blockExhaustion(float exhaustion, CallbackInfo ci) {
        EntityPlayer self = (EntityPlayer) (Object) this;
        if (!self.world.isRemote && RingManager.isSaturationActive(self)) {
            ci.cancel();
        }
    }
}
