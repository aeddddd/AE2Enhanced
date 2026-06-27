package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.client.gui.implementations.GuiMEMonitorable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 优化 GuiMEMonitorable.postUpdate()：取消每次收到网络包都立即调用 repo.updateView() 的行为,
 * 改为依赖 func_73876_c() (updateScreen) 中每 tick 的统一调用.
 *
 * 这样在同一 tick 内收到多个 PacketMEInventoryUpdate 时,视图只会重建一次,
 * 而不是每个包都触发一次 O(N) 的 updateView() + O(N log N) 排序.
 *
 * 副作用：UI 刷新最多延迟 1 tick(约 50ms),在终端交互中完全不可感知.
 */
@Mixin(value = GuiMEMonitorable.class, remap = false)
public class MixinGuiMEMonitorable {

    @Inject(
            method = "postUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/client/me/ItemRepo;updateView()V",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void ae2enhanced$skipImmediateViewUpdate(CallbackInfo ci) {
        // 取消 postUpdate() 中剩余的 updateView() + setScrollBar() 调用.
        // updateView() 会在 func_73876_c() 中每 tick 统一调用一次.
        ci.cancel();
    }
}
