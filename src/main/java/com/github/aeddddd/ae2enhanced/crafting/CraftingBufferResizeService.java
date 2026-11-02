package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.ContainerNull;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

/** 处理样板合成表扩容，并保留 ae2fc 的运行时子类。 */
public final class CraftingBufferResizeService {

    private CraftingBufferResizeService() {
    }

    public static InventoryCrafting resize(InventoryCrafting original, Container container,
            ICraftingPatternDetails details) {
        if (details == null || details.isCraftable()) {
            return original;
        }
        IAEItemStack[] inputs = details.getInputs();
        if (inputs == null || inputs.length <= original.getSizeInventory()) {
            return original;
        }
        int size = Math.min(10, Math.max(4, (int) Math.ceil(Math.sqrt(inputs.length))));
        InventoryCrafting larger = newSameClassTable(original, container, size);
        for (int i = 0; i < original.getSizeInventory(); i++) {
            ItemStack stack = original.getStackInSlot(i);
            if (!stack.isEmpty()) {
                larger.setInventorySlotContents(i, stack.copy());
            }
        }
        return larger;
    }

    private static InventoryCrafting newSameClassTable(InventoryCrafting original, Container container, int size) {
        try {
            return original.getClass().getConstructor(Container.class, int.class, int.class)
                    .newInstance(container, size, size);
        } catch (Throwable ignored) {
            return new InventoryCrafting(new ContainerNull(), size, size);
        }
    }
}
