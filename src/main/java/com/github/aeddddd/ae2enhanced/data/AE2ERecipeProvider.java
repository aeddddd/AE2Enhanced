package com.github.aeddddd.ae2enhanced.data;

import java.util.function.Consumer;

import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;

/**
 * 配方数据生成器。
 * <p>当前为占位实现，后续可在此添加模组自定义配方。</p>
 */
public class AE2ERecipeProvider extends RecipeProvider {

    public AE2ERecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> consumer) {
        // 占位：后续在此添加 AE2Enhanced 自定义配方
    }
}
