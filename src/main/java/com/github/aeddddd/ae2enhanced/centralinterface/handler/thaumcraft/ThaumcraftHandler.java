package com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.RecipeMatcher;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.IThaumcraftRecipe;
import thaumcraft.api.crafting.InfusionRecipe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thaumcraft 注魔矩阵远程处理器.
 *
 * <p>配方匹配策略：在 {@code canStart} 中通过输入物品种类+数量遍历注魔配方列表,
 * 找到匹配后缓存；{@code pushMaterials} 将主材放入矩阵正下方基座、辅材放入周围基座；
 * {@code startProcess} 使用 {@code FakePlayer} 调用 {@code craftingStart} 并临时授予研究,
 * 以跳过进度检查.</p>
 */
public class ThaumcraftHandler implements IRemoteHandler, IVirtualBatchCraftingHandler {

    private static final String BLOCK_ID = "thaumcraft:infusion_matrix";

    // Thaumcraft 内部类反射(API 类直接引用,内部类反射隔离)
    private static Class<?> CLASS_TILE_INFUSION_MATRIX;
    private static Class<?> CLASS_TILE_PEDESTAL;
    private static Method METHOD_CRAFTING_START;
    private static Method METHOD_GET_SURROUNDINGS;
    private static Method METHOD_VALID_LOCATION;
    private static Method METHOD_SYNC_TILE;
    private static Method METHOD_MARK_DIRTY;
    private static Method METHOD_PEDESTAL_GET_STACK;
    private static Method METHOD_PEDESTAL_SET_STACK;
    private static Method METHOD_PEDESTAL_SET_STACK_FROM_INFUSION;
    private static Field FIELD_ACTIVE;
    private static Field FIELD_CRAFTING;
    private static Field FIELD_PEDESTALS;
    private static Field FIELD_RECIPE_OUTPUT;
    private static Field FIELD_RECIPE_OUTPUT_LABEL;
    private static Field FIELD_RECIPE_INPUT;
    private static Field FIELD_RECIPE_INGREDIENTS;
    private static Field FIELD_RECIPE_TYPE;
    private static Field FIELD_RECIPE_ESSENTIA;
    private static Field FIELD_RECIPE_INSTABILITY;
    private static Field FIELD_RECIPE_XP;
    private static Field FIELD_RECIPE_PLAYER;
    private static Field FIELD_CHECK_SURROUNDINGS;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_INFUSION_MATRIX = Class.forName("thaumcraft.common.tiles.crafting.TileInfusionMatrix");
            CLASS_TILE_PEDESTAL = Class.forName("thaumcraft.common.tiles.crafting.TilePedestal");

            METHOD_CRAFTING_START = CLASS_TILE_INFUSION_MATRIX.getMethod("craftingStart", EntityPlayer.class);
            METHOD_GET_SURROUNDINGS = CLASS_TILE_INFUSION_MATRIX.getDeclaredMethod("getSurroundings");
            METHOD_GET_SURROUNDINGS.setAccessible(true);
            METHOD_VALID_LOCATION = CLASS_TILE_INFUSION_MATRIX.getMethod("validLocation");
            METHOD_SYNC_TILE = CLASS_TILE_INFUSION_MATRIX.getMethod("syncTile", boolean.class);
            METHOD_MARK_DIRTY = CLASS_TILE_INFUSION_MATRIX.getMethod("func_70296_d");

            METHOD_PEDESTAL_GET_STACK = CLASS_TILE_PEDESTAL.getMethod("func_70301_a", int.class);
            METHOD_PEDESTAL_SET_STACK = CLASS_TILE_PEDESTAL.getMethod("func_70299_a", int.class, ItemStack.class);
            METHOD_PEDESTAL_SET_STACK_FROM_INFUSION = CLASS_TILE_PEDESTAL.getMethod("setInventorySlotContentsFromInfusion", int.class, ItemStack.class);

            FIELD_ACTIVE = CLASS_TILE_INFUSION_MATRIX.getField("active");
            FIELD_CRAFTING = CLASS_TILE_INFUSION_MATRIX.getField("crafting");
            FIELD_PEDESTALS = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("pedestals");
            FIELD_PEDESTALS.setAccessible(true);
            FIELD_RECIPE_OUTPUT = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeOutput");
            FIELD_RECIPE_OUTPUT.setAccessible(true);
            FIELD_RECIPE_OUTPUT_LABEL = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeOutputLabel");
            FIELD_RECIPE_OUTPUT_LABEL.setAccessible(true);
            FIELD_RECIPE_INPUT = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeInput");
            FIELD_RECIPE_INPUT.setAccessible(true);
            FIELD_RECIPE_INGREDIENTS = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeIngredients");
            FIELD_RECIPE_INGREDIENTS.setAccessible(true);
            FIELD_RECIPE_TYPE = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeType");
            FIELD_RECIPE_TYPE.setAccessible(true);
            FIELD_RECIPE_ESSENTIA = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeEssentia");
            FIELD_RECIPE_ESSENTIA.setAccessible(true);
            FIELD_RECIPE_INSTABILITY = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeInstability");
            FIELD_RECIPE_INSTABILITY.setAccessible(true);
            FIELD_RECIPE_XP = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeXP");
            FIELD_RECIPE_XP.setAccessible(true);
            FIELD_RECIPE_PLAYER = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipePlayer");
            FIELD_RECIPE_PLAYER.setAccessible(true);
            FIELD_CHECK_SURROUNDINGS = CLASS_TILE_INFUSION_MATRIX.getField("checkSurroundings");

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ThaumcraftHandler reflection init failed", e);
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
        return CLASS_TILE_INFUSION_MATRIX.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) return false;
        if (isCrafting(te)) return false;
        if (!validLocation(te)) return false;
        if (!clearAndCheckPedestals(world, pos, te)) return false;

        // AE 样板约定：第一个非空槽位 = 主材,其余 = 辅材
        boolean hasItems = false;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }
        return hasItems;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) return false;
        // dispatcher 已在 pushMaterials 前调用 clearOutputs 清空基座，
        // 这里不再重复清空，避免意外删除残留物品。

        // 按 AE 样板槽位顺序收集：第一个非空 = 主材,其余 = 辅材
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        if (stacks.isEmpty()) return false;

        // 放入主材基座(矩阵正下方 2 格)
        ItemStack mainItem = stacks.remove(0);
        BlockPos mainPos = pos.down(2);
        TileEntity mainTe = world.getTileEntity(mainPos);
        if (!CLASS_TILE_PEDESTAL.isInstance(mainTe)) return false;
        try {
            METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, mainItem);
        } catch (Exception e) {
            return false;
        }

        // 放入辅材(按 pedestals 列表顺序,逐个基座放剩余物品)
        try {
            METHOD_GET_SURROUNDINGS.invoke(te);
            @SuppressWarnings("unchecked")
            List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);
            for (BlockPos pPos : pedestals) {
                if (stacks.isEmpty()) break;
                TileEntity pTe = world.getTileEntity(pPos);
                if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                if (!((ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0)).isEmpty()) continue;
                ItemStack aux = stacks.remove(0);
                METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, aux);
            }
        } catch (Exception e) {
            return false;
        }
        return stacks.isEmpty();
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) return false;
        if (isCrafting(te)) return true;

        // 先尝试让矩阵自己走正常流程：craftingStart 内部会查找配方、检查研究、吸收源质
        // 注意：craftingStart 返回 void,不能读取 boolean 返回值
        if (world instanceof WorldServer) {
            try {
                FakePlayer fakePlayer = FakePlayerFactory.get((WorldServer) world,
                    new GameProfile(UUID.randomUUID(), "[AE2E]"));

                // 确保矩阵已激活(active 是电源开关,craftingStart 不检查它,但 craftCycle 需要 active=true)
                if (!isActive(te)) {
                    FIELD_ACTIVE.setBoolean(te, true);
                    METHOD_MARK_DIRTY.invoke(te);
                    METHOD_SYNC_TILE.invoke(te, false);
                }

                METHOD_CRAFTING_START.invoke(te, fakePlayer);
                if (isCrafting(te)) {
                    return true;
                }

                // 未启动,可能是研究不足.从基座反查配方,临时授予研究后重试
                InfusionRecipe recipe = findRecipeFromPedestals(world, pos, te);
                if (recipe != null) {
                    IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(fakePlayer);
                    String research = recipe.getResearch();
                    boolean added = false;
                    if (knowledge != null && research != null && !research.isEmpty()) {
                        added = knowledge.addResearch(research);
                    }
                    METHOD_CRAFTING_START.invoke(te, fakePlayer);
                    if (added && knowledge != null) {
                        knowledge.removeResearch(research);
                    }
                    if (isCrafting(te)) {
                        return true;
                    }
                    // 仍然失败(可能是源质不足等),回退到强制完成
                    return forceStartCrafting(world, pos, te, recipe);
                }
            } catch (Exception e) {
                InfusionRecipe recipe = findRecipeFromPedestals(world, pos, te);
                if (recipe != null) {
                    return forceStartCrafting(world, pos, te, recipe);
                }
            }
        }

        return false;
    }

    /**
     * FakePlayer 方案失败时的回退：直接设置矩阵字段并立即完成合成.
     * 这会跳过注魔过程(源质吸收、稳定性波动等),直接产出结果.
     */
    private boolean forceStartCrafting(World world, BlockPos pos, TileEntity te, InfusionRecipe recipe) {
        try {
            // 获取主材
            BlockPos mainPos = pos.down(2);
            TileEntity mainTe = world.getTileEntity(mainPos);
            if (!CLASS_TILE_PEDESTAL.isInstance(mainTe)) return false;
            ItemStack mainItem = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(mainTe, 0);
            if (mainItem.isEmpty()) return false;

            // 获取产物
            Object output = recipe.getRecipeOutput();
            if (output instanceof ItemStack) {
                ItemStack result = ((ItemStack) output).copy();

                // 消耗主材(如果有耐久则减 1,否则清空)
                if (mainItem.isItemStackDamageable()) {
                    mainItem.setItemDamage(mainItem.getItemDamage() + 1);
                    if (mainItem.getItemDamage() >= mainItem.getMaxDamage()) {
                        METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, ItemStack.EMPTY);
                    } else {
                        METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, mainItem);
                    }
                } else {
                    METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, ItemStack.EMPTY);
                }

                // 消耗辅材并处理 container item
                @SuppressWarnings("unchecked")
                List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);
                NonNullList<Ingredient> components = recipe.getComponents();
                for (int i = 0; i < components.size() && i < pedestals.size(); i++) {
                    BlockPos pPos = pedestals.get(i);
                    TileEntity pTe = world.getTileEntity(pPos);
                    if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                    ItemStack stack = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0);
                    if (stack.isEmpty()) continue;

                    ItemStack container = stack.getItem().getContainerItem(stack);
                    if (container != null && !container.isEmpty()) {
                        METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, container.copy());
                    } else {
                        METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, ItemStack.EMPTY);
                    }
                }

                // 放入产物
                METHOD_PEDESTAL_SET_STACK_FROM_INFUSION.invoke(mainTe, 0, result);
            }

            // 重置矩阵状态
            FIELD_ACTIVE.setBoolean(te, false);
            FIELD_CRAFTING.setBoolean(te, false);
            METHOD_MARK_DIRTY.invoke(te);
            METHOD_SYNC_TILE.invoke(te, false);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) return false;
        return !isCrafting(te);
    }

    // ---- IVirtualCraftingHandler / IVirtualBatchCraftingHandler ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        InfusionRecipe recipe = ThaumcraftApi.getInfusionRecipe(outputs[0].createItemStack());
        if (recipe == null) return false;
        return matchInfusionRecipe(recipe, ingredients);
    }

    public List<ItemStack> virtualCraft(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, IActionSource source) {
        return virtualCraftBatch(world, pos, ingredients, outputs, 1, source);
    }

    @Override
    public List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return Arrays.asList(
                EnumParticleTypes.SPELL_WITCH,
                EnumParticleTypes.PORTAL,
                EnumParticleTypes.ENCHANTMENT_TABLE,
                EnumParticleTypes.END_ROD
        );
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;
        InfusionRecipe recipe = ThaumcraftApi.getInfusionRecipe(outputs[0].createItemStack());
        if (recipe == null) return costs;

        List<ItemStack> available = collectNonEmpty(ingredients);

        // 主材
        Ingredient mainInput = recipe.getRecipeInput();
        if (mainInput != null) {
            for (int i = 0; i < available.size(); i++) {
                if (mainInput.apply(available.get(i))) {
                    IAEItemStack cost = AEItemStack.fromItemStack(available.remove(i).copy());
                    cost.setStackSize(count);
                    costs.add(cost);
                    break;
                }
            }
        }

        // 辅材
        NonNullList<Ingredient> components = recipe.getComponents();
        if (components != null) {
            for (Ingredient ing : components) {
                if (ing == null || ing == Ingredient.EMPTY) continue;
                for (int i = 0; i < available.size(); i++) {
                    if (ing.apply(available.get(i))) {
                        IAEItemStack cost = AEItemStack.fromItemStack(available.remove(i).copy());
                        cost.setStackSize(count);
                        costs.add(cost);
                        break;
                    }
                }
            }
        }

        // 源质
        AspectList aspects = recipe.getAspects();
        if (aspects != null && aspects.size() > 0) {
            List<IAEStack> essentia = createEssentiaCosts(aspects, count);
            if (essentia == null) {
                return null;
            }
            costs.addAll(essentia);
        }

        return costs;
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count, IActionSource source) {
        List<ItemStack> products = new ArrayList<>();
        if (!canCraftVirtually(world, pos, ingredients, outputs)) return products;
        return scaleOutputsByCount(outputs, count);
    }

    // ---- 批量虚拟合成辅助 ----

    private boolean matchInfusionRecipe(InfusionRecipe recipe, InventoryCrafting ingredients) {
        List<ItemStack> available = collectNonEmpty(ingredients);
        Ingredient mainInput = recipe.getRecipeInput();
        if (mainInput != null) {
            boolean found = false;
            for (int i = 0; i < available.size(); i++) {
                if (mainInput.apply(available.get(i))) {
                    available.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        NonNullList<Ingredient> components = recipe.getComponents();
        if (components != null) {
            for (Ingredient ing : components) {
                if (ing == null || ing == Ingredient.EMPTY) continue;
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
        }
        return available.isEmpty();
    }

    private List<ItemStack> collectNonEmpty(InventoryCrafting ingredients) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) list.add(stack.copy());
        }
        return list;
    }

    /**
     * 通过反射创建源质消耗栈，避免在 ThaumicEnergistics 未安装时类加载失败。
     *
     * @return 源质消耗列表；若 ThaumicEnergistics 缺失或任意源质栈无法创建则返回 null
     */
    private List<IAEStack> createEssentiaCosts(AspectList aspects, long count) {
        List<IAEStack> costs = new ArrayList<>();
        try {
            Class<?> essentiaStackClass = Class.forName("thaumicenergistics.api.EssentiaStack");
            Class<?> aeEssentiaStackClass = Class.forName("thaumicenergistics.integration.appeng.AEEssentiaStack");
            Constructor<?> ctor = essentiaStackClass.getConstructor(Aspect.class, int.class);
            Method fromEssentiaStack = aeEssentiaStackClass.getMethod("fromEssentiaStack", essentiaStackClass);

            for (Aspect aspect : aspects.getAspects()) {
                if (aspect == null) continue;
                int amount = aspects.getAmount(aspect);
                if (amount <= 0) continue;
                Object essStack = ctor.newInstance(aspect, (int) ((long) amount * count));
                Object aeStack = fromEssentiaStack.invoke(null, essStack);
                if (aeStack == null) {
                    return null;
                }
                costs.add((IAEStack) aeStack);
            }
        } catch (Exception e) {
            // ThaumicEnergistics 未安装或版本不兼容
            return null;
        }
        return costs;
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return collectAllPedestalItems(world, pos);
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();

        // 主材基座产物
        BlockPos mainPos = pos.down(2);
        TileEntity mainTe = world.getTileEntity(mainPos);
        if (CLASS_TILE_PEDESTAL.isInstance(mainTe)) {
            try {
                ItemStack stack = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(mainTe, 0);
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, ItemStack.EMPTY);
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // 辅材基座返还物品
        try {
            TileEntity te = world.getTileEntity(pos);
            if (CLASS_TILE_INFUSION_MATRIX.isInstance(te)) {
                @SuppressWarnings("unchecked")
                List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);
                for (BlockPos pPos : pedestals) {
                    TileEntity pTe = world.getTileEntity(pPos);
                    if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                    ItemStack stack = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0);
                    if (!stack.isEmpty()) {
                        result.add(stack.copy());
                        METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, ItemStack.EMPTY);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return result;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return collectAllPedestalItems(world, pos);
    }

    // ---- 配方查找(仅在 startProcess 回退路径中使用) ----

    /**
     * 从已放置到基座的物品反查注魔配方.
     * 用于 craftingStart 失败后的 FakePlayer research 授予和强制回退.
     */
    private static InfusionRecipe findRecipeFromPedestals(World world, BlockPos pos, TileEntity te) {
        // 读取主材
        BlockPos mainPos = pos.down(2);
        TileEntity mainTe = world.getTileEntity(mainPos);
        if (!CLASS_TILE_PEDESTAL.isInstance(mainTe)) return null;
        ItemStack mainItem;
        try {
            mainItem = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(mainTe, 0);
        } catch (Exception e) {
            return null;
        }
        if (mainItem.isEmpty()) return null;

        // 读取辅材
        List<ItemStack> auxItems = new ArrayList<>();
        try {
            METHOD_GET_SURROUNDINGS.invoke(te);
            @SuppressWarnings("unchecked")
            List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);
            for (BlockPos pPos : pedestals) {
                TileEntity pTe = world.getTileEntity(pPos);
                if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                ItemStack stack = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0);
                if (!stack.isEmpty()) {
                    auxItems.add(stack);
                }
            }
        } catch (Exception e) {
            return null;
        }

        // 遍历配方匹配
        Map<net.minecraft.util.ResourceLocation, IThaumcraftRecipe> recipes = ThaumcraftApi.getCraftingRecipes();
        for (IThaumcraftRecipe recipe : recipes.values()) {
            if (!(recipe instanceof InfusionRecipe)) continue;
            InfusionRecipe ir = (InfusionRecipe) recipe;
            Ingredient input = ir.getRecipeInput();
            NonNullList<Ingredient> components = ir.getComponents();
            if (input == null || components == null) continue;
            if (!input.apply(mainItem)) continue;
            if (auxItems.size() != components.size()) continue;
            if (RecipeMatcher.findMatches(auxItems, components) != null) {
                return ir;
            }
        }
        return null;
    }

    // ---- 辅助方法 ----

    private static boolean isActive(TileEntity te) {
        try {
            return FIELD_ACTIVE.getBoolean(te);
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isCrafting(TileEntity te) {
        try {
            return FIELD_CRAFTING.getBoolean(te);
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean validLocation(TileEntity te) {
        try {
            return (boolean) METHOD_VALID_LOCATION.invoke(te);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean clearAndCheckPedestals(World world, BlockPos pos, TileEntity te) {
        try {
            METHOD_GET_SURROUNDINGS.invoke(te);
            @SuppressWarnings("unchecked")
            List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);

            // 主材基座必须为空
            BlockPos mainPos = pos.down(2);
            TileEntity mainTe = world.getTileEntity(mainPos);
            if (!CLASS_TILE_PEDESTAL.isInstance(mainTe)) return false;
            if (!((ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(mainTe, 0)).isEmpty()) return false;

            // 辅材基座必须为空
            for (BlockPos pPos : pedestals) {
                TileEntity pTe = world.getTileEntity(pPos);
                if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                if (!((ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0)).isEmpty()) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 收集主材基座与所有辅材基座上的物品，用于 clearOutputs / revertMaterials。
     */
    private List<ItemStack> collectAllPedestalItems(World world, BlockPos pos) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();

        // 主材基座
        BlockPos mainPos = pos.down(2);
        TileEntity mainTe = world.getTileEntity(mainPos);
        if (CLASS_TILE_PEDESTAL.isInstance(mainTe)) {
            try {
                ItemStack stack = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(mainTe, 0);
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, ItemStack.EMPTY);
                }
            } catch (Exception ignored) {}
        }

        // 辅材基座
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_TILE_INFUSION_MATRIX.isInstance(te)) {
            try {
                METHOD_GET_SURROUNDINGS.invoke(te);
                @SuppressWarnings("unchecked")
                List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);
                for (BlockPos pPos : pedestals) {
                    TileEntity pTe = world.getTileEntity(pPos);
                    if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                    ItemStack stack = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0);
                    if (!stack.isEmpty()) {
                        result.add(stack.copy());
                        METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, ItemStack.EMPTY);
                    }
                }
            } catch (Exception ignored) {}
        }

        return result;
    }
}
