package com.github.aeddddd.ae2enhanced.mixin.client;

import java.util.OptionalInt;

import net.minecraft.client.gui.screens.Screen;

import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.client.gui.widgets.NumberEntryWidget;

import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.CraftAmountLongPacket;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 突破单次下单 int 上限（客户端部分）.
 * <p>将数量输入框上限从 {@link Integer#MAX_VALUE} 提高到 {@link Long#MAX_VALUE}（9.2E）；
 * 数量超出 int 范围时改走本模组的 {@link CraftAmountLongPacket} 通道,int 范围内保持原生流程.</p>
 */
@Mixin(value = CraftAmountScreen.class, remap = false)
public class MixinCraftAmountScreen {

    @Shadow
    @Final
    private NumberEntryWidget amountToCraft;

    @Redirect(method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lappeng/client/gui/widgets/NumberEntryWidget;setMaxValue(J)V"),
            remap = false)
    private void ae2e$raiseMaxAmount(NumberEntryWidget widget, long maxValue) {
        widget.setMaxValue(Long.MAX_VALUE);
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$confirmLong(CallbackInfo ci) {
        long amount = this.amountToCraft.getLongValue().orElse(0L);
        if (amount <= Integer.MAX_VALUE) {
            return; // int 范围内走原生 int 通道
        }
        boolean craftMissingAmount = this.amountToCraft.startsWithEquals();
        ModNetwork.CHANNEL.sendToServer(
                new CraftAmountLongPacket(amount, craftMissingAmount, Screen.hasShiftDown()));
        ci.cancel();
    }

    /**
     * 原生 updateBeforeRender 用 getIntValue() 判断"下一步"按钮可用性,
     * 数量超过 int 上限时返回 empty 导致按钮被禁用.此处统一改为 long 感知：
     * 超出 int 时返回 1（仅用于 >0 判断）,int 范围内保持原值.
     */
    @Redirect(method = { "updateBeforeRender", "confirm" },
            at = @At(value = "INVOKE",
                    target = "Lappeng/client/gui/widgets/NumberEntryWidget;getIntValue()Ljava/util/OptionalInt;"),
            remap = false)
    private OptionalInt ae2e$longAwareIntValue(NumberEntryWidget widget) {
        var longValue = widget.getLongValue();
        if (longValue.isEmpty()) {
            return OptionalInt.empty();
        }
        long value = longValue.getAsLong();
        return value > Integer.MAX_VALUE ? OptionalInt.of(1) : OptionalInt.of((int) value);
    }
}
