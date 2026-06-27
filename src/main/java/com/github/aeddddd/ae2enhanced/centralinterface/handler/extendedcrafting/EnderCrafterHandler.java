package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Arrays;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended Crafting 末影工作台远程处理器.
 *
 * <p>末影工作台为 3×3 合成网格,材料放入后自动开始合成,
 * 需要周围存在 Ender Alternator 才能推进进度.
 * 合成完成后产物留在 result 槽,matrix 中未消耗的物品保留.</p>
 *
 * <p>处理样板策略(参考 ExtendedCraftingTableHandler / AstralSorceryHandler)：
 * AE 处理样板不保证槽位一一对应,因此 {@code canStart} 中通过物品种类+数量
 * 遍历配方列表进行多对多匹配；{@code pushMaterials} 按配方 {@code getIngredients()}
 * 的槽位顺序从可用物品中匹配并精确放置.</p>
 */
public class EnderCrafterHandler implements IRemoteHandler, IVirtualBatchCraftingHandler {

    private static final String BLOCK_ID = "extendedcrafting:ender_crafter";

    private static Class<?> CLASS_TILE_ENDER_CRAFTER;
    private static Class<?> CLASS_ABSTRACT_EXTENDED_TABLE;
    private static Method METHOD_GET_PROGRESS;
    private static Method METHOD_GET_PROGRESS_REQUIRED;
    private static Method METHOD_GET_ALTERNATOR_POSITIONS;
    private static Method METHOD_GET_RESULT;
    private static Method METHOD_SET_RESULT;
    private static Method METHOD_GET_MATRIX;
    private static boolean baseReflectionReady = false;

    private static Class<?> CLASS_RECIPE_MANAGER;
    private static Method METHOD_RECIPE_MANAGER_GET_INSTANCE;
    private static Method METHOD_GET_RECIPES;
    private static boolean recipeReflectionReady = false;

    private static void initBaseReflection() {
        if (baseReflectionReady) return;
        try {
            CLASS_TILE_ENDER_CRAFTER = Class.forName("com.blakebr0.extendedcrafting.tile.TileEnderCrafter");
            CLASS_ABSTRACT_EXTENDED_TABLE = Class.forName("com.blakebr0.extendedcrafting.tile.AbstractExtendedTable");

            METHOD_GET_PROGRESS = CLASS_TILE_ENDER_CRAFTER.getMethod("getProgress");
            METHOD_GET_PROGRESS_REQUIRED = CLASS_TILE_ENDER_CRAFTER.getMethod("getProgressRequired");
            METHOD_GET_ALTERNATOR_POSITIONS = CLASS_TILE_ENDER_CRAFTER.getMethod("getAlternatorPositions");

            METHOD_GET_RESULT = CLASS_ABSTRACT_EXTENDED_TABLE.getMethod("getResult");
            METHOD_SET_RESULT = CLASS_ABSTRACT_EXTENDED_TABLE.getMethod("setResult", ItemStack.class);
            METHOD_GET_MATRIX = CLASS_ABSTRACT_EXTENDED_TABLE.getMethod("getMatrix");

            baseReflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] EnderCrafterHandler base reflection init failed", e);
        }
    }

    private static void initRecipeReflection() {
        if (recipeReflectionReady) return;
        try {
            CLASS_RECIPE_MANAGER = Class.forName("com.blakebr0.extendedcrafting.crafting.endercrafter.EnderCrafterRecipeManager");
            METHOD_RECIPE_MANAGER_GET_INSTANCE = CLASS_RECIPE_MANAGER.getMethod("getInstance");
            METHOD_GET_RECIPES = CLASS_RECIPE_MANAGER.getMethod("getRecipes");
            recipeReflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] EnderCrafterHandler recipe reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return BLOCK_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initBaseReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_TILE_ENDER_CRAFTER.isInstance(te);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initBaseReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return false;

        try {
            // matrix 必须全部为空
            List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);
            for (ItemStack stack : matrix) {
                if (!stack.isEmpty()) return false;
            }

            // result 必须为空
            ItemStack result = (ItemStack) METHOD_GET_RESULT.invoke(te);
            if (!result.isEmpty()) return false;

            // 周围必须有 Ender Alternator,否则 progress 不会增加
            List<BlockPos> alternators = (List<BlockPos>) METHOD_GET_ALTERNATOR_POSITIONS.invoke(te);
            if (alternators == null || alternators.isEmpty()) return false;

        } catch (Exception e) {
            return false;
        }

        // 通过 ingredients 反查配方(不依赖槽位顺序)
        Object recipe = findRecipe(ingredients);
        if (recipe != null) {
            if (session != null) {
                session.setRecipeCache(recipe);
            }
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initBaseReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return false;

        try {
            Object recipe = session != null ? session.getRecipeCache() : null;
            if (recipe == null) {
                recipe = findRecipe(ingredients);
                if (recipe == null) return false;
            }

            List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);

            // 快照原 matrix，放置失败时回滚
            List<ItemStack> snapshot = new ArrayList<>();
            for (ItemStack stack : matrix) {
                snapshot.add(stack.copy());
            }

            // 收集可用物品(保持原始 count)
            List<ItemStack> available = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    available.add(stack.copy());
                }
            }

            // 按配方 getIngredients() 的槽位顺序精确放置
            IRecipe irecipe = (IRecipe) recipe;
            NonNullList<Ingredient> recipeIngredients = irecipe.getIngredients();
            for (int slot = 0; slot < Math.min(recipeIngredients.size(), matrix.size()); slot++) {
                Ingredient ing = recipeIngredients.get(slot);
                if (ing == null || ing == Ingredient.EMPTY) continue;

                for (int i = 0; i < available.size(); i++) {
                    ItemStack stack = available.get(i);
                    if (ing.apply(stack)) {
                        matrix.set(slot, stack.copy());
                        available.remove(i);
                        break;
                    }
                }
            }

            // 所有输入物品必须被放置；否则回滚 matrix 并视为失败
            if (!available.isEmpty()) {
                for (int i = 0; i < matrix.size() && i < snapshot.size(); i++) {
                    matrix.set(i, snapshot.get(i));
                }
                te.markDirty();
                return false;
            }

            te.markDirty();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        if (session != null) {
            session.clearRecipeCache();
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initBaseReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return result;

        try {
            // 收集产物(result 槽)
            ItemStack product = (ItemStack) METHOD_GET_RESULT.invoke(te);
            if (!product.isEmpty()) {
                result.add(product.copy());
                METHOD_SET_RESULT.invoke(te, ItemStack.EMPTY);
            }

            // 收集 matrix 中未消耗的残余物品
            List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);
            for (int i = 0; i < matrix.size(); i++) {
                ItemStack stack = matrix.get(i);
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    matrix.set(i, ItemStack.EMPTY);
                }
            }

            te.markDirty();
        } catch (Exception e) {
            // ignore
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initBaseReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return false;

        try {
            // 有产物可收集：无论 progress 状态如何,只要 result 非空就视为完成
            ItemStack result = (ItemStack) METHOD_GET_RESULT.invoke(te);
            if (!result.isEmpty()) {
                return true;
            }

            int progress = (int) METHOD_GET_PROGRESS.invoke(te);
            if (progress == 0) {
                // 无产物且无进度：检查 matrix 是否也空,避免刚 push 材料后误判为 idle
                List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);
                for (ItemStack stack : matrix) {
                    if (!stack.isEmpty()) return false;
                }
                return true;
            }
        } catch (Exception e) {
            return false;
        }

        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        initBaseReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return result;

        if (session != null) {
            session.clearRecipeCache();
        }

        try {
            // 收集 result 槽
            ItemStack product = (ItemStack) METHOD_GET_RESULT.invoke(te);
            if (!product.isEmpty()) {
                result.add(product.copy());
                METHOD_SET_RESULT.invoke(te, ItemStack.EMPTY);
            }

            // 收集 matrix 中所有物品
            List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);
            for (int i = 0; i < matrix.size(); i++) {
                ItemStack stack = matrix.get(i);
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    matrix.set(i, ItemStack.EMPTY);
                }
            }

            te.markDirty();
        } catch (Exception e) {
            // ignore
        }

        return result;
    }

    // ---- IVirtualCraftingHandler / IVirtualBatchCraftingHandler ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        initBaseReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return false;
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;

        Object recipe = findRecipe(ingredients);
        if (recipe == null) return false;

        // 验证产物是否匹配（忽略 NBT 预览与精确产物的差异，仅校验物品类型）
        IRecipe irecipe = (IRecipe) recipe;
        ItemStack recipeOutput = irecipe.getRecipeOutput();
        if (recipeOutput.isEmpty()) return false;
        ItemStack expected = outputs[0].createItemStack();
        return recipeOutput.getItem() == expected.getItem()
                && recipeOutput.getMetadata() == expected.getMetadata();
    }

    public List<ItemStack> virtualCraft(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, IActionSource source) {
        return virtualCraftBatch(world, pos, ingredients, outputs, 1, source);
    }

    @Override
    public List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return Arrays.asList(
                EnumParticleTypes.PORTAL,
                EnumParticleTypes.DRAGON_BREATH,
                EnumParticleTypes.SPELL_WITCH,
                EnumParticleTypes.END_ROD
        );
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos,
                                         InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                IAEItemStack cost = AEItemStack.fromItemStack(stack.copy());
                cost.setStackSize((long) stack.getCount() * count);
                costs.add(cost);
            }
        }
        return costs;
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos,
                                             InventoryCrafting ingredients, IAEItemStack[] outputs, long count, IActionSource source) {
        List<ItemStack> products = new ArrayList<>();
        if (!canCraftVirtually(world, pos, ingredients, outputs)) return products;
        return scaleOutputsByCount(outputs, count);
    }

    // ---- 配方查找与匹配 ----

    @SuppressWarnings("unchecked")
    private static Object findRecipe(InventoryCrafting ingredients) {
        initRecipeReflection();
        try {
            Object recipeManager = METHOD_RECIPE_MANAGER_GET_INSTANCE.invoke(null);
            List<Object> recipes = (List<Object>) METHOD_GET_RECIPES.invoke(recipeManager);
            if (recipes == null) return null;

            for (Object recipe : recipes) {
                if (ingredientsMatch(recipe, ingredients)) {
                    return recipe;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * 检查 ingredients 中的材料是否满足 recipe 的所有非空 ingredients.
     *
     * <p>关键处理：AE2 处理样板传来的 InventoryCrafting 不保证槽位对应关系,
     * 因此将输入按 count 拆分为单件列表后再与配方的非空 ingredient 进行一对一匹配.</p>
     */
    private static boolean ingredientsMatch(Object recipe, InventoryCrafting ingredients) {
        try {
            IRecipe irecipe = (IRecipe) recipe;
            NonNullList<Ingredient> recipeIngredients = irecipe.getIngredients();
            if (recipeIngredients == null || recipeIngredients.isEmpty()) return false;

            // 收集 recipe 中所有非空 ingredient(每个代表 1 个物品需求)
            List<Ingredient> required = new ArrayList<>();
            for (Ingredient ing : recipeIngredients) {
                if (ing != null && ing != Ingredient.EMPTY) {
                    required.add(ing);
                }
            }

            // 收集输入物品并按 count 拆分为单件
            List<ItemStack> available = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    int count = stack.getCount();
                    for (int c = 0; c < count; c++) {
                        ItemStack single = stack.copy();
                        single.setCount(1);
                        available.add(single);
                    }
                }
            }

            // 数量必须一致
            if (required.size() != available.size()) return false;

            // 贪心匹配
            for (Ingredient ing : required) {
                boolean found = false;
                for (int i = 0; i < available.size(); i++) {
                    if (ing.apply(available.get(i))) {
                        available.remove(i);
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
