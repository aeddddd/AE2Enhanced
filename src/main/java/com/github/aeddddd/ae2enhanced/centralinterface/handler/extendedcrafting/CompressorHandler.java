package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extended Crafting 量子压缩机处理器.
 *
 * <p>突破压缩机输入限制,直接将所需全部材料注入内部,
 * 绕过 {@code inputLimit} 和逐次堆叠限制.</p>
 */
public class CompressorHandler implements IRemoteHandler, IVirtualBatchCraftingHandler {

    private static final String BLOCK_ID = "extendedcrafting:compressor";

    // 反射缓存
    private static Class<?> CLASS_TILE_COMPRESSOR;
    private static Class<?> CLASS_COMPRESSOR_RECIPE;
    private static Class<?> CLASS_RECIPE_MANAGER;
    private static Object RECIPE_MANAGER_INSTANCE;
    private static Method METHOD_GET_RECIPES;

    private static Method METHOD_GET_INPUT;
    private static Method METHOD_GET_OUTPUT;
    private static Method METHOD_GET_INPUT_COUNT;
    private static Method METHOD_GET_CATALYST;
    private static Method METHOD_CONSUME_CATALYST;
    private static Method METHOD_GET_POWER_COST;
    private static Field FIELD_MATERIAL_STACK;
    private static Field FIELD_MATERIAL_COUNT;
    private static Field FIELD_INPUT_LIMIT;
    private static Field FIELD_PROGRESS;
    private static Field FIELD_EJECTING;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_COMPRESSOR = Class.forName("com.blakebr0.extendedcrafting.tile.TileCompressor");
            CLASS_COMPRESSOR_RECIPE = Class.forName("com.blakebr0.extendedcrafting.crafting.CompressorRecipe");
            CLASS_RECIPE_MANAGER = Class.forName("com.blakebr0.extendedcrafting.crafting.CompressorRecipeManager");
            Method getInstance = CLASS_RECIPE_MANAGER.getMethod("getInstance");
            RECIPE_MANAGER_INSTANCE = getInstance.invoke(null);
            METHOD_GET_RECIPES = CLASS_RECIPE_MANAGER.getMethod("getRecipes");


            METHOD_GET_INPUT = CLASS_COMPRESSOR_RECIPE.getMethod("getInput");
            METHOD_GET_OUTPUT = CLASS_COMPRESSOR_RECIPE.getMethod("getOutput");
            METHOD_GET_INPUT_COUNT = CLASS_COMPRESSOR_RECIPE.getMethod("getInputCount");
            METHOD_GET_CATALYST = CLASS_COMPRESSOR_RECIPE.getMethod("getCatalyst");
            METHOD_CONSUME_CATALYST = CLASS_COMPRESSOR_RECIPE.getMethod("consumeCatalyst");
            METHOD_GET_POWER_COST = CLASS_COMPRESSOR_RECIPE.getMethod("getPowerCost");
            FIELD_MATERIAL_STACK = CLASS_TILE_COMPRESSOR.getDeclaredField("materialStack");
            FIELD_MATERIAL_STACK.setAccessible(true);
            FIELD_MATERIAL_COUNT = CLASS_TILE_COMPRESSOR.getDeclaredField("materialCount");
            FIELD_MATERIAL_COUNT.setAccessible(true);
            FIELD_INPUT_LIMIT = CLASS_TILE_COMPRESSOR.getDeclaredField("inputLimit");
            FIELD_INPUT_LIMIT.setAccessible(true);
            FIELD_PROGRESS = CLASS_TILE_COMPRESSOR.getDeclaredField("progress");
            FIELD_PROGRESS.setAccessible(true);
            FIELD_EJECTING = CLASS_TILE_COMPRESSOR.getDeclaredField("ejecting");
            FIELD_EJECTING.setAccessible(true);

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] Compressor reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return BLOCK_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_TILE_COMPRESSOR.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_COMPRESSOR.isInstance(te)) return false;
        if (getProgress(te) != 0) return false;
        // 强制关闭输入限制,允许一次性注入全部材料
        try {
            FIELD_INPUT_LIMIT.setBoolean(te, false);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to disable compressor inputLimit", e);
        }
        return true;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_COMPRESSOR.isInstance(te)) return false;
        if (getProgress(te) != 0) return false;

        // 收集输入材料(忽略空槽)
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                inputs.add(stack.copy());
            }
        }
        if (inputs.isEmpty()) return false;

        // 通过产物反查配方
        // 注意：ingredients slot 0 通常是主要材料,我们用它来找配方
        // 但更可靠的方式是：已经有一个 recipe 在压缩机里,或者通过 expected output 查找
        // 由于 pushMaterials 不知道 expected output,我们用 slot 0 的物品 + 所有输入来匹配
        Object recipe = findRecipeByInputs(inputs);
        if (recipe == null) return false;

        // 主材料：取第一个匹配 recipe input 的物品
        net.minecraft.item.crafting.Ingredient recipeInput = getRecipeInput(recipe);
        ItemStack mainMaterial = findMatchingStack(inputs, recipeInput);
        if (mainMaterial.isEmpty()) return false;

        // 直接设置 materialStack / materialCount,不限制数量
        // 注意：必须清空 slot 1,因为 TileCompressor.setInventorySlotContents
        // 会将堆叠截断到 64,然后 update() tick 会继续从 slot 1 吸取到 materialCount,
        // 导致实际材料数量 = 反射值 + 64(例如 1000 → 1064)
        try {
            FIELD_MATERIAL_STACK.set(te, mainMaterial.copy());
            FIELD_MATERIAL_COUNT.setInt(te, mainMaterial.getCount());
            setSlot(te, 1, ItemStack.EMPTY);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to inject compressor materials", e);
            return false;
        }

        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        // 压缩机在 materialCount >= inputCount 时自动开始
        // 将 ejecting 设为 false 防止它立刻弹出产物
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_TILE_COMPRESSOR.isInstance(te)) {
            try {
                FIELD_EJECTING.setBoolean(te, false);
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_COMPRESSOR.isInstance(te)) return false;
        if (getProgress(te) != 0) return false;

        // 检查 slot 0 是否有产物：有产物说明已完成,可以收集
        ItemStack output = getSlot(te, 0);
        return !output.isEmpty();
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_COMPRESSOR.isInstance(te)) return result;

        // 取出 slot 0 的产物
        ItemStack output = extractSlot(te, 0, 64);
        if (!output.isEmpty()) {
            result.add(output);
        }

        // 重置 materialStack / materialCount,防止干扰下一次
        try {
            FIELD_MATERIAL_STACK.set(te, ItemStack.EMPTY);
            FIELD_MATERIAL_COUNT.setInt(te, 0);
            setSlot(te, 1, ItemStack.EMPTY);
        } catch (Exception ignored) {
        }

        return result;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_COMPRESSOR.isInstance(te)) return result;

        // 提取 slot 0 和 slot 1 的所有物品
        for (int slot : new int[]{0, 1}) {
            ItemStack stack = extractSlot(te, slot, 64);
            if (!stack.isEmpty()) result.add(stack);
        }

        // 重置 material
        try {
            FIELD_MATERIAL_STACK.set(te, ItemStack.EMPTY);
            FIELD_MATERIAL_COUNT.setInt(te, 0);
        } catch (Exception ignored) {}

        return result;
    }

    // ---- IVirtualCraftingHandler / IVirtualBatchCraftingHandler ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        initReflection();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;

        ItemStack expected = outputs[0].createItemStack();
        if (expected.isEmpty()) return false;

        Object recipe = findRecipeByOutput(expected);
        if (recipe == null) return false;

        return ingredientsMatchCompressor(recipe, ingredients);
    }

    public List<ItemStack> virtualCraft(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, IActionSource source) {
        return virtualCraftBatch(world, pos, ingredients, outputs, 1, source);
    }

    @Override
    public List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return Arrays.asList(
                EnumParticleTypes.PORTAL,
                EnumParticleTypes.SPELL_WITCH,
                EnumParticleTypes.SMOKE_LARGE,
                EnumParticleTypes.END_ROD
        );
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        initReflection();

        Object recipe = findRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return costs;

        int inputCount = getRecipeInputCount(recipe);
        int powerCost = getRecipePowerCost(recipe);
        Ingredient inputIngredient = getRecipeInput(recipe);
        Ingredient catalystIngredient = getRecipeCatalyst(recipe);
        boolean consumeCatalyst = consumeCatalyst(recipe);

        // 主材料：按配方 inputCount * count 扣除
        ItemStack mainStack = findFirstMatchingStack(ingredients, inputIngredient);
        if (!mainStack.isEmpty()) {
            IAEItemStack mainCost = AEItemStack.fromItemStack(mainStack.copy());
            mainCost.setStackSize((long) inputCount * count);
            costs.add(mainCost);
        }

        // 催化剂：仅在消耗催化剂时扣除
        if (consumeCatalyst && catalystIngredient != null) {
            ItemStack catalystStack = findFirstMatchingStack(ingredients, catalystIngredient);
            if (!catalystStack.isEmpty()) {
                IAEItemStack catalystCost = AEItemStack.fromItemStack(catalystStack.copy());
                catalystCost.setStackSize(count);
                costs.add(catalystCost);
            }
        }

        // RF 消耗
        if (powerCost > 0) {
            costs.add(AEEnergyStack.create((long) powerCost * count));
        }

        return costs;
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count, IActionSource source) {
        List<ItemStack> products = new ArrayList<>();
        if (!canCraftVirtually(world, pos, ingredients, outputs)) return products;
        return scaleOutputsByCount(outputs, count);
    }

    // ---- 配方查找与匹配 ----

    @SuppressWarnings("unchecked")
    private static Object findRecipeByOutput(ItemStack output) {
        initReflection();
        try {
            List<Object> recipes = (List<Object>) METHOD_GET_RECIPES.invoke(RECIPE_MANAGER_INSTANCE);
            if (recipes == null) return null;
            for (Object recipe : recipes) {
                ItemStack recipeOutput = (ItemStack) METHOD_GET_OUTPUT.invoke(recipe);
                if (!recipeOutput.isEmpty()
                        && recipeOutput.getItem() == output.getItem()
                        && recipeOutput.getMetadata() == output.getMetadata()) {
                    return recipe;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static boolean ingredientsMatchCompressor(Object recipe, InventoryCrafting ingredients) {
        try {
            int inputCount = getRecipeInputCount(recipe);
            Ingredient inputIngredient = getRecipeInput(recipe);
            Ingredient catalystIngredient = getRecipeCatalyst(recipe);
            boolean consumeCatalyst = consumeCatalyst(recipe);

            int inputFound = 0;
            boolean catalystFound = false;
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (stack.isEmpty()) continue;

                if (inputIngredient.apply(stack)) {
                    inputFound += stack.getCount();
                } else if (consumeCatalyst && catalystIngredient != null && catalystIngredient.apply(stack)) {
                    catalystFound = true;
                } else {
                    // 未知材料
                    return false;
                }
            }

            return inputFound >= inputCount && (!consumeCatalyst || catalystFound);
        } catch (Exception e) {
            return false;
        }
    }

    private static ItemStack findFirstMatchingStack(InventoryCrafting ingredients, Ingredient ingredient) {
        if (ingredient == null) return ItemStack.EMPTY;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty() && ingredient.apply(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static int getRecipeInputCount(Object recipe) {
        try {
            return (int) METHOD_GET_INPUT_COUNT.invoke(recipe);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getRecipePowerCost(Object recipe) {
        try {
            return (int) METHOD_GET_POWER_COST.invoke(recipe);
        } catch (Exception e) {
            return 0;
        }
    }

    private static Ingredient getRecipeCatalyst(Object recipe) {
        try {
            return (Ingredient) METHOD_GET_CATALYST.invoke(recipe);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean consumeCatalyst(Object recipe) {
        try {
            return (boolean) METHOD_CONSUME_CATALYST.invoke(recipe);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 反射辅助 ----

    private static int getProgress(TileEntity te) {
        try {
            return FIELD_PROGRESS.getInt(te);
        } catch (Exception e) {
            return -1;
        }
    }

    private static void setSlot(TileEntity te, int slot, ItemStack stack) {
        if (te instanceof IInventory) {
            ((IInventory) te).setInventorySlotContents(slot, stack);
        }
    }

    private static ItemStack getSlot(TileEntity te, int slot) {
        if (te instanceof IInventory) {
            return ((IInventory) te).getStackInSlot(slot);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractSlot(TileEntity te, int slot, int amount) {
        if (te instanceof IInventory) {
            IInventory inv = (IInventory) te;
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                ItemStack extracted = stack.splitStack(Math.min(amount, stack.getCount()));
                if (stack.isEmpty()) {
                    inv.setInventorySlotContents(slot, ItemStack.EMPTY);
                }
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    @SuppressWarnings("unchecked")
    private static Object findRecipeByInputs(List<ItemStack> inputs) {
        try {
            List<Object> recipes = (List<Object>) METHOD_GET_RECIPES.invoke(RECIPE_MANAGER_INSTANCE);
            if (recipes == null) return null;

            for (Object recipe : recipes) {
                net.minecraft.item.crafting.Ingredient recipeInput = getRecipeInput(recipe);

                // 检查是否有输入匹配 recipe input
                boolean hasInput = false;
                for (ItemStack stack : inputs) {
                    if (recipeInput.apply(stack)) {
                        hasInput = true;
                        break;
                    }
                }
                if (!hasInput) continue;

                return recipe;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] findRecipeByInputs failed", e);
        }
        return null;
    }

    private static ItemStack findMatchingStack(List<ItemStack> inputs, net.minecraft.item.crafting.Ingredient recipeInput) {
        if (recipeInput == null || recipeInput == net.minecraft.item.crafting.Ingredient.EMPTY) return ItemStack.EMPTY;
        for (ItemStack stack : inputs) {
            if (recipeInput.apply(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static net.minecraft.item.crafting.Ingredient getRecipeInput(Object recipe) {
        try {
            return (net.minecraft.item.crafting.Ingredient) METHOD_GET_INPUT.invoke(recipe);
        } catch (Exception e) {
            return net.minecraft.item.crafting.Ingredient.EMPTY;
        }
    }
}
