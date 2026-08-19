package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.ring.RingNBT;
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
 * 强制飞行：佩戴飞升凭证并开启强制飞行时,每 tick 处理移动包前清零浮空计数,
 * 阻止原版 "Flying is not enabled" 踢出(含 allow-flight=false 服务器).
 * MC 原生类; remap=false + MCP/SRG 双名数组(规避 jar 内 refmap 滞后问题).
 * player 为 public 目标字段,不允许带别名 shadow,直接强转访问(reobf 重命名引用);
 * 四个 private 浮空计数器保留带别名 shadow(仅 private 目标允许别名).
 */
@Mixin(value = NetHandlerPlayServer.class, remap = false)
public abstract class MixinRingFlightKick {

    @Shadow(remap = false, aliases = {"field_184344_B"})
    private boolean floating;
    @Shadow(remap = false, aliases = {"field_147365_f"})
    private int floatingTickCount;
    @Shadow(remap = false, aliases = {"field_184345_D"})
    private boolean vehicleFloating;
    @Shadow(remap = false, aliases = {"field_184346_E"})
    private int vehicleFloatingTickCount;

    @Inject(method = {"processPlayer", "func_147347_a"}, at = @At("HEAD"))
    private void ae2e$resetFloatingForRing(CPacketPlayer packet, CallbackInfo ci) {
        EntityPlayerMP player = ((NetHandlerPlayServer) (Object) this).player;
        if (player == null) return;
        ItemStack ring = com.github.aeddddd.ae2enhanced.ring.RingLocator.findRing(player);
        if (!ring.isEmpty() && RingNBT.isForceFlightEnabled(ring)) {
            this.floating = false;
            this.floatingTickCount = 0;
            this.vehicleFloating = false;
            this.vehicleFloatingTickCount = 0;
        }
    }
}
