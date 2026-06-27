package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extended Crafting 合成核心 + 基座处理器.
 *
 * <p>结构：中心 CraftingCore,周围 7×7 水平范围内放置 Pedestal.
 * slot 0 → Core 主材料,slot 1+ → Pedestals 辅助材料(各1个).</p>
 */
public class ExtendedCraftingHandler implements IRemoteHandler, IVirtualBatchCraftingHandler {

    private static final String CORE_ID = "extendedcrafting:crafting_core";

    // 反射缓存
    private static Class<?> CLASS_CORE;
    private static Class<?> CLASS_PEDESTAL;
    private static Field FIELD_PROGRESS;
    private static Method METHOD_LOCATE_PEDESTALS;
    private static Method METHOD_GET_INVENTORY_CORE;
    private static Method METHOD_GET_INVENTORY_PEDESTAL;
    private static boolean reflectionReady = false;

    // 批量虚拟合成：CombinationRecipe 反射
    private static Class<?> CLASS_COMBINATION_RECIPE;
    private static Class<?> CLASS_COMBINATION_RECIPE_MANAGER;
    private static Method METHOD_COMBINATION_GET_INSTANCE;
    private static Method METHOD_COMBINATION_GET_RECIPES;
    private static Method METHOD_RECIPE_GET_OUTPUT;
    private static Method METHOD_RECIPE_GET_COST;
    private static Method METHOD_RECIPE_GET_INPUT_INGREDIENT;
    private static Method METHOD_RECIPE_GET_PEDESTAL_INGREDIENTS;
    private static boolean virtualReflectionReady = false;

    /**
     * 防止 pushMaterials 后立即 isIdle(progress==0) 导致提前收回材料。
     * 推料 tick 记录在 {@link TargetSession} 中。
     */
    private static final int PUSH_IDLE_GRACE_TICKS = 2;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_CORE = Class.forName("com.blakebr0.extendedcrafting.tile.TileCraftingCore");
            CLASS_PEDESTAL = Class.forName("com.blakebr0.extendedcrafting.tile.TilePedestal");
            FIELD_PROGRESS = CLASS_CORE.getDeclaredField("progress");
            FIELD_PROGRESS.setAccessible(true);
            METHOD_LOCATE_PEDESTALS = CLASS_CORE.getDeclaredMethod("locatePedestals");
            METHOD_LOCATE_PEDESTALS.setAccessible(true);
            METHOD_GET_INVENTORY_CORE = CLASS_CORE.getMethod("getInventory");
            METHOD_GET_INVENTORY_PEDESTAL = CLASS_PEDESTAL.getMethod("getInventory");
            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ExtendedCrafting reflection init failed", e);
        }
    }

    private static void initVirtualReflection() {
        if (virtualReflectionReady) return;
        try {
            CLASS_COMBINATION_RECIPE = Class.forName("com.blakebr0.extendedcrafting.crafting.CombinationRecipe");
            CLASS_COMBINATION_RECIPE_MANAGER = Class.forName("com.blakebr0.extendedcrafting.crafting.CombinationRecipeManager");
            METHOD_COMBINATION_GET_INSTANCE = CLASS_COMBINATION_RECIPE_MANAGER.getMethod("getInstance");
            METHOD_COMBINATION_GET_RECIPES = CLASS_COMBINATION_RECIPE_MANAGER.getMethod("getRecipes");
            METHOD_RECIPE_GET_OUTPUT = CLASS_COMBINATION_RECIPE.getMethod("getOutput");
            METHOD_RECIPE_GET_COST = CLASS_COMBINATION_RECIPE.getMethod("getCost");
            METHOD_RECIPE_GET_INPUT_INGREDIENT = CLASS_COMBINATION_RECIPE.getMethod("getInputIngredient");
            METHOD_RECIPE_GET_PEDESTAL_INGREDIENTS = CLASS_COMBINATION_RECIPE.getMethod("getPedestalIngredients");
            virtualReflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ExtendedCrafting virtual reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return CORE_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_CORE.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        if (getProgress(te) != 0) return false;

        // Core inventory slot 0 必须为空
        IItemHandler coreInv = getCoreInventory(te);
        if (coreInv == null) return false;
        if (!coreInv.getStackInSlot(0).isEmpty()) return false;

        // 检查周围有足够 pedestals(空或已有匹配材料的也算,简化处理)
        List<BlockPos> pedestalPositions = getPedestalPositions(te);
        int needed = 0;
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) needed++;
        }
        return pedestalPositions.size() >= needed;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        if (getProgress(te) != 0) return false;

        IItemHandler coreInv = getCoreInventory(te);
        if (coreInv == null) return false;

        ItemStack main = ingredients.getStackInSlot(0);
        List<BlockPos> chosenPedestals = new ArrayList<>();

        // 预检主材料
        if (!main.isEmpty()) {
            ItemStack simulated = coreInv.insertItem(0, main.copy(), true);
            if (!simulated.isEmpty()) {
                return false;
            }
        }

        // 预检辅助材料
        List<BlockPos> pedestalPositions = getPedestalPositions(te);
        int pedestalIdx = 0;
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            BlockPos chosen = null;
            while (pedestalIdx < pedestalPositions.size()) {
                BlockPos pPos = pedestalPositions.get(pedestalIdx);
                pedestalIdx++;
                TileEntity pTe = world.getTileEntity(pPos);
                if (CLASS_PEDESTAL.isInstance(pTe)) {
                    IItemHandler pInv = getPedestalInventory(pTe);
                    if (pInv != null && pInv.getStackInSlot(0).isEmpty()) {
                        ItemStack single = stack.copy();
                        single.setCount(1);
                        ItemStack simulated = pInv.insertItem(0, single, true);
                        if (simulated.isEmpty()) {
                            chosen = pPos;
                            break;
                        }
                    }
                }
            }
            if (chosen == null) {
                return false;
            }
            chosenPedestals.add(chosen);
        }

        // 实际插入
        if (!main.isEmpty()) {
            coreInv.insertItem(0, main.copy(), false);
        }
        for (int i = 0; i < chosenPedestals.size(); i++) {
            int ingredientSlot = 1 + i; // 当前实现按顺序一一对应
            // 跳过空槽位重新对齐：找到第 i 个非空辅助材料
            while (ingredientSlot < ingredients.getSizeInventory() && ingredients.getStackInSlot(ingredientSlot).isEmpty()) {
                ingredientSlot++;
            }
            if (ingredientSlot >= ingredients.getSizeInventory()) break;
            BlockPos pPos = chosenPedestals.get(i);
            TileEntity pTe = world.getTileEntity(pPos);
            if (!CLASS_PEDESTAL.isInstance(pTe)) continue;
            IItemHandler pInv = getPedestalInventory(pTe);
            if (pInv == null) continue;
            ItemStack single = ingredients.getStackInSlot(ingredientSlot).copy();
            single.setCount(1);
            pInv.insertItem(0, single, false);
        }

        if (session != null) {
            session.setPushTick(world.getTotalWorldTime());
        }
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        // EC CraftingCore 是自动检测配方并推进的,不需要显式启动
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        // 推料后至少等待数 tick,避免 progress==0 时立即收回刚放入的材料
        if (session != null && !session.isPushGraceElapsed(world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) {
            return false;
        }
        // progress == 0 表示合成已完成(或尚未开始),允许 collectProducts 提取产物
        return getProgress(te) == 0;
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return collectAllFromTarget(world, pos);
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return result;
        if (getProgress(te) != 0) return result;

        // 从 Core slot 0 提取产物
        IItemHandler coreInv = getCoreInventory(te);
        if (coreInv != null) {
            ItemStack output = coreInv.extractItem(0, 64, false);
            if (!output.isEmpty()) {
                result.add(output);
            }
        }

        // 清理 Pedestals 中的残余材料(防止干扰下一次合成)
        List<BlockPos> pedestalPositions = getPedestalPositions(te);
        for (BlockPos pPos : pedestalPositions) {
            TileEntity pTe = world.getTileEntity(pPos);
            if (CLASS_PEDESTAL.isInstance(pTe)) {
                IItemHandler pInv = getPedestalInventory(pTe);
                if (pInv != null) {
                    ItemStack residual = pInv.extractItem(0, 64, false);
                    if (!residual.isEmpty()) {
                        result.add(residual);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return collectAllFromTarget(world, pos);
    }

    private List<ItemStack> collectAllFromTarget(World world, BlockPos pos) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return result;

        IItemHandler coreInv = getCoreInventory(te);
        if (coreInv != null) {
            for (int i = 0; i < coreInv.getSlots(); i++) {
                ItemStack stack = coreInv.extractItem(i, 64, false);
                if (!stack.isEmpty()) result.add(stack);
            }
        }

        List<BlockPos> pedestalPositions = getPedestalPositions(te);
        for (BlockPos pPos : pedestalPositions) {
            TileEntity pTe = world.getTileEntity(pPos);
            if (CLASS_PEDESTAL.isInstance(pTe)) {
                IItemHandler pInv = getPedestalInventory(pTe);
                if (pInv != null) {
                    ItemStack stack = pInv.extractItem(0, 64, false);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
        }
        return result;
    }

    private static int getProgress(TileEntity core) {
        try {
            return (int) FIELD_PROGRESS.get(core);
        } catch (Exception e) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BlockPos> getPedestalPositions(TileEntity core) {
        try {
            return (List<BlockPos>) METHOD_LOCATE_PEDESTALS.invoke(core);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static IItemHandler getCoreInventory(TileEntity core) {
        IItemHandler cap = core.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (cap != null) return cap;
        try {
            return (IItemHandler) METHOD_GET_INVENTORY_CORE.invoke(core);
        } catch (Exception e) {
            return null;
        }
    }

    private static IItemHandler getPedestalInventory(TileEntity pedestal) {
        IItemHandler cap = pedestal.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (cap != null) return cap;
        try {
            return (IItemHandler) METHOD_GET_INVENTORY_PEDESTAL.invoke(pedestal);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- IVirtualCraftingHandler / IVirtualBatchCraftingHandler ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        initReflection();
        initVirtualReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;

        ItemStack expected = outputs[0].createItemStack();
        if (expected.isEmpty()) return false;

        Object recipe = findCombinationRecipeByOutput(expected);
        if (recipe == null) return false;

        return ingredientsMatchCombination(recipe, ingredients);
    }

    public List<ItemStack> virtualCraft(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, IActionSource source) {
        return virtualCraftBatch(world, pos, ingredients, outputs, 1, source);
    }

    @Override
    public List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return Arrays.asList(
                EnumParticleTypes.ENCHANTMENT_TABLE,
                EnumParticleTypes.PORTAL,
                EnumParticleTypes.SPELL_WITCH,
                EnumParticleTypes.END_ROD
        );
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        initVirtualReflection();

        // 物品消耗：核心材料 +  pedestal 材料，按 count 倍增
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                IAEItemStack cost = AEItemStack.fromItemStack(stack.copy());
                cost.setStackSize((long) stack.getCount() * count);
                costs.add(cost);
            }
        }

        // RF 消耗：按配方 cost * count
        Object recipe = findCombinationRecipeByOutput(outputs[0].createItemStack());
        if (recipe != null) {
            long rf = getRecipeCost(recipe);
            if (rf > 0) {
                costs.add(AEEnergyStack.create(rf * count));
            }
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
    private static Object findCombinationRecipeByOutput(ItemStack output) {
        initVirtualReflection();
        try {
            Object manager = METHOD_COMBINATION_GET_INSTANCE.invoke(null);
            List<Object> recipes = (List<Object>) METHOD_COMBINATION_GET_RECIPES.invoke(manager);
            if (recipes == null) return null;
            for (Object recipe : recipes) {
                ItemStack recipeOutput = (ItemStack) METHOD_RECIPE_GET_OUTPUT.invoke(recipe);
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

    @SuppressWarnings("unchecked")
    private static boolean ingredientsMatchCombination(Object recipe, InventoryCrafting ingredients) {
        try {
            Ingredient coreIngredient = (Ingredient) METHOD_RECIPE_GET_INPUT_INGREDIENT.invoke(recipe);
            List<Ingredient> pedestalIngredients = (List<Ingredient>) METHOD_RECIPE_GET_PEDESTAL_INGREDIENTS.invoke(recipe);

            List<ItemStack> available = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    available.add(stack.copy());
                }
            }

            // 核心材料：必须有一个且仅一个匹配
            boolean coreFound = false;
            for (int i = 0; i < available.size(); i++) {
                if (coreIngredient.apply(available.get(i))) {
                    available.remove(i);
                    coreFound = true;
                    break;
                }
            }
            if (!coreFound) return false;

            // pedestal 材料：每个 ingredient 必须有一个匹配
            for (Ingredient ped : pedestalIngredients) {
                if (ped == null || ped == Ingredient.EMPTY) continue;
                boolean found = false;
                for (int i = 0; i < available.size(); i++) {
                    if (ped.apply(available.get(i))) {
                        available.remove(i);
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }

            // 不能有额外材料
            return available.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static long getRecipeCost(Object recipe) {
        try {
            return (long) METHOD_RECIPE_GET_COST.invoke(recipe);
        } catch (Exception e) {
            return 0;
        }
    }
}
