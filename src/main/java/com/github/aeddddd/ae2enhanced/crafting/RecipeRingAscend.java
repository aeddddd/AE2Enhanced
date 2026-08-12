package com.github.aeddddd.ae2enhanced.crafting;

import com.github.aeddddd.ae2enhanced.item.ItemConstrainedMicroSingularity;
import com.github.aeddddd.ae2enhanced.item.ItemNetworkLinkCredential;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * 指环飞升配方：指环 + 1 个无限时间被约束微型奇点 → 飞升进度 +1.
 * 重复 16 次后指环飞升.必须逐个合成,每次消耗一个永久奇点.
 */
public class RecipeRingAscend extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    public RecipeRingAscend(ResourceLocation name) {
        setRegistryName(name);
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        ItemStack ring = ItemStack.EMPTY;
        ItemStack singularity = ItemStack.EMPTY;
        int nonEmpty = 0;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            nonEmpty++;
            if (stack.getItem() instanceof ItemNetworkLinkCredential) {
                if (!ring.isEmpty()) return false;
                ring = stack;
            } else if (stack.getItem() instanceof ItemConstrainedMicroSingularity
                    && ItemConstrainedMicroSingularity.isPermanent(stack)) {
                if (!singularity.isEmpty()) return false;
                singularity = stack;
            } else {
                return false;
            }
        }

        if (nonEmpty != 2 || ring.isEmpty() || singularity.isEmpty()) {
            return false;
        }
        // 已飞升的指环不可继续合成；飞升仪式要求指环已达到阶段 III
        return !RingNBT.isAscended(ring) && RingNBT.getTier(ring) >= 2;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.getItem() instanceof ItemNetworkLinkCredential) {
                ItemStack result = stack.copy();
                result.setCount(1);
                int progress = RingNBT.getAscendProgress(result) + 1;
                RingNBT.setAscendProgress(result, progress);
                if (progress >= RingNBT.MAX_ASCEND) {
                    RingNBT.setAscended(result, true);
                }
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
