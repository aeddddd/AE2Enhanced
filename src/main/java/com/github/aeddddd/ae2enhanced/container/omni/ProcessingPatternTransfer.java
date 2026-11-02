package com.github.aeddddd.ae2enhanced.container.omni;

import java.util.Collection;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

public final class ProcessingPatternTransfer {
    private ProcessingPatternTransfer() {
    }

    public static void replace(IItemHandlerModifiable inputs, IItemHandlerModifiable outputs,
            Map<Integer, ItemStack> newInputs, Collection<ItemStack> newOutputs) {
        clear(inputs);
        clear(outputs);
        for (Map.Entry<Integer, ItemStack> entry : newInputs.entrySet()) {
            int slot = entry.getKey();
            ItemStack stack = entry.getValue();
            if (slot >= 0 && slot < inputs.getSlots() && stack != null && !stack.isEmpty()) {
                inputs.setStackInSlot(slot, stack.copy());
            }
        }
        int slot = 0;
        for (ItemStack stack : newOutputs) {
            if (slot >= outputs.getSlots()) {
                break;
            }
            if (stack != null && !stack.isEmpty()) {
                outputs.setStackInSlot(slot++, stack.copy());
            }
        }
    }

    private static void clear(IItemHandlerModifiable inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }
}
