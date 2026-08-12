package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import com.github.aeddddd.ae2enhanced.util.compat.HeiCompat;
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

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("AE2Enhanced-JEI");

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JEISearchKeyHandler.setJeiRuntime(jeiRuntime);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        registry.addRecipeCategories(new BlackHoleRecipeCategory(registry.getJeiHelpers().getGuiHelper()));
        registry.addRecipeCategories(new ChamberRecipeCategory(registry.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void register(IModRegistry registry) {
        registerSlotIngredientProvider(registry);

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
        // 指南书为测试物品：JEI 隐藏
        if (ItemRegistry.GUIDE_BOOK != null) {
            blacklist.addIngredientToBlacklist(new ItemStack(ItemRegistry.GUIDE_BOOK));
        }

        // 必须将 BlackHoleRecipe 包装为 BlackHoleRecipeWrapper,与 IRecipeCategory 的泛型匹配
        List<BlackHoleRecipeWrapper> wrappers = new ArrayList<>();
        for (BlackHoleRecipe recipe : BlackHoleRecipeRegistry.getRecipes()) {
            wrappers.add(new BlackHoleRecipeWrapper(recipe));
        }
        registry.addRecipes(wrappers, BlackHoleRecipeCategory.UID);

        // 奇点处理仓配方
        List<ChamberRecipeWrapper> chamberWrappers = new ArrayList<>();
        for (com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe recipe
                : com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex.allRecipes()) {
            chamberWrappers.add(new ChamberRecipeWrapper(recipe));
        }
        registry.addRecipes(chamberWrappers, ChamberRecipeCategory.UID);

        // 处理仓配方催化剂：点击方块可查看其配方
        registry.addRecipeCatalyst(new ItemStack(BlockRegistry.SINGULARITY_CHAMBER), ChamberRecipeCategory.UID);

        // Omni Terminal 配方转移：使用 universal handler 支持所有 recipe category
        registry.getRecipeTransferRegistry().addUniversalRecipeTransferHandler(
                new com.github.aeddddd.ae2enhanced.integration.jei.OmniTermRecipeTransferHandler());

        // Smart Pattern Interface MiniGUI ghost ingredient drag support
        registry.addGhostIngredientHandler(
                com.github.aeddddd.ae2enhanced.client.gui.GuiSmartPatternInterface.class,
                new com.github.aeddddd.ae2enhanced.integration.jei.SmartPatternInterfaceGhostHandler());

        // Smart Pattern Interface 一键转移：将 JEI 配方填充到锁定的配方
        registry.getRecipeTransferRegistry().addUniversalRecipeTransferHandler(
                new com.github.aeddddd.ae2enhanced.integration.jei.SmartPatternRecipeTransferHandler(
                        registry.getJeiHelpers().recipeTransferHandlerHelper()));
    }

    /**
     * 终端假物品成分识别（E2a）的版本适配入口.
     * HEI ≥ 4.34.0：通过官方 ISlotIngredientProvider API 注册.
     * HEI ≤ 4.33.x：回退到 MixinGuiContainerWrapper（旧版兼容,计划移除）.
     */
    private static void registerSlotIngredientProvider(IModRegistry registry) {
        if (HeiCompat.HAS_SLOT_INGREDIENT_PROVIDER) {
            try {
                SlotIngredientProviderSupport.register(registry);
            } catch (Throwable t) {
                LOGGER.error("[AE2E] 注册 HEI ISlotIngredientProvider 失败,终端假物品的 R/U 查询将不可用", t);
            }
        } else {
            LOGGER.warn("[AE2E] 检测到旧版 HEI/JEI（缺少 ISlotIngredientProvider API,HEI < 4.34.0）.");
            LOGGER.warn("[AE2E] 已回退到 Mixin 方式实现终端假物品识别.旧版兼容将在未来几个版本中移除,请升级 HEI 到 4.34.0 或更高版本.");
        }
    }
}
