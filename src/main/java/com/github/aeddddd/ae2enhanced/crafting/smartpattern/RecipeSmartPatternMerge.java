package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import appeng.api.implementations.ICraftingPatternItem;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

import javax.annotation.Nonnull;

/**
 * 智能样板合并配方：智能样板 + 1~N 个已编码样板 → 智能样板(追加配方).
 *
 * <p>匹配与结果预览由本类负责;实际的配方追加、文件保存与输出 NBT 更新
 * 在 {@link SmartPatternMergeHelper#handleMerge} 中于合成取出时执行(仅服务端).</p>
 */
public class RecipeSmartPatternMerge extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    @Override
    public boolean matches(@Nonnull InventoryCrafting inv, @Nonnull World worldIn) {
        ItemStack smart = ItemStack.EMPTY;
        int encodedCount = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ItemSmartPattern) {
                if (!smart.isEmpty() || ItemSmartPattern.getPatternDataId(stack) == null) {
                    return false;
                }
                smart = stack;
            } else if (stack.getItem() instanceof ICraftingPatternItem) {
                encodedCount++;
            } else {
                return false;
            }
        }
        return !smart.isEmpty() && encodedCount > 0;
    }

    @Override
    @Nonnull
    public ItemStack getCraftingResult(@Nonnull InventoryCrafting inv) {
        // 预览: 返回智能样板副本; 实际配方追加在取出时由 SmartPatternMergeHelper 完成
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemSmartPattern) {
                ItemStack result = stack.copy();
                result.setCount(1);
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
    @Nonnull
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
