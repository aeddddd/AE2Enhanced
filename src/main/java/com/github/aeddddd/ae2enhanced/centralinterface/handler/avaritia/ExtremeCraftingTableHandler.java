package com.github.aeddddd.ae2enhanced.centralinterface.handler.avaritia;

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
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Avaritia 终焉工作台(Extreme Crafting Table, 9x9)虚拟合成处理器.
 *
 * <p>机制与 ExtendedCrafting 工作台 handler 完全一致：始终将样板按<strong>处理样板</strong>
 * 处理——不查原版工作台配方、不信任样板声明的输入/输出、输入成本按 Avaritia 配方每个非空
 * ingredient 槽固定 1 个物品计算，产物以 Avaritia 配方的输出为准。</p>
 *
 * <p>所有 Avaritia 类均通过反射访问({@code morph.avaritia.tile.TileDireCraftingTable} /
 * {@code morph.avaritia.recipe.AvaritiaRecipeManager#EXTREME_RECIPES} /
 * {@code morph.avaritia.recipe.extreme.IExtremeRecipe})，Avaritia 未安装时本类不会被加载。</p>
 */
public class ExtremeCraftingTableHandler implements IVirtualBatchCraftingHandler {

    private static final String[] TABLE_IDS = {
        "avaritia:extreme_crafting_table"
    };

    // 反射缓存
    private static Class<?> CLASS_DIRE_TABLE;
    private static Method METHOD_GET_RECIPE_OUTPUT;
    private static Method METHOD_GET_INGREDIENTS;
    private static Map<?, ?> EXTREME_RECIPES;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_DIRE_TABLE = Class.forName("morph.avaritia.tile.TileDireCraftingTable");
            Class<?> recipeManagerClass = Class.forName("morph.avaritia.recipe.AvaritiaRecipeManager");
            Field recipesField = recipeManagerClass.getField("EXTREME_RECIPES");
            EXTREME_RECIPES = (Map<?, ?>) recipesField.get(null);
            Class<?> extremeRecipeClass = Class.forName("morph.avaritia.recipe.extreme.IExtremeRecipe");
            METHOD_GET_RECIPE_OUTPUT = extremeRecipeClass.getMethod("getRecipeOutput");
            METHOD_GET_INGREDIENTS = extremeRecipeClass.getMethod("getIngredients");
            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ExtremeCraftingTable reflection init failed", e);
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
        return CLASS_DIRE_TABLE.isInstance(te);
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
        Object recipe = findRecipe(world, pos, outputs);
        if (recipe == null) return Collections.emptyList();
        return recipeToCosts(recipe, count);
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients,
                                             IAEItemStack[] outputs, long count, IActionSource source,
                                             ICraftingPatternDetails details) {
        // 成本按 Avaritia 配方计算，但产物必须与样板输出一致，否则 AE2 CPU 的 waitingFor
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

    private static Object findRecipe(World world, BlockPos pos, IAEItemStack[] outputs) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_DIRE_TABLE.isInstance(te)) return null;

        if (outputs == null || outputs.length == 0 || outputs[0] == null) return null;
        ItemStack expectedOutput = outputs[0].createItemStack();
        if (expectedOutput.isEmpty()) return null;

        for (Object recipe : EXTREME_RECIPES.values()) {
            ItemStack recipeOutput = getRecipeOutput(recipe);
            if (outputsMatch(recipeOutput, expectedOutput)) {
                return recipe;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<IAEStack> recipeToCosts(Object recipe, long count) {
        NonNullList<Ingredient> ingredients;
        try {
            ingredients = (NonNullList<Ingredient>) METHOD_GET_INGREDIENTS.invoke(recipe);
        } catch (Exception e) {
            return Collections.emptyList();
        }
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

    private static ItemStack getRecipeOutput(Object recipe) {
        try {
            Object result = METHOD_GET_RECIPE_OUTPUT.invoke(recipe);
            return result instanceof ItemStack ? (ItemStack) result : ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 输出匹配：比较 Item + Metadata + Count + NBT。
     * 若 expected 有 NBT 则必须完全一致，否则仍可能匹配到 NBT 不同的配方。
     */
    private static boolean outputsMatch(ItemStack recipeOutput, ItemStack expected) {
        if (recipeOutput == null || recipeOutput.isEmpty() || expected.isEmpty()) return false;
        if (recipeOutput.getItem() != expected.getItem()) return false;
        if (recipeOutput.getMetadata() != expected.getMetadata()) return false;
        if (recipeOutput.getCount() != expected.getCount()) return false;
        if (expected.hasTagCompound() && !ItemStack.areItemStackTagsEqual(recipeOutput, expected)) {
            return false;
        }
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
