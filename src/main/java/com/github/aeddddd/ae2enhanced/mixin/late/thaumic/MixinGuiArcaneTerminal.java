package com.github.aeddddd.ae2enhanced.mixin.late.thaumic;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEssentiaSafe;
import com.github.aeddddd.ae2enhanced.util.fakeitem.GasFakeItemChecks;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumicenergistics.client.gui.part.GuiArcaneTerminal;
import thaumicenergistics.container.slot.SlotME;

import java.util.ArrayList;
import java.util.List;

/**
 * E2a：在奥术终端中渲染流体/气体/源质假物品的 tooltip.
 * 本 mixin 位于 mixins.ae2enhanced.late.thaumic.json 中,条件加载.
 */
@Mixin(value = GuiArcaneTerminal.class, remap = false)
public class MixinGuiArcaneTerminal {

    @Inject(method = "func_191948_b", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onRenderTooltip(int mouseX, int mouseY, CallbackInfo ci) {
        GuiContainer screen = (GuiContainer) (Object) this;
        Slot slot = screen.getSlotUnderMouse();
        if (!(slot instanceof SlotME) || !slot.getHasStack()) {
            return;
        }

        IAEStack aeStack = ((SlotME) slot).getAEStack();
        if (!(aeStack instanceof IAEItemStack)) return;

        ItemStack stack = ((IAEItemStack) aeStack).createItemStack();
        List<String> tooltip = new ArrayList<>();

        if (ItemFluidDrop.isFluidDrop(stack)) {
            net.minecraftforge.fluids.FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
            String name = fluid != null ? fluid.getLocalizedName() : "Unknown Fluid";
            tooltip.add("\u00A7b" + "Fluid: " + name);
            tooltip.add("\u00A77" + "Amount: " + aeStack.getStackSize());
        } else if (GasFakeItemChecks.isGasFakeItem(stack)) {
            String gasName = GasFakeItemChecks.tryGetGasName(stack);
            String name = gasName != null ? gasName : "Unknown Gas";
            tooltip.add("\u00A7b" + "Gas: " + name);
            tooltip.add("\u00A77" + "Amount: " + aeStack.getStackSize());
        } else if (FakeEssentiaSafe.isEssentiaFakeItem(stack)) {
            String aspectTag = FakeEssentiaSafe.tryGetAspectTag(stack);
            String aspectName = aspectTag != null ? aspectTag : "Unknown";
            tooltip.add("\u00A7b" + "Essentia: " + aspectName);
            tooltip.add("\u00A77" + "Amount: " + aeStack.getStackSize());
        } else {
            return;
        }

        screen.drawHoveringText(tooltip, mouseX, mouseY);
        ci.cancel();
    }
}
