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
import java.util.Map;

/**
 * JEI 奇点处理仓配方包装.
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
        List<ItemStack> inputs = new ArrayList<>();
        for (Map.Entry<String, Long> entry : recipe.getInputs().entrySet()) {
            ItemStack template = recipe.getInputTemplates().get(entry.getKey());
            if (template == null || template.isEmpty()) {
                continue;
            }
            ItemStack display = template.copy();
            display.setCount((int) Math.min(entry.getValue(), Integer.MAX_VALUE));
            inputs.add(display);
        }
        ingredients.setInputs(VanillaTypes.ITEM, inputs);
        ingredients.setOutput(VanillaTypes.ITEM, recipe.getOutput());
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        String text = I18n.format("jei.ae2enhanced.chamber.time", recipe.getTimeTicks());
        minecraft.fontRenderer.drawString(text, (recipeWidth - minecraft.fontRenderer.getStringWidth(text)) / 2,
                recipeHeight - 10, 0xFF888888);
    }
}
