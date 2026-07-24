package com.github.aeddddd.ae2enhanced.data;

import java.util.function.Consumer;

import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;

import appeng.core.definitions.AEItems;

import com.github.aeddddd.ae2enhanced.registry.ModItems;

/**
 * 配方数据生成器.
 */
public class AE2ERecipeProvider extends RecipeProvider implements IConditionBuilder {

    public AE2ERecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> consumer) {
        // 个人维度核心：末影珍珠 + 钻石环绕下界之星
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.PERSONAL_DIMENSION.get())
                .pattern("EDE")
                .pattern("DND")
                .pattern("EDE")
                .define('E', Items.ENDER_PEARL)
                .define('D', Items.DIAMOND)
                .define('N', Items.NETHER_STAR)
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                .save(consumer);

        // 个人维度管理器：黑曜石与末影珍珠环绕钻石
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModItems.PERSONAL_DIMENSION_MANAGER.get())
                .pattern("OEO")
                .pattern("EDE")
                .pattern("OEO")
                .define('O', Items.OBSIDIAN)
                .define('E', Items.ENDER_PEARL)
                .define('D', Items.DIAMOND)
                .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
                .save(consumer);

        // 先进 ME 全能工具：熵变机械臂 + 充能手杖 + 稳定时空流形 + 网络工具 + 内存卡
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.ME_OMNI_TOOL.get())
                .pattern(" E ")
                .pattern("CSN")
                .pattern(" M ")
                .define('E', AEItems.ENTROPY_MANIPULATOR)
                .define('C', AEItems.CHARGED_STAFF)
                .define('S', ModItems.STABLE_SPACETIME_MANIFOLD.get())
                .define('N', AEItems.NETWORK_TOOL)
                .define('M', AEItems.MEMORY_CARD)
                .unlockedBy("has_entropy_manipulator", has(AEItems.ENTROPY_MANIPULATOR))
                .save(consumer);
    }
}
