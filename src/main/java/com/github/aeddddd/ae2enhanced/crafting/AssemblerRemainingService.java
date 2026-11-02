package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.container.ContainerNull;
import appeng.util.Platform;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;

/** 分子装配室执行侧的配方剩余物结算。 */
public final class AssemblerRemainingService {

    private AssemblerRemainingService() {
    }

    public static ItemStack remaining(State state, ICraftingPatternDetails plan,
            InventoryCrafting craftingInventory, ItemStack stack) {
        if (plan == null || !plan.isCraftable() || !(plan instanceof IPatternHelperAccess)) {
            return Platform.getContainerItem(stack);
        }
        try {
            if (state.remaining == null) {
                IRecipe recipe = ((IPatternHelperAccess) plan).ae2enhanced$standardRecipe();
                if (recipe == null) {
                    return Platform.getContainerItem(stack);
                }
                InventoryCrafting copy = new InventoryCrafting(new ContainerNull(), 3, 3);
                for (int i = 0; i < craftingInventory.getSizeInventory() && i < copy.getSizeInventory(); i++) {
                    ItemStack input = craftingInventory.getStackInSlot(i);
                    copy.setInventorySlotContents(i, input.isEmpty() ? ItemStack.EMPTY : input.copy());
                }
                state.remaining = recipe.getRemainingItems(copy);
                state.slot = 0;
            }
            NonNullList<ItemStack> remaining = state.remaining;
            int slot = state.slot++;
            ItemStack result = slot < remaining.size() ? remaining.get(slot) : ItemStack.EMPTY;
            if (state.slot >= craftingInventory.getSizeInventory()) {
                state.remaining = null;
            }
            return result.isEmpty() ? ItemStack.EMPTY : result.copy();
        } catch (Throwable throwable) {
            state.remaining = null;
            return Platform.getContainerItem(stack);
        }
    }

    public static final class State {
        private NonNullList<ItemStack> remaining;
        private int slot;
    }
}
