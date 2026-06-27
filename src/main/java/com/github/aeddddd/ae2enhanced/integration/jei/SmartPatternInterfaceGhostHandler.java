package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.client.gui.GuiSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.client.gui.jei.GhostIngredientTarget;
import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.inventory.Slot;

import javax.annotation.Nonnull;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JEI Ghost Ingredient Handler for Smart Pattern Interface MiniGUI.
 * Supports dragging items/fluids/gases/essentia into the 81-slot input area.
 */
public class SmartPatternInterfaceGhostHandler implements IGhostIngredientHandler<GuiSmartPatternInterface> {

    @Nonnull
    @Override
    public <I> List<Target<I>> getTargets(@Nonnull GuiSmartPatternInterface gui, @Nonnull I ingredient, boolean doStart) {
        if (gui.getTile().getLockedRecipeIndex() < 0) {
            return Collections.emptyList();
        }

        boolean isItem = ingredient instanceof net.minecraft.item.ItemStack;
        boolean isFluid = ingredient instanceof net.minecraftforge.fluids.FluidStack;
        boolean isGas = false;
        if (!isItem && !isFluid) {
            try {
                isGas = ingredient.getClass().getName().equals("mekanism.api.gas.GasStack");
            } catch (Exception ignored) {
            }
        }

        if (!isItem && !isFluid && !isGas) {
            return Collections.emptyList();
        }

        ArrayList<Target<I>> targets = new ArrayList<>();
        int scrollOffset = gui.getTile().getMiniGuiScrollOffset();
        int slotStart = ContainerSmartPatternInterface.SLOT_MINIGUI_INPUT_START + scrollOffset * 9;
        int slotEnd = slotStart + 9;

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            int slotNumber = slot.slotNumber;
            if (slotNumber >= slotStart && slotNumber < slotEnd) {
                if (slot instanceof appeng.container.slot.SlotFake) {
                    Target<I> target = new GhostIngredientTargetWrapper<>(gui.getGuiLeft(), gui.getGuiTop(), slot);
                    targets.add(target);
                }
            }
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

    /**
     * Wrapper around GhostIngredientTarget that implements JEI's generic Target interface.
     */
    private static class GhostIngredientTargetWrapper<I> implements Target<I> {
        private final int guiLeft;
        private final int guiTop;
        private final Slot slot;

        GhostIngredientTargetWrapper(int guiLeft, int guiTop, Slot slot) {
            this.guiLeft = guiLeft;
            this.guiTop = guiTop;
            this.slot = slot;
        }

        @Nonnull
        @Override
        public Rectangle getArea() {
            return new Rectangle(this.guiLeft + this.slot.xPos, this.guiTop + this.slot.yPos, 16, 16);
        }

        @Override
        public void accept(@Nonnull I ingredient) {
            appeng.api.storage.data.IAEItemStack aeStack = GhostIngredientTarget.resolveIngredient(ingredient);
            if (aeStack == null) return;
            try {
                appeng.core.sync.network.NetworkHandler.instance().sendToServer(
                    new appeng.core.sync.packets.PacketInventoryAction(
                        appeng.helpers.InventoryAction.PLACE_JEI_GHOST_ITEM,
                        (appeng.container.slot.SlotFake) this.slot,
                        aeStack
                    )
                );
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }
}
