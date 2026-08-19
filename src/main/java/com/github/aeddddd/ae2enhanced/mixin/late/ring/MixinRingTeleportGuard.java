package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.ring.RingProtection;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 飞升凭证免疫强制位移：拦截服务端强制位置同步(其他模组拉扯/传送的主要路径).
 * 本 mod 自身的合法位移(Blink/穿墙自救/个人维度传送)通过
 * {@link RingProtection#allowTeleport} 白名单放行.
 * MC 原生类; remap=false + MCP/SRG 双名数组(规避 jar 内 refmap 滞后问题).
 */
@Mixin(value = EntityPlayerMP.class, remap = false)
public class MixinRingTeleportGuard {

    @Inject(method = {"setPositionAndUpdate", "func_70634_a"}, at = @At("HEAD"), cancellable = true)
    private void ae2e$guardForcedTeleport(double x, double y, double z, CallbackInfo ci) {
        EntityPlayerMP self = (EntityPlayerMP) (Object) this;
        if (RingProtection.isPullProtectionEnabled(self) && !RingProtection.isTeleportAllowed(self)) {
            ci.cancel();
        }
    }
}
