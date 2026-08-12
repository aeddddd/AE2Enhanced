package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import com.github.aeddddd.ae2enhanced.ring.RingProtection;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.NetHandlerPlayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 强制飞行：佩戴飞升指环并开启强制飞行时,每 tick 处理移动包前清零浮空计数,
 * 阻止原版 "Flying is not enabled" 踢出(含 allow-flight=false 服务器).
 * MC 原生类,remap=true 使用 MCP 名.
 */
@Mixin(value = NetHandlerPlayServer.class, remap = true)
public abstract class MixinRingFlightKick {

    @Shadow
    private EntityPlayerMP player;
    @Shadow
    private boolean floating;
    @Shadow
    private int floatingTickCount;
    @Shadow
    private boolean vehicleFloating;
    @Shadow
    private int vehicleFloatingTickCount;

    @Inject(method = "processPlayer", at = @At("HEAD"))
    private void ae2e$resetFloatingForRing(CPacketPlayer packet, CallbackInfo ci) {
        if (this.player == null) return;
        ItemStack ring = com.github.aeddddd.ae2enhanced.ring.RingLocator.findRing(this.player);
        if (!ring.isEmpty() && RingNBT.isForceFlightEnabled(ring)) {
            this.floating = false;
            this.floatingTickCount = 0;
            this.vehicleFloating = false;
            this.vehicleFloatingTickCount = 0;
        }
    }
}
