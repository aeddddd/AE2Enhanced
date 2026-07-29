package com.github.aeddddd.ae2enhanced.integration.jei;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;

/**
 * JEI 插件：注册黑洞合成配方类别与配方显示.
 * <p>移植自 1.12 AE2EnhancedJEIPlugin;1.12 的假物品黑名单 / Omni 终端转移 /
 * 智能样板 ghost 拖拽等功能不在当前移植范围内.</p>
 */
@JeiPlugin
public class AE2EnhancedJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation(AE2Enhanced.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new BlackHoleRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        // 与 BlackHoleCraftingHelper 一致,从配方管理器读取数据包配方
        List<BlackHoleRecipe> recipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.BLACK_HOLE_TYPE.get());
        registration.addRecipes(BlackHoleRecipeCategory.RECIPE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 黑洞由装配枢纽产生,以装配控制器作为催化剂(对该方块按 R/U 可查看黑洞配方)
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.ASSEMBLY_CONTROLLER.get()),
                BlackHoleRecipeCategory.RECIPE_TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // 单方块虚拟合成 CPU 仅通过指令获取,在 JEI 物品列表中隐藏
        jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
                List.of(new ItemStack(ModItems.VIRTUAL_CRAFTING_CPU.get())));
    }
}
