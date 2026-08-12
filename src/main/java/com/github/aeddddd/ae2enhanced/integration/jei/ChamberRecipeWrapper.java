package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 奇点处理仓配方包装.
 * 每个输入组作为一个槽位,组内替代物品由 JEI 自动循环展示.
 */
public class ChamberRecipeWrapper implements IRecipeWrapper {

    private final ChamberRecipe recipe;

    public ChamberRecipeWrapper(ChamberRecipe recipe) {
        this.recipe = recipe;
    }

    public ChamberRecipe getRecipe() {
        return recipe;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<List<ItemStack>> inputGroups = new ArrayList<>();
        for (ChamberRecipe.InputGroup group : recipe.getInputGroups()) {
            List<ItemStack> alternatives = new ArrayList<>();
            for (ItemStack template : group.getTemplates()) {
                ItemStack display = template.copy();
                display.setCount((int) Math.min(Math.max(1, group.getCount()), 64));
                alternatives.add(display);
            }
            if (!alternatives.isEmpty()) {
                inputGroups.add(alternatives);
            }
        }
        ingredients.setInputLists(VanillaTypes.ITEM, inputGroups);
        ingredients.setOutput(VanillaTypes.ITEM, recipe.getOutput());
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        String text = I18n.format("jei.ae2enhanced.chamber.time", recipe.getTimeTicks());
        minecraft.fontRenderer.drawString(text, recipeWidth - minecraft.fontRenderer.getStringWidth(text) - 4,
                recipeHeight - 10, 0xFF808080);
    }
}
