package com.github.aeddddd.ae2enhanced.centralinterface.handler.astralsorcery;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.HandlerUtils;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.github.aeddddd.ae2enhanced.storage.starlight.AEStarlightStack;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.EnumParticleTypes;

/**
 * Astral Sorcery 星辉祭坛远程处理器.
 *
 * <p>支持全部 5 个等级(DISCOVERY~BRILLIANCE).</p>
 *
 * <p><b>配方策略</b>：{@code canStart} 只做结构就绪检查(无进行中的合成、结构匹配、槽位可清空),
 * 不查配方；{@code pushMaterials} 在写入祭坛之前定位一次配方并缓存到 {@link TargetSession},
 * 然后按该配方的 slot 布局精确放置物品(包括 3x3 主网格与 Attunement/Constellation/Trait 额外槽位)；
 * {@code startProcess} 复用缓存配方直接创建 {@code ActiveCraftingTask} 并注入 TileAltar,
 * 绕过 FakePlayer 进度门控.</p>
 *
 * <p>主产物以样板声明的 {@code expectedOutputs[0]} 为准(首个产物即主产物),配方只用于
 * “按槽位放置”这一项放置位置知识。配方存在性与星能校验仍保留，但都下沉到
 * {@code pushMaterials} 的写入点之前：不匹配时材料尚未推进祭坛，由 dispatcher 立即回退，
 * 因此失败方式是当场拒绝。</p>
 */
public class AstralSorceryHandler implements IRemoteHandler, IVirtualBatchCraftingHandler {

    private static final String BLOCK_ID = "astralsorcery:blockaltar";
    private static final UUID FAKE_PLAYER_UUID = UUID.fromString("ae2e0000-fa2e-4e2e-ae2e-ae2e00000000");

    // 反射缓存
    private static Class<?> CLASS_TILE_ALTAR;
    private static Class<?> CLASS_ABSTRACT_ALTAR_RECIPE;
    private static Class<?> CLASS_ALTAR_RECIPE_REGISTRY;
    private static Class<?> CLASS_ITEM_HANDLE;
    private static Class<?> CLASS_ACTIVE_CRAFTING_TASK;
    private static Class<?> CLASS_ATTUNEMENT_RECIPE;
    private static Class<?> CLASS_CONSTELLATION_RECIPE;
    private static Class<?> CLASS_TRAIT_RECIPE;
    private static Class<?> CLASS_TILE_ATTUNEMENT_RELAY;
    private static Method METHOD_GET_INVENTORY_HANDLER;
    private static Method METHOD_RELAY_GET_INVENTORY_HANDLER;
    private static Method METHOD_GET_ACTIVE_CRAFTING_TASK;
    private static Method METHOD_GET_MULTIBLOCK_STATE;
    private static Method METHOD_GET_ALTAR_LEVEL;
    private static Method METHOD_MARK_FOR_UPDATE;
    private static Method METHOD_GET_NEEDED_LEVEL;
    private static Method METHOD_GET_OUTPUT_FOR_MATCHING;
    private static Method METHOD_GET_NATIVE_RECIPE;
    private static Method METHOD_FULFILLES_STARLIGHT;
    private static Method METHOD_FIND_MATCHING_RECIPE;
    private static Method METHOD_MATCH_CRAFTING;
    private static Method METHOD_GET_RECIPE_INGREDIENT;
    private static Method METHOD_CRAFTING_TICK_TIME;
    private static Method METHOD_GET_PASSIVE_STARLIGHT;
    private static Field FIELD_CRAFTING_TASK;
    private static Field FIELD_RECIPES_MAP;
    private static Field FIELD_ADDITIONAL_SLOTS;
    private static Field FIELD_MATCH_STACKS;
    private static Field FIELD_MATCH_TRAIT_STACKS;
    private static Field FIELD_ADDITIONALLY_REQUIRED_STACKS;
    private static Constructor<?> CTOR_ACTIVE_CRAFTING_TASK;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_ALTAR = Class.forName("hellfirepvp.astralsorcery.common.tile.TileAltar");
            CLASS_ABSTRACT_ALTAR_RECIPE = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.AbstractAltarRecipe");
            CLASS_ALTAR_RECIPE_REGISTRY = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.AltarRecipeRegistry");
            CLASS_ITEM_HANDLE = Class.forName("hellfirepvp.astralsorcery.common.crafting.ItemHandle");
            CLASS_ACTIVE_CRAFTING_TASK = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.ActiveCraftingTask");
            CLASS_ATTUNEMENT_RECIPE = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.recipes.AttunementRecipe");
            CLASS_CONSTELLATION_RECIPE = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.recipes.ConstellationRecipe");
            CLASS_TRAIT_RECIPE = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.recipes.TraitRecipe");
            CLASS_TILE_ATTUNEMENT_RELAY = Class.forName("hellfirepvp.astralsorcery.common.tile.TileAttunementRelay");

            METHOD_GET_INVENTORY_HANDLER = CLASS_TILE_ALTAR.getMethod("getInventoryHandler");
            METHOD_RELAY_GET_INVENTORY_HANDLER = CLASS_TILE_ATTUNEMENT_RELAY.getMethod("getInventoryHandler");
            METHOD_GET_ACTIVE_CRAFTING_TASK = CLASS_TILE_ALTAR.getMethod("getActiveCraftingTask");
            METHOD_GET_MULTIBLOCK_STATE = CLASS_TILE_ALTAR.getMethod("getMultiblockState");
            METHOD_GET_ALTAR_LEVEL = CLASS_TILE_ALTAR.getMethod("getAltarLevel");
            METHOD_MARK_FOR_UPDATE = CLASS_TILE_ALTAR.getMethod("markForUpdate");

            METHOD_GET_NEEDED_LEVEL = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("getNeededLevel");
            METHOD_GET_OUTPUT_FOR_MATCHING = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("getOutputForMatching");
            METHOD_GET_NATIVE_RECIPE = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("getNativeRecipe");
            METHOD_FULFILLES_STARLIGHT = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("fulfillesStarlightRequirement", CLASS_TILE_ALTAR);
            METHOD_CRAFTING_TICK_TIME = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("craftingTickTime");
            METHOD_GET_PASSIVE_STARLIGHT = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("getPassiveStarlightRequired");

            METHOD_FIND_MATCHING_RECIPE = CLASS_ALTAR_RECIPE_REGISTRY.getMethod("findMatchingRecipe", CLASS_TILE_ALTAR, boolean.class);
            FIELD_RECIPES_MAP = CLASS_ALTAR_RECIPE_REGISTRY.getField("recipes");

            METHOD_MATCH_CRAFTING = CLASS_ITEM_HANDLE.getMethod("matchCrafting", ItemStack.class);
            METHOD_GET_RECIPE_INGREDIENT = CLASS_ITEM_HANDLE.getMethod("getRecipeIngredient");

            FIELD_ADDITIONAL_SLOTS = CLASS_ATTUNEMENT_RECIPE.getDeclaredField("additionalSlots");
            FIELD_ADDITIONAL_SLOTS.setAccessible(true);

            FIELD_MATCH_STACKS = CLASS_CONSTELLATION_RECIPE.getDeclaredField("matchStacks");
            FIELD_MATCH_STACKS.setAccessible(true);

            FIELD_MATCH_TRAIT_STACKS = CLASS_TRAIT_RECIPE.getDeclaredField("matchTraitStacks");
            FIELD_MATCH_TRAIT_STACKS.setAccessible(true);

            FIELD_ADDITIONALLY_REQUIRED_STACKS = CLASS_TRAIT_RECIPE.getDeclaredField("additionallyRequiredStacks");
            FIELD_ADDITIONALLY_REQUIRED_STACKS.setAccessible(true);

            FIELD_CRAFTING_TASK = CLASS_TILE_ALTAR.getDeclaredField("craftingTask");
            FIELD_CRAFTING_TASK.setAccessible(true);

            CTOR_ACTIVE_CRAFTING_TASK = CLASS_ACTIVE_CRAFTING_TASK.getConstructor(CLASS_ABSTRACT_ALTAR_RECIPE, int.class, UUID.class);

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] AstralSorceryHandler reflection init failed", e);
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
        return CLASS_TILE_ALTAR.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ALTAR.isInstance(te)) return false;

        // 必须空闲
        if (getActiveCraftingTask(te) != null) return false;
        // 结构必须匹配
        if (!getMultiblockState(te)) return false;

        // 祭坛所有 slot 必须为空；如有残留物品则尝试清空
        // 注意：matches() 检查的是 handler.getSlots() 中的所有 slot,不能仅清空 accessibleSize
        IItemHandler handler = getInventoryHandler(te);
        if (handler == null) return false;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack current = handler.getStackInSlot(i);
            if (!current.isEmpty()) {
                ItemStack extracted = handler.extractItem(i, current.getCount(), false);
                if (!handler.getStackInSlot(i).isEmpty()) {
                    return false; // 无法清空,祭坛被占用
                }
            }
        }

        // 本方法只做结构就绪检查：配方是否存在、星能是否满足都下沉到 pushMaterials 的写入点之前，
        // 避免每个候选目标在 canStart 阶段都做一次祭坛配方表遍历。
        return true;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ALTAR.isInstance(te)) return false;

        IItemHandler handler = getInventoryHandler(te);
        if (handler == null) return false;

        Object recipe = session != null ? session.getRecipeCache() : null;
        if (recipe == null) {
            // 唯一保留的配方定位：主产物取样板声明的首个产物，配方只用于槽位放置。
            recipe = findRecipeForBatch(te, ingredients, mainOutput(session));
            if (recipe == null) return false;
        }

        // 以下两项校验必须在任何写入之前完成，保证失败时祭坛仍为空、材料可被完整回退
        if (!fulfillesStarlightRequirement(recipe, te)) return false;
        if (!ingredientsMatch(recipe, ingredients)) return false;

        if (session != null) {
            session.setRecipeCache(recipe);
        }

        // 清空祭坛所有 slot(matches() 会检查 handler 的全部 slot)
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack current = handler.getStackInSlot(i);
            if (!current.isEmpty()) {
                handler.extractItem(i, current.getCount(), false);
            }
        }

        // 收集可用物品(AE 样板中的所有非空物品)
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                available.add(stack.copy());
            }
        }

        // 放置 3x3 部分：按配方的 Ingredient 顺序从 available 中匹配并放置
        Object nativeRecipe = getNativeRecipe(recipe);
        if (nativeRecipe != null) {
            NonNullList<Ingredient> recipeIngs = ((net.minecraft.item.crafting.IRecipe) nativeRecipe).getIngredients();
            for (int slot = 0; slot < Math.min(recipeIngs.size(), 9); slot++) {
                Ingredient ing = recipeIngs.get(slot);
                if (ing == null || ing == Ingredient.EMPTY) continue;
                for (int i = 0; i < available.size(); i++) {
                    ItemStack stack = available.get(i);
                    if (ing.apply(stack)) {
                        ItemStack remainder = handler.insertItem(slot, stack.copy(), false);
                        if (!remainder.isEmpty()) {
                            fallbackSetStack(handler, slot, stack.copy());
                        }
                        available.remove(i);
                        break;
                    }
                }
            }
        }

        // 放置额外槽位(Attunement / Constellation / Trait)
        placeExtraSlots(recipe, available, handler, CLASS_ATTUNEMENT_RECIPE, FIELD_ADDITIONAL_SLOTS);
        placeExtraSlots(recipe, available, handler, CLASS_CONSTELLATION_RECIPE, FIELD_MATCH_STACKS);
        placeExtraSlots(recipe, available, handler, CLASS_TRAIT_RECIPE, FIELD_MATCH_TRAIT_STACKS);

        // 放置 TraitRecipe 的外部 additionallyRequiredStacks(光波增幅器 / AttunementRelay)
        placeOuterStacks(world, pos, recipe, available);

        // 所有输入物品必须被放置；剩余表示匹配失败，由 dispatcher 调用 revertMaterials 回退
        return available.isEmpty();
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ALTAR.isInstance(te)) return false;

        // 已经处于合成中
        if (getActiveCraftingTask(te) != null) {
            if (session != null) session.clearRecipeCache();
            return true;
        }

        try {
            // 直接使用 canStart/pushMaterials 已验证的配方创建 ActiveCraftingTask
            Object recipe = session != null ? session.getRecipeCache() : null;
            if (recipe == null) {
                // 回退：通过 findMatchingRecipe 查找(此时物品已放置好,应该能找到)
                recipe = METHOD_FIND_MATCHING_RECIPE.invoke(null, te, false);
            }
            if (recipe == null) {
                if (session != null) session.clearRecipeCache();
                return false;
            }

            Object altarLevel = METHOD_GET_ALTAR_LEVEL.invoke(te);
            Object neededLevel = METHOD_GET_NEEDED_LEVEL.invoke(recipe);
            int diff = Math.max(0, ((Enum<?>) altarLevel).ordinal() - ((Enum<?>) neededLevel).ordinal());
            int multiplier = (int) Math.round(Math.pow(2, diff));

            Object task = CTOR_ACTIVE_CRAFTING_TASK.newInstance(recipe, multiplier, FAKE_PLAYER_UUID);
            FIELD_CRAFTING_TASK.set(te, task);
            METHOD_MARK_FOR_UPDATE.invoke(te);
            if (session != null) session.clearRecipeCache();
            return true;
        } catch (Exception e) {
            if (session != null) session.clearRecipeCache();
            return false;
        }
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ALTAR.isInstance(te)) return false;
        return getActiveCraftingTask(te) == null;
    }

    // ---- IVirtualCraftingHandler / IVirtualBatchCraftingHandler ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        initReflection();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ALTAR.isInstance(te)) return false;

        ItemStack expected = outputs[0].createItemStack();
        Object recipe = findAltarRecipeByOutput(expected);
        if (recipe == null) return false;

        List<Ingredient> required = collectRequiredIngredients(recipe);
        return matchIngredients(required, ingredients);
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
        initReflection();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;

        Object recipe = findAltarRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return costs;

        List<Ingredient> required = collectRequiredIngredients(recipe);
        List<ItemStack> available = HandlerUtils.collectNonEmpty(ingredients);
        for (Ingredient ing : required) {
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

        int starlight = getPassiveStarlightRequired(recipe);
        if (starlight > 0) {
            costs.add(AEStarlightStack.create((long) starlight * count));
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

    @SuppressWarnings("unchecked")
    private Object findAltarRecipeByOutput(ItemStack output) {
        if (output.isEmpty()) return null;
        try {
            Map<?, List<?>> recipes = (Map<?, List<?>>) FIELD_RECIPES_MAP.get(null);
            if (recipes == null) return null;
            for (List<?> list : recipes.values()) {
                for (Object recipe : list) {
                    ItemStack recipeOutput = (ItemStack) METHOD_GET_OUTPUT_FOR_MATCHING.invoke(recipe);
                    if (!recipeOutput.isEmpty()
                            && recipeOutput.getItem() == output.getItem()
                            && recipeOutput.getMetadata() == output.getMetadata()) {
                        return recipe;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private List<Ingredient> collectRequiredIngredients(Object recipe) {
        List<Ingredient> required = new ArrayList<>();
        try {
            Object nativeRecipe = METHOD_GET_NATIVE_RECIPE.invoke(recipe);
            if (nativeRecipe instanceof net.minecraft.item.crafting.IRecipe) {
                for (Ingredient ing : ((net.minecraft.item.crafting.IRecipe) nativeRecipe).getIngredients()) {
                    if (ing != null && ing != Ingredient.EMPTY) {
                        required.add(ing);
                    }
                }
            }

            addHandleIngredients(required, recipe, CLASS_ATTUNEMENT_RECIPE, FIELD_ADDITIONAL_SLOTS);
            addHandleIngredients(required, recipe, CLASS_CONSTELLATION_RECIPE, FIELD_MATCH_STACKS);
            addHandleIngredients(required, recipe, CLASS_TRAIT_RECIPE, FIELD_MATCH_TRAIT_STACKS);

            // TraitRecipe 外部 additionallyRequiredStacks(光波增幅器 / AttunementRelay)
            addHandleIngredients(required, recipe, CLASS_TRAIT_RECIPE, FIELD_ADDITIONALLY_REQUIRED_STACKS);
        } catch (Exception e) {
            // ignore
        }
        return required;
    }

    /**
     * 把配方某字段中的 ItemHandle 转成 Ingredient 追加到 required.
     *
     * <p>map 字段(AttunementRecipe#additionalSlots、ConstellationRecipe#matchStacks、
     * TraitRecipe#matchTraitStacks)与 list 字段(TraitRecipe#additionallyRequiredStacks)
     * 的遍历逻辑一致,仅容器类型不同.</p>
     *
     * <p>字段不可访问时抛出异常,由调用方决定是整体放弃(严格路径)还是跳过(容错路径).</p>
     */
    @SuppressWarnings("unchecked")
    private static void addHandleIngredients(List<Ingredient> required, Object recipe, Class<?> recipeClass, Field field) throws Exception {
        if (!recipeClass.isInstance(recipe) || field == null) return;
        Object value = field.get(recipe);
        if (value == null) return;
        Collection<?> handles = value instanceof Map
                ? ((Map<Object, Object>) value).values()
                : (Collection<?>) value;
        for (Object handle : handles) {
            if (handle == null) continue;
            Ingredient ing = (Ingredient) METHOD_GET_RECIPE_INGREDIENT.invoke(handle);
            if (ing != null && ing != Ingredient.EMPTY) {
                required.add(ing);
            }
        }
    }

    /** {@link #addHandleIngredients} 的容错包装：单个字段反射失败时跳过,不影响其余槽位收集. */
    private static void addHandleIngredientsLeniently(List<Ingredient> required, Object recipe, Class<?> recipeClass, Field field) {
        try {
            addHandleIngredients(required, recipe, recipeClass, field);
        } catch (Exception ignored) {
            // ignore
        }
    }

    private boolean matchIngredients(List<Ingredient> required, InventoryCrafting ingredients) {
        List<ItemStack> available = HandlerUtils.collectNonEmpty(ingredients);
        for (Ingredient ing : required) {
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
        return available.isEmpty();
    }

    private int getPassiveStarlightRequired(Object recipe) {
        try {
            return (int) METHOD_GET_PASSIVE_STARLIGHT.invoke(recipe);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();

        // 产物掉落在祭坛上方 y+1.3 附近
        AxisAlignedBB aabb = new AxisAlignedBB(
            pos.getX(), pos.getY() + 1.0, pos.getZ(),
            pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0
        );
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, aabb);
        for (EntityItem entity : items) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;
            if (expectedOutputs != null && expectedOutputs.length > 0 && expectedOutputs[0] != null) {
                if (outputsMatch(stack, expectedOutputs[0].createItemStack())) {
                    result.add(stack.copy());
                    entity.setDead();
                }
            } else {
                result.add(stack.copy());
                entity.setDead();
            }
        }

        // 清空祭坛所有 slot,防止残留物品阻塞下一次推送
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_TILE_ALTAR.isInstance(te)) {
            // 双重校验：确保确实不在合成中,避免过早清空未完成的材料
            if (getActiveCraftingTask(te) == null) {
                IItemHandler handler = getInventoryHandler(te);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack current = handler.getStackInSlot(i);
                        if (!current.isEmpty()) {
                            result.add(current.copy());
                            handler.extractItem(i, current.getCount(), false);
                        }
                    }
                }
            }
        }

        return result;
    }

    // ---- 配方查找与匹配 ----

    /**
     * 取本批样板声明的主产物(首个产物).
     *
     * <p>样板声明的首个产物即主产物。</p>
     */
    private static ItemStack mainOutput(TargetSession session) {
        IAEItemStack[] expected = session != null ? session.getExpectedOutputs() : null;
        if (expected == null || expected.length == 0 || expected[0] == null) {
            return ItemStack.EMPTY;
        }
        return expected[0].createItemStack();
    }

    /**
     * 定位本批要按槽位放置的祭坛配方.
     *
     * <p>只遍历祭坛配方表一次：优先返回“输出与样板主产物一致且输入可满足”的配方，
     * 无输出一致项时退回“仅输入可满足”的配方。
     * 只搜索当前祭坛等级及以下的配方。</p>
     *
     * @param mainOutput 样板声明的主产物；为空时退化为仅按输入匹配
     * @return 匹配到的配方；无匹配返回 null
     */
    @SuppressWarnings("unchecked")
    private static Object findRecipeForBatch(TileEntity altar, InventoryCrafting ingredients, ItemStack mainOutput) {
        try {
            Map<Object, List<Object>> recipesMap = (Map<Object, List<Object>>) FIELD_RECIPES_MAP.get(null);
            if (recipesMap == null) return null;

            Object altarLevel = METHOD_GET_ALTAR_LEVEL.invoke(altar);
            int altarOrdinal = ((Enum<?>) altarLevel).ordinal();

            Object ingredientsOnlyMatch = null;
            for (Map.Entry<Object, List<Object>> entry : recipesMap.entrySet()) {
                Object level = entry.getKey();
                int levelOrdinal = ((Enum<?>) level).ordinal();
                if (levelOrdinal > altarOrdinal) continue;

                for (Object recipe : entry.getValue()) {
                    if (!ingredientsMatch(recipe, ingredients)) continue;
                    if (!mainOutput.isEmpty() && recipeOutputMatches(recipe, mainOutput)) {
                        return recipe;
                    }
                    if (ingredientsOnlyMatch == null) {
                        ingredientsOnlyMatch = recipe;
                    }
                }
            }
            return ingredientsOnlyMatch;
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /** 配方输出是否与样板声明的主产物同类(忽略 NBT：星辉产物可能带随机 NBT)。 */
    private static boolean recipeOutputMatches(Object recipe, ItemStack mainOutput) {
        try {
            ItemStack recipeOutput = (ItemStack) METHOD_GET_OUTPUT_FOR_MATCHING.invoke(recipe);
            return !recipeOutput.isEmpty()
                    && recipeOutput.getItem() == mainOutput.getItem()
                    && recipeOutput.getMetadata() == mainOutput.getMetadata();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean ingredientsMatch(Object recipe, InventoryCrafting ingredients) {
        try {
            Object nativeRecipe = getNativeRecipe(recipe);
            if (nativeRecipe == null) return false;

            NonNullList<Ingredient> recipeIngs = ((net.minecraft.item.crafting.IRecipe) nativeRecipe).getIngredients();
            List<Ingredient> required = new ArrayList<>();
            for (Ingredient ing : recipeIngs) {
                if (ing != null && ing != Ingredient.EMPTY) {
                    required.add(ing);
                }
            }

            // AttunementRecipe 额外 slot
            addHandleIngredientsLeniently(required, recipe, CLASS_ATTUNEMENT_RECIPE, FIELD_ADDITIONAL_SLOTS);

            // ConstellationRecipe 额外 slot
            addHandleIngredientsLeniently(required, recipe, CLASS_CONSTELLATION_RECIPE, FIELD_MATCH_STACKS);

            // TraitRecipe 额外 slot(内部 matchTraitStacks)
            addHandleIngredientsLeniently(required, recipe, CLASS_TRAIT_RECIPE, FIELD_MATCH_TRAIT_STACKS);

            // TraitRecipe 外部 additionallyRequiredStacks(光波增幅器 / AttunementRelay)
            addHandleIngredientsLeniently(required, recipe, CLASS_TRAIT_RECIPE, FIELD_ADDITIONALLY_REQUIRED_STACKS);

            List<ItemStack> available = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    available.add(stack.copy());
                }
            }

            if (required.size() != available.size()) return false;

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

    @SuppressWarnings("unchecked")
    private static void placeExtraSlots(Object recipe, List<ItemStack> available, IItemHandler handler, Class<?> recipeClass, Field field) {
        try {
            if (!recipeClass.isInstance(recipe)) return;
            Map<Object, Object> slots = (Map<Object, Object>) field.get(recipe);
            if (slots == null) return;
            for (Map.Entry<Object, Object> entry : slots.entrySet()) {
                Object slotEnum = entry.getKey();
                Object itemHandle = entry.getValue();
                if (slotEnum == null || itemHandle == null) continue;
                int slotId = (int) slotEnum.getClass().getMethod("getSlotId").invoke(slotEnum);
                for (int i = 0; i < available.size(); i++) {
                    ItemStack stack = available.get(i);
                    boolean match = (boolean) METHOD_MATCH_CRAFTING.invoke(itemHandle, stack);
                    if (match) {
                        ItemStack remainder = handler.insertItem(slotId, stack.copy(), false);
                        if (!remainder.isEmpty()) {
                            fallbackSetStack(handler, slotId, stack.copy());
                        }
                        available.remove(i);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static void placeOuterStacks(World world, BlockPos altarPos, Object recipe, List<ItemStack> available) {
        try {
            if (!CLASS_TRAIT_RECIPE.isInstance(recipe)) return;
            List<Object> outerStacks = (List<Object>) FIELD_ADDITIONALLY_REQUIRED_STACKS.get(recipe);
            if (outerStacks == null || outerStacks.isEmpty()) return;

            // 收集祭坛周围空的 TileAttunementRelay
            List<TileEntity> emptyRelays = new ArrayList<>();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos relayPos = altarPos.add(dx, 0, dz);
                    TileEntity te = world.getTileEntity(relayPos);
                    if (CLASS_TILE_ATTUNEMENT_RELAY.isInstance(te)) {
                        IItemHandler relayHandler = (IItemHandler) METHOD_RELAY_GET_INVENTORY_HANDLER.invoke(te);
                        if (relayHandler != null && relayHandler.getStackInSlot(0).isEmpty()) {
                            emptyRelays.add(te);
                        }
                    }
                }
            }

            for (int i = 0; i < outerStacks.size() && i < emptyRelays.size(); i++) {
                Object itemHandle = outerStacks.get(i);
                if (itemHandle == null) continue;
                for (int j = 0; j < available.size(); j++) {
                    ItemStack stack = available.get(j);
                    boolean match = (boolean) METHOD_MATCH_CRAFTING.invoke(itemHandle, stack);
                    if (match) {
                        TileEntity relay = emptyRelays.get(i);
                        IItemHandler relayHandler = (IItemHandler) METHOD_RELAY_GET_INVENTORY_HANDLER.invoke(relay);
                        if (relayHandler != null) {
                            ItemStack toInsert = stack.copy();
                            toInsert.setCount(1);
                            ItemStack remainder = relayHandler.insertItem(0, toInsert, false);
                            if (!remainder.isEmpty()) {
                                fallbackSetStack(relayHandler, 0, toInsert);
                            }
                            available.remove(j);
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static void fallbackSetStack(IItemHandler handler, int slot, ItemStack stack) {
        try {
            if (handler instanceof IItemHandlerModifiable) {
                ((IItemHandlerModifiable) handler).setStackInSlot(slot, stack);
            }
        } catch (Exception ignored) {}
    }

    private static Object getNativeRecipe(Object recipe) {
        try {
            return METHOD_GET_NATIVE_RECIPE.invoke(recipe);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean fulfillesStarlightRequirement(Object recipe, TileEntity altar) {
        try {
            return (boolean) METHOD_FULFILLES_STARLIGHT.invoke(recipe, altar);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 辅助方法 ----

    private static Object getActiveCraftingTask(TileEntity te) {
        try {
            return METHOD_GET_ACTIVE_CRAFTING_TASK.invoke(te);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean getMultiblockState(TileEntity te) {
        try {
            return (boolean) METHOD_GET_MULTIBLOCK_STATE.invoke(te);
        } catch (Exception e) {
            return false;
        }
    }

    private static IItemHandler getInventoryHandler(TileEntity te) {
        try {
            return (IItemHandler) METHOD_GET_INVENTORY_HANDLER.invoke(te);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean outputsMatch(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        if (a.getMetadata() != b.getMetadata()) return false;
        // 星辉产物可能带有随机 NBT(如 Rock Crystal),回收时忽略 NBT
        return true;
    }
}
