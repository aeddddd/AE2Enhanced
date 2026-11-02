package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.container.ContainerNull;
import appeng.util.Platform;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;

/** 按实际配方剩余物生成 CPU 的 waitingFor 预期。 */
public final class RemainingItemsService {

    private RemainingItemsService() {
    }

    public static void beginPush(CraftingCpuExecutionContext context, ICraftingPatternDetails details,
            InventoryCrafting inventory) {
        context.beginPush(details, inventory);
    }

    public static ItemStack expected(CraftingCpuExecutionContext context, ItemStack stack) {
        ICraftingPatternDetails details = context.expectedDetails();
        InventoryCrafting inventory = context.expectedInventory();
        if (details == null || inventory == null || !(details instanceof IPatternHelperAccess)) {
            return Platform.getContainerItem(stack);
        }
        try {
            if (context.expectedRemaining() == null) {
                IRecipe recipe = ((IPatternHelperAccess) details).ae2enhanced$standardRecipe();
                if (recipe == null) {
                    return Platform.getContainerItem(stack);
                }
                InventoryCrafting copy = new InventoryCrafting(new ContainerNull(), 3, 3);
                for (int i = 0; i < inventory.getSizeInventory() && i < copy.getSizeInventory(); i++) {
                    ItemStack input = inventory.getStackInSlot(i);
                    copy.setInventorySlotContents(i, input.isEmpty() ? ItemStack.EMPTY : input.copy());
                }
                context.expectedRemaining(recipe.getRemainingItems(copy));
            }
            NonNullList<ItemStack> remaining = context.expectedRemaining();
            int slot = context.nextExpectedSlot();
            ItemStack result = slot < remaining.size() ? remaining.get(slot) : ItemStack.EMPTY;
            if (context.expectedSlot() >= inventory.getSizeInventory()) {
                context.clearExpectedRemaining();
            }
            return result.isEmpty() ? ItemStack.EMPTY : result.copy();
        } catch (Throwable throwable) {
            context.clearExpectedRemaining();
            return Platform.getContainerItem(stack);
        }
    }
}
