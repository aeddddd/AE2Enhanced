package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // ---- IVirtualBatchCraftingHandler（旧签名兼容） ----

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
        return findRecipe(world, pos, outputs) != null;
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients,
                                         IAEItemStack[] outputs, long count, ICraftingPatternDetails details) {
        IRecipe recipe = findRecipe(world, pos, outputs);
        if (recipe == null) return Collections.emptyList();
        return recipeToCosts(recipe, count);
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
                if (outputsMatch(recipe.getRecipeOutput(), output)) {
                    matches.add(recipe);
                }
            }
        } catch (Exception ignored) {
        }
        return matches;
    }

    private static List<IAEStack> recipeToCosts(IRecipe recipe, long count) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients == null) return Collections.emptyList();

        Map<ItemKey, Long> merged = new HashMap<>();
        for (Ingredient ing : ingredients) {
            if (ing == null || ing == Ingredient.EMPTY) continue;
            ItemStack[] matching = ing.getMatchingStacks();
            if (matching == null || matching.length == 0) continue;
            for (ItemStack stack : matching) {
                if (stack != null && !stack.isEmpty()) {
                    merged.merge(new ItemKey(stack), count, Long::sum);
                    break;
                }
            }
        }

        List<IAEStack> costs = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> entry : merged.entrySet()) {
            IAEItemStack cost = AEItemStack.fromItemStack(entry.getKey().representative.copy());
            if (cost != null) {
                cost.setStackSize(entry.getValue());
                costs.add(cost);
            }
        }
        return costs;
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

    /**
     * 输出匹配：忽略 NBT，比较 Item + Metadata + Count。
     */
    private static boolean outputsMatch(ItemStack recipeOutput, ItemStack expected) {
        if (recipeOutput.isEmpty() || expected.isEmpty()) return false;
        if (recipeOutput.getItem() != expected.getItem()) return false;
        if (recipeOutput.getMetadata() != expected.getMetadata()) return false;
        if (recipeOutput.getCount() != expected.getCount()) return false;
        return true;
    }

    private static final class ItemKey {
        final net.minecraft.item.Item item;
        final int meta;
        final net.minecraft.nbt.NBTTagCompound nbt;
        final ItemStack representative;

        ItemKey(ItemStack stack) {
            this.item = stack.getItem();
            this.meta = stack.getMetadata();
            this.nbt = stack.hasTagCompound() ? stack.getTagCompound().copy() : null;
            this.representative = stack.copy();
            this.representative.setCount(1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey other = (ItemKey) o;
            return this.item == other.item && this.meta == other.meta &&
                    (this.nbt == null ? other.nbt == null : this.nbt.equals(other.nbt));
        }

        @Override
        public int hashCode() {
            int result = item.hashCode();
            result = 31 * result + meta;
            result = 31 * result + (nbt != null ? nbt.hashCode() : 0);
            return result;
        }
    }
}
