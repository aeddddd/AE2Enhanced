package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.client.gui.implementations.GuiCellWorkbench;
import appeng.container.slot.SlotFake;
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

import java.util.Collections;

/**
 * E2a：在 Cell Workbench 中渲染流体/气体/源质假物品的 tooltip.
 */
@Mixin(value = GuiCellWorkbench.class, remap = false)
public class MixinGuiCellWorkbench {

    @Inject(method = "func_191948_b", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onRenderTooltip(int mouseX, int mouseY, CallbackInfo ci) {
        GuiContainer screen = (GuiContainer) (Object) this;
        Slot slot = screen.getSlotUnderMouse();
        if (!(slot instanceof SlotFake) || !slot.getHasStack()) {
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
}
