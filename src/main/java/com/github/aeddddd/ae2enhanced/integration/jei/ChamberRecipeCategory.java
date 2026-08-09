package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * JEI 奇点处理仓配方类别.
 */
public class ChamberRecipeCategory implements IRecipeCategory<ChamberRecipeWrapper> {

    public static final String UID = AE2Enhanced.MOD_ID + ".chamber";

    private final IDrawable background;
    private final IDrawable icon;
    private final String localizedName;

    public ChamberRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(150, 56);
        this.icon = guiHelper.createDrawableIngredient(new ItemStack(BlockRegistry.SINGULARITY_CHAMBER));
        this.localizedName = I18n.format("jei.ae2enhanced.category.chamber");
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return localizedName;
    }

    @Override
    public String getModName() {
        return AE2Enhanced.MOD_NAME;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Nullable
    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, ChamberRecipeWrapper recipeWrapper, IIngredients ingredients) {
        IGuiItemStackGroup stacks = recipeLayout.getItemStacks();

        // 输入：左侧 3×2 网格
        int inputIndex = 0;
        for (List<ItemStack> inputs : ingredients.getInputs(ItemStack.class)) {
            if (inputs.isEmpty() || inputIndex >= 6) {
                break;
            }
            int x = 8 + (inputIndex % 3) * 18;
            int y = 8 + (inputIndex / 3) * 18;
            stacks.init(inputIndex, true, x, y);
            stacks.set(inputIndex, inputs.get(0));
            inputIndex++;
        }

        // 输出：右侧
        stacks.init(9, false, 116, 17);
        stacks.set(9, ingredients.getOutputs(ItemStack.class).get(0).get(0));
    }
}
