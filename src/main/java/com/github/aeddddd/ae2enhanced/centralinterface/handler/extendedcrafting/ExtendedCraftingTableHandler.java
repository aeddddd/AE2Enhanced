package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.recipe.IngredientMatcher;
import com.github.aeddddd.ae2enhanced.centralinterface.recipe.RecipeCosts;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Extended Crafting 工作台虚拟合成处理器.
 *
 * <p>支持 Basic(3x3)、Advanced(5x5)、Elite(7x7)、Ultimate(9x9) 四种工作台。
 * 该 handler 始终将样板按<strong>处理样板</strong>处理：不查原版工作台配方、
 * 不信任样板声明的输入/输出、输入成本按 EC 配方每个非空 ingredient 槽固定 1 个物品计算，
 * 产物以 EC 配方的输出为准。</p>
 */
public class ExtendedCraftingTableHandler implements IVirtualBatchCraftingHandler {

    private static final String[] TABLE_IDS = {
        "extendedcrafting:table_basic",
        "extendedcrafting:table_advanced",
        "extendedcrafting:table_elite",
        "extendedcrafting:table_ultimate"
    };

    // 反射缓存
    private static Class<?> CLASS_ABSTRACT_TABLE;
    private static Method METHOD_GET_LINE_SIZE;
    private static Method METHOD_GET_RECIPES;
    private static Method METHOD_GET_TIER;
    private static Object RECIPE_MANAGER_INSTANCE;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_ABSTRACT_TABLE = Class.forName("com.blakebr0.extendedcrafting.tile.AbstractExtendedTable");
            METHOD_GET_LINE_SIZE = CLASS_ABSTRACT_TABLE.getMethod("getLineSize");
            Class<?> recipeManagerClass = Class.forName("com.blakebr0.extendedcrafting.crafting.table.TableRecipeManager");
            Method getInstance = recipeManagerClass.getMethod("getInstance");
            RECIPE_MANAGER_INSTANCE = getInstance.invoke(null);
            METHOD_GET_RECIPES = recipeManagerClass.getMethod("getRecipes");
            Class<?> tieredRecipeClass = Class.forName("com.blakebr0.extendedcrafting.crafting.table.ITieredRecipe");
            METHOD_GET_TIER = tieredRecipeClass.getMethod("getTier");
            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ExtendedCraftingTable reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        for (String id : TABLE_IDS) {
            if (id.equals(blockId)) return true;
        }
        return false;
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_ABSTRACT_TABLE.isInstance(te);
    }

    @Override
    public EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.virtualOnly();
    }

    @Override
    public long getDefaultParallel() {
        return 8;
    }

    @Override
    public boolean skipCooldownOnSingleBatch() {
        // 如果最终只处理了 1 份（自带并行和虚拟卡均不生效），
        // 表现得像普通物理发配，不进入虚拟冷却，允许 CPU 继续调度。
        return true;
    }

    // ---- IRemoteHandler 物理模式（纯虚拟设备，空实现） ----

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        return true;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source, TargetSession session) {
        return Collections.emptyList();
    }

    // ---- IVirtualBatchCraftingHandler（兼容重载） ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        return canCraftVirtually(world, pos, ingredients, outputs, null);
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients,
                                         IAEItemStack[] outputs, long count) {
        return getVirtualCost(world, pos, ingredients, outputs, count, null);
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients,
                                             IAEItemStack[] outputs, long count, IActionSource source) {
        return virtualCraftBatch(world, pos, ingredients, outputs, count, source, null);
    }

    // ---- IVirtualBatchCraftingHandler（带 patternDetails） ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients,
                                     IAEItemStack[] outputs, ICraftingPatternDetails details) {
        // 要求样板声明的输入真的能构成配方：只检查"配方表里存在同输出的配方"会让
        // "任意输入 + 真产物样板"凭空刷出真产物（成本却按配方计算 —— 用料与产物脱钩）。
        return IngredientMatcher.matches(ingredients, findRecipe(world, pos, outputs));
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients,
                                         IAEItemStack[] outputs, long count, ICraftingPatternDetails details) {
        IRecipe recipe = findRecipe(world, pos, outputs);
        if (recipe == null) return Collections.emptyList();
        return RecipeCosts.fromIngredients(recipe.getIngredients(), count);
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients,
                                             IAEItemStack[] outputs, long count, IActionSource source,
                                             ICraftingPatternDetails details) {
        // 成本按 EC 配方计算，但产物必须与样板输出一致，否则 AE2 CPU 的 waitingFor
        // 无法匹配，导致任务残留一份无法完成。
        return scaleOutputsByCount(outputs, count);
    }

    @Override
    public List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return java.util.Arrays.asList(
                EnumParticleTypes.PORTAL,
                EnumParticleTypes.ENCHANTMENT_TABLE,
                EnumParticleTypes.SPELL_WITCH,
                EnumParticleTypes.END_ROD
        );
    }

    // ---- 配方查找与成本/产物计算 ----

    private static IRecipe findRecipe(World world, BlockPos pos, IAEItemStack[] outputs) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_ABSTRACT_TABLE.isInstance(te)) return null;

        int lineSize = getLineSize(te);
        if (lineSize <= 0) return null;

        if (outputs == null || outputs.length == 0 || outputs[0] == null) return null;
        ItemStack expectedOutput = outputs[0].createItemStack();
        if (expectedOutput.isEmpty()) return null;

        List<IRecipe> recipes = findRecipesByOutput(expectedOutput, lineSize);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<IRecipe> findRecipesByOutput(ItemStack output, int lineSize) {
        List<IRecipe> matches = new ArrayList<>();
        try {
            List<IRecipe> recipes = (List<IRecipe>) METHOD_GET_RECIPES.invoke(RECIPE_MANAGER_INSTANCE);
            if (recipes == null) return matches;

            int gridTier = getTierFromGridSize(lineSize * lineSize);
            for (IRecipe recipe : recipes) {
                int tier = getRecipeTier(recipe);
                if (tier > 0 && tier != gridTier) continue;
                if (RecipeCosts.outputMatches(recipe.getRecipeOutput(), output)) {
                    matches.add(recipe);
                }
            }
        } catch (Exception ignored) {
        }
        return matches;
    }

    // ---- 辅助方法 ----

    private static int getLineSize(TileEntity te) {
        try {
            return (int) METHOD_GET_LINE_SIZE.invoke(te);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int getRecipeTier(IRecipe recipe) {
        try {
            return (int) METHOD_GET_TIER.invoke(recipe);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getTierFromGridSize(int size) {
        if (size < 10) return 1;
        if (size < 26) return 2;
        if (size < 50) return 3;
        return 4;
    }
}
