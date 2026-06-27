package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.client.gui.MathExpressionParser;
import appeng.client.gui.implementations.GuiCraftAmount;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.network.packet.PacketCraftRequestLong;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEssentiaSafe;
import com.github.aeddddd.ae2enhanced.util.fakeitem.GasFakeItemChecks;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.Collections;

/**
 * E2a/E2b：合成数量 GUI 增强.
 * - 流体/气体/源质假物品 tooltip 渲染
 * - 下单量 int -> long(E2b)
 */
@Mixin(value = GuiCraftAmount.class, remap = false)
public class MixinGuiCraftAmount {

    @Shadow
    private GuiTextField amountToCraft;

    @Shadow
    private GuiButton next;

    @Inject(method = "func_191948_b", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onRenderTooltip(int mouseX, int mouseY, CallbackInfo ci) {
        GuiContainer screen = (GuiContainer) (Object) this;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.getHasStack()) {
            return;
        }

        ItemStack stack = slot.getStack();
        String tooltip = null;

        if (!com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED && ItemFluidDrop.isFluidDrop(stack)) {
            net.minecraftforge.fluids.FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
            if (fluid != null) {
                tooltip = fluid.getLocalizedName();
            }
        } else if (!com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED && GasFakeItemChecks.isGasFakeItem(stack)) {
            String gasName = GasFakeItemChecks.tryGetGasName(stack);
            if (gasName != null) {
                tooltip = gasName;
            }
        } else if (FakeEssentiaSafe.isEssentiaFakeItem(stack)) {
            String aspectTag = FakeEssentiaSafe.tryGetAspectTag(stack);
            if (aspectTag != null) {
                tooltip = "Essentia: " + aspectTag;
            }
        }

        if (tooltip != null) {
            screen.drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY);
            ci.cancel();
        }
    }

    /**
     * E2b：拦截 Next/Start 按钮,将下单量从 int 提升为 long.
     */
    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onActionPerformed(GuiButton btn, CallbackInfo ci) throws IOException {
        if (btn == this.next) {
            try {
                String out = this.amountToCraft.getText();
                double resultD = MathExpressionParser.parse(out);
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
            double resultD = MathExpressionParser.parse(out);
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
