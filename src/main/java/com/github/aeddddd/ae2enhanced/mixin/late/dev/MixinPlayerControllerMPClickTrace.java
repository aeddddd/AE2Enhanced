package com.github.aeddddd.ae2enhanced.mixin.late.dev;

import com.github.aeddddd.ae2enhanced.util.dev.ClickPacketTrace;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 点击包取证（客户端侧）：记录每次 {@code PlayerControllerMP.windowClick} 的字段。
 *
 * <p>与驱动注入次数、服务端收包次数对比，即可判定"放大"发生在哪一段。</p>
 */
@Mixin(value = PlayerControllerMP.class, remap = true)
public class MixinPlayerControllerMPClickTrace {

    @Inject(method = "windowClick", at = @At("HEAD"))
    private void ae2e$traceWindowClick(int windowId, int slotId, int mouseButton, ClickType type,
                                       EntityPlayer player, CallbackInfoReturnable<Object> cir) {
        if (!com.github.aeddddd.ae2enhanced.util.dev.SplicedTermTestHarness.enabled()) {
            return;
        }
        ClickPacketTrace.clientCall(windowId, slotId, mouseButton, String.valueOf(type));
    }
}
