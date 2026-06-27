package com.github.aeddddd.ae2enhanced.integration.jei;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartRecipe;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidHelper;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.ingredients.Ingredients;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JEI/HEI 配方查询助手.
 *
 * <p>通过 JEI 的 RecipeRegistry 查询目标方块对应的所有配方.</p>
 * <p>查询策略：</p>
 * <ol>
 *   <li><b>催化剂匹配</b>：遍历所有 RecipeCategory,检查其催化剂列表中是否包含目标方块</li>
 *   <li><b>IFocus 回退</b>：创建 OUTPUT 焦点,查询与目标方块作为输出相关的类别</li>
 * </ol>
 *
 * <p>注意：JEI 是纯客户端模组,此类必须在客户端调用.</p>
 */
@SideOnly(Side.CLIENT)
public class JEIRecipeHelper {

    /**
     * 检查 JEI/HEI 是否可用.
     */
    public static boolean isJeiAvailable() {
        return JEISearchKeyHandler.getJeiRuntime() != null;
    }

    /**
     * 查询指定方块对应的所有 JEI 配方.
     *
     * @param blockRegistryName 方块 registry name(如 "minecraft:furnace")
     * @return 转换后的 SmartRecipe 列表,若 JEI 不可用或找不到配方则返回空列表
     */
    @Nonnull
    public static List<SmartRecipe> getRecipesForBlock(@Nonnull String blockRegistryName) {
        if (!isJeiAvailable()) {
            return Collections.emptyList();
        }

        IJeiRuntime runtime = JEISearchKeyHandler.getJeiRuntime();
        if (runtime == null) {
            return Collections.emptyList();
        }
        IRecipeRegistry registry = runtime.getRecipeRegistry();

        ItemStack targetStack = getItemStackFromBlockId(blockRegistryName);
        if (targetStack.isEmpty()) {
            AE2Enhanced.LOGGER.warn("[AE2E] Cannot find ItemStack for block: {}", blockRegistryName);
            return Collections.emptyList();
        }

        // 策略1：通过催化剂匹配
        List<IRecipeCategory> categories = findCategoriesByCatalyst(registry, targetStack);
        if (categories.isEmpty()) {
            // 策略2：通过 IFocus OUTPUT 回退匹配
            categories = findCategoriesByFocus(registry, targetStack);
        }

        if (categories.isEmpty()) {
            AE2Enhanced.LOGGER.debug("[AE2E] No JEI categories found for block: {}", blockRegistryName);
            return Collections.emptyList();
        }

        List<SmartRecipe> result = new ArrayList<>();
        for (IRecipeCategory category : categories) {
            try {
                @SuppressWarnings("unchecked")
                List<IRecipeWrapper> wrappers = registry.getRecipeWrappers(category);
                for (IRecipeWrapper wrapper : wrappers) {
                    SmartRecipe recipe = convertWrapper(wrapper, category);
                    if (recipe != null) {
                        result.add(recipe);
                    }
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to get recipes for category: {}", category.getUid(), e);
            }
        }

        // 过载保护：截断
        int max = AE2EnhancedConfig.smartPattern.maxRecipes;
        if (result.size() > max) {
            AE2Enhanced.LOGGER.warn("[AE2E] SmartPattern recipes truncated: {} / {} for {}",
                    result.size(), max, blockRegistryName);
            return result.subList(0, max);
        }

        return result;
    }

    /**
     * 检查目标方块是否在黑名单中.
     */
    public static boolean isBlacklisted(@Nonnull String blockRegistryName) {
        for (String entry : AE2EnhancedConfig.smartPattern.blacklist) {
            if (entry.equalsIgnoreCase(blockRegistryName)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static ItemStack getItemStackFromBlockId(@Nonnull String blockId) {
        String[] parts = blockId.split("@", 2);
        ResourceLocation rl = new ResourceLocation(parts[0]);
        int meta = 0;
        if (parts.length > 1) {
            try {
                meta = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        net.minecraft.block.Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block != null) {
            return new ItemStack(block, 1, meta);
        }
        return ItemStack.EMPTY;
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static List<IRecipeCategory> findCategoriesByCatalyst(
            @Nonnull IRecipeRegistry registry, @Nonnull ItemStack target) {
        List<IRecipeCategory> result = new ArrayList<>();
        for (IRecipeCategory category : registry.getRecipeCategories()) {
            try {
                List<Object> catalysts = registry.getRecipeCatalysts(category);
                if (catalysts != null && containsItemStack(catalysts, target)) {
                    result.add(category);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static List<IRecipeCategory> findCategoriesByFocus(
            @Nonnull IRecipeRegistry registry, @Nonnull ItemStack target) {
        try {
            IFocus<ItemStack> focus = registry.createFocus(IFocus.Mode.OUTPUT, target);
            return registry.getRecipeCategories(focus);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static boolean containsItemStack(@Nonnull List<Object> catalysts, @Nonnull ItemStack target) {
        for (Object catalyst : catalysts) {
            if (catalyst instanceof ItemStack) {
                ItemStack stack = (ItemStack) catalyst;
                if (ItemStack.areItemsEqual(stack, target)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 将 JEI 的 IRecipeWrapper 转换为 SmartRecipe.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static SmartRecipe convertWrapper(@Nonnull IRecipeWrapper wrapper, @Nonnull IRecipeCategory category) {
        try {
            Ingredients ingredients = new Ingredients();
            wrapper.getIngredients(ingredients);

            List<List<ItemStack>> inputLists = ingredients.getInputs(VanillaTypes.ITEM);
            List<List<ItemStack>> outputLists = ingredients.getOutputs(VanillaTypes.ITEM);

            // 收集所有输入(物品 + 流体)
            List<IAEItemStack> inputList = new ArrayList<>();
            for (List<ItemStack> slotInputs : inputLists) {
                if (!slotInputs.isEmpty()) {
                    inputList.add(AEItemStack.fromItemStack(slotInputs.get(0)));
                }
            }
            // ae2fc 流体输入
            if (Ae2fcFluidHelper.isLoaded()) {
                List<List<FluidStack>> fluidInputs = ingredients.getInputs(VanillaTypes.FLUID);
                for (List<FluidStack> slotFluids : fluidInputs) {
                    if (!slotFluids.isEmpty()) {
                        IAEItemStack fluidDrop = Ae2fcFluidHelper.packFluid2AEDrops(slotFluids.get(0));
                        if (fluidDrop != null) {
                            inputList.add(fluidDrop);
                        }
                    }
                }
            }

            // 收集所有输出(物品 + 流体),支持多输出
            List<IAEItemStack> outputList = new ArrayList<>();
            for (List<ItemStack> slotOutputs : outputLists) {
                if (!slotOutputs.isEmpty()) {
                    outputList.add(AEItemStack.fromItemStack(slotOutputs.get(0)));
                }
            }
            // ae2fc 流体输出
            if (Ae2fcFluidHelper.isLoaded()) {
                List<List<FluidStack>> fluidOutputs = ingredients.getOutputs(VanillaTypes.FLUID);
                for (List<FluidStack> slotFluids : fluidOutputs) {
                    if (!slotFluids.isEmpty()) {
                        IAEItemStack fluidDrop = Ae2fcFluidHelper.packFluid2AEDrops(slotFluids.get(0));
                        if (fluidDrop != null) {
                            outputList.add(fluidDrop);
                        }
                    }
                }
            }

            IAEItemStack[] inputs = inputList.toArray(new IAEItemStack[0]);
            IAEItemStack[] outputs = outputList.toArray(new IAEItemStack[0]);

            // 智能样板统一作为 processing 配方处理，不区分 crafting/processing
            return new SmartRecipe(inputs, outputs, false);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to convert JEI recipe wrapper for category: {}",
                    category.getUid(), e);
            return null;
        }
    }
}
