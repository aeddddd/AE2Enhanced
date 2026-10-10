package com.github.aeddddd.ae2enhanced.mixin.late.dev;

import com.github.aeddddd.ae2enhanced.util.dev.ClickPacketTrace;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketClickWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 点击包取证（服务端侧）：记录每个到达 {@code NetHandlerPlayServer.processClickWindow} 的包字段。
 *
 * <p>仅用于定位"少量点击被放大成百万次槽位操作"的事故（验收文档 M 节）。计数本身有界，
 * 且只有 harness 开启时才会被调用。</p>
 */
@Mixin(value = NetHandlerPlayServer.class, remap = true)
public class MixinNetHandlerPlayServerClickTrace {

    @Inject(method = "processClickWindow", at = @At("HEAD"))
    private void ae2e$traceClickWindow(CPacketClickWindow packet, CallbackInfo ci) {
        if (!com.github.aeddddd.ae2enhanced.util.dev.SplicedTermTestHarness.enabled()) {
            return;
        }
        ClickPacketTrace.serverPacket(packet.getWindowId(), packet.getSlotId(), packet.getUsedButton(),
                String.valueOf(packet.getClickType()), packet.getActionNumber());
    }
}
