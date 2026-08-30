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
 * 强制飞行：佩戴飞升凭证并开启强制飞行时,每 tick 处理移动包前清零浮空标记,
 * 阻止原版 "Flying is not enabled" 踢出(含 allow-flight=false 服务器).
 * MC 原生类; remap=false + MCP/SRG 双名数组(规避 jar 内 refmap 滞后问题).
 * player 为 public 目标字段,不允许带别名 shadow,直接强转访问(reobf 重命名引用).
 *
 * <p>仅 shadow floating/vehicleFloating 两个标记位:计数器(floatingTickCount 等)
 * 在部分整合包中被 AT 改为非 private,带别名 shadow 会触发
 * "Non-private field cannot be aliased" 导致整个 mixin 被拒;且计数器仅在
 * floating==true 时自增,原版 else 分支也会自行清零,无需我们重置.</p>
 */
@Mixin(value = NetHandlerPlayServer.class, remap = false)
public abstract class MixinRingFlightKick {

    @Shadow(remap = false, aliases = {"field_184344_B"})
    private boolean floating;
    @Shadow(remap = false, aliases = {"field_184345_D"})
    private boolean vehicleFloating;

    @Inject(method = {"processPlayer", "func_147347_a"}, at = @At("HEAD"))
    private void ae2e$resetFloatingForRing(CPacketPlayer packet, CallbackInfo ci) {
        EntityPlayerMP player = ((NetHandlerPlayServer) (Object) this).player;
        if (player == null) return;
        ItemStack ring = com.github.aeddddd.ae2enhanced.ring.RingLocator.findRing(player);
        if (!ring.isEmpty() && RingNBT.isForceFlightEnabled(ring)) {
            this.floating = false;
            this.vehicleFloating = false;
        }
    }
}
