package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientBlacklist;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 插件：注册黑洞合成配方类别与配方显示.
 */
@JEIPlugin
public class AE2EnhancedJEIPlugin implements IModPlugin {

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JEISearchKeyHandler.setJeiRuntime(jeiRuntime);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        registry.addRecipeCategories(new BlackHoleRecipeCategory(registry.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void register(IModRegistry registry) {
        IIngredientRegistry ingredientRegistry = registry.getIngredientRegistry();
        IIngredientBlacklist blacklist = registry.getJeiHelpers().getIngredientBlacklist();

        // E2a：将假物品加入 JEI 黑名单,避免在物品列表中显示
        if (ItemRegistry.ESSENTIA_DROP != null) {
            for (ItemStack stack : ItemEssentiaDrop.getAllAspectStacks()) {
                blacklist.addIngredientToBlacklist(stack);
            }
        }
        if (ItemRegistry.FLUID_DROP != null) {
            // 隐藏基础流体假物品(getSubItems 已返回空,黑名单确保基础物品也不显示)
            blacklist.addIngredientToBlacklist(new ItemStack(ItemRegistry.FLUID_DROP));

        }
        if (ItemRegistry.GAS_DROP != null) {
            blacklist.addIngredientToBlacklist(new ItemStack(ItemRegistry.GAS_DROP));
        }

        // 必须将 BlackHoleRecipe 包装为 BlackHoleRecipeWrapper,与 IRecipeCategory 的泛型匹配
        List<BlackHoleRecipeWrapper> wrappers = new ArrayList<>();
        for (BlackHoleRecipe recipe : BlackHoleRecipeRegistry.getRecipes()) {
            wrappers.add(new BlackHoleRecipeWrapper(recipe));
        }
        registry.addRecipes(wrappers, BlackHoleRecipeCategory.UID);

        // Omni Terminal 配方转移：使用 universal handler 支持所有 recipe category
        registry.getRecipeTransferRegistry().addUniversalRecipeTransferHandler(
                new com.github.aeddddd.ae2enhanced.integration.jei.OmniTermRecipeTransferHandler());

        // Smart Pattern Interface MiniGUI ghost ingredient drag support
        registry.addGhostIngredientHandler(
                com.github.aeddddd.ae2enhanced.client.gui.GuiSmartPatternInterface.class,
                new com.github.aeddddd.ae2enhanced.integration.jei.SmartPatternInterfaceGhostHandler());
    }
}
