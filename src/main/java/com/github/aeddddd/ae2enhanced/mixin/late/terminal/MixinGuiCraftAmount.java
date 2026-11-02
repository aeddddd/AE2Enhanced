package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.client.gui.MathExpressionParser;
import appeng.client.gui.implementations.GuiCraftAmount;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketCraftRequestLong;
import com.github.aeddddd.ae2enhanced.util.AmountParser;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

/**
 * E2b：合成数量 GUI 增强，下单量 int -> long。
 */
@Mixin(value = GuiCraftAmount.class, remap = false)
public class MixinGuiCraftAmount {

    @Shadow
    private GuiTextField amountToCraft;

    @Shadow
    private GuiButton next;

    /**
     * E2b：拦截 Next/Start 按钮,将下单量从 int 提升为 long.
     */
    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onActionPerformed(GuiButton btn, CallbackInfo ci) throws IOException {
        if (btn == this.next) {
            try {
                String out = this.amountToCraft.getText();
                double resultD = AmountParser.parse(out);
                long result = resultD <= 0.0 || Double.isNaN(resultD) ? 1L : (long) MathExpressionParser.round(resultD, 0);
                AE2Enhanced.network.sendToServer(new PacketCraftRequestLong(result, net.minecraft.client.gui.GuiScreen.isShiftKeyDown()));
                ci.cancel();
            } catch (NumberFormatException e) {
                this.amountToCraft.setText("1");
                ci.cancel();
            }
        }
    }

    /**
     * E2b：让加减按钮支持 long 数量,避免超过 int 范围后溢出.
     */
    @Inject(method = "addQty", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onAddQty(int i, CallbackInfo ci) {
        try {
            String out = this.amountToCraft.getText();
            double resultD = AmountParser.parse(out);
            long result = resultD <= 0.0 || Double.isNaN(resultD) ? 0L : (long) MathExpressionParser.round(resultD, 0);
            if (result == 1 && i > 1) {
                result = 0;
            }
            result += i;
            if (result < 1) {
                result = 1;
            }
            this.amountToCraft.setText(Long.toString(result));
            ci.cancel();
        } catch (NumberFormatException e) {
            // empty catch block, matching original style
        }
    }
}
