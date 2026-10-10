package com.github.aeddddd.ae2enhanced.centralinterface.handler.botania;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.reflection.ReflectionCache;
import com.github.aeddddd.ae2enhanced.mixin.late.botania.ITileAltarAccessor;
import com.github.aeddddd.ae2enhanced.mixin.late.botania.ITilePoolAccessor;
import com.github.aeddddd.ae2enhanced.mixin.late.botania.ITileRuneAltarAccessor;
import com.github.aeddddd.ae2enhanced.mixin.late.botania.ITileSimpleInventoryAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Botania Handler 的反射辅助类.
 *
 * 缓存对 Botania 全部使用到的类/方法/字段的反射引用,避免重复反射带来的性能损耗.
 * 查找统一走 {@link ReflectionCache}:全部缓存由静态字段初始化一次性完成,失败时只记一条警告、
 * 不抛异常,未命中的缓存项保持 null,由对应 handler 自行降级
 * (isValidTarget 回退到类型检查 / 配方无法计价).
 *
 * 本类自身不引用任何 vazkii.botania 类型,因此可以安全地被无条件加载.
 *
 * <p><b>服务端崩溃注意事项</b>:TilePool / TileRuneAltar / TileAltar 均声明了
 * renderHUD(Minecraft, ScaledResolution) 客户端专属方法。JVM 在执行
 * getMethod/getDeclaredMethod 时会解析目标类全部已声明方法的签名,
 * 在 Dedicated Server 上必然抛出 NoClassDefFoundError: net/minecraft/client/Minecraft。
 * 因此这三个类(以及其父类 TileSimpleInventory 的 getItemHandler)的<b>方法</b>一律通过
 * mixin.late.botania 包下的 Invoker 接口访问;<b>字段</b>类型均不引用客户端类,
 * getDeclaredField 不触发方法签名解析,可安全保留反射.</p>
 */
public class BotaniaReflectionHelper {

    // ---- TilePool (方法见 ITilePoolAccessor) ----
    public static final Class<?> CLASS_TILE_POOL =
            ReflectionCache.clazz("vazkii.botania.common.block.tile.mana.TilePool");

    // ---- TileAlfPortal ----
    public static final Class<?> CLASS_TILE_ALF_PORTAL =
            ReflectionCache.clazz("vazkii.botania.common.block.tile.TileAlfPortal");

    // ---- TileTerraPlate ----
    public static final Class<?> CLASS_TILE_TERRA_PLATE =
            ReflectionCache.clazz("vazkii.botania.common.block.tile.TileTerraPlate");
    public static final Method METHOD_HAS_VALID_PLATFORM =
            ReflectionCache.declaredMethod(CLASS_TILE_TERRA_PLATE, "hasValidPlatform");
    public static final Method METHOD_GET_ITEMS =
            ReflectionCache.declaredMethod(CLASS_TILE_TERRA_PLATE, "getItems");

    // ---- TileRuneAltar (方法见 ITileRuneAltarAccessor / ITileSimpleInventoryAccessor) ----
    public static final Class<?> CLASS_TILE_RUNE_ALTAR =
            ReflectionCache.clazz("vazkii.botania.common.block.tile.TileRuneAltar");
    public static final Field FIELD_CURRENT_RECIPE =
            ReflectionCache.declaredField(CLASS_TILE_RUNE_ALTAR, "currentRecipe");
    public static final Field FIELD_COOLDOWN =
            ReflectionCache.declaredField(CLASS_TILE_RUNE_ALTAR, "cooldown");
    public static final Field FIELD_MANA =
            ReflectionCache.declaredField(CLASS_TILE_RUNE_ALTAR, "mana");
    public static final Field FIELD_MANA_TO_GET =
            ReflectionCache.field(CLASS_TILE_RUNE_ALTAR, "manaToGet");

    // ---- TileAltar (方法见 ITileAltarAccessor / ITileSimpleInventoryAccessor) ----
    public static final Class<?> CLASS_TILE_ALTAR =
            ReflectionCache.clazz("vazkii.botania.common.block.tile.TileAltar");

    // ---- BotaniaStateProps / AlfPortalState ----
    @SuppressWarnings("rawtypes")
    public static final IProperty ALFPORTAL_STATE_PROP =
            (IProperty) readStaticField("vazkii.botania.api.state.BotaniaStateProps", "ALFPORTAL_STATE");
    public static final Object ALFPORTAL_STATE_OFF =
            readStaticField("vazkii.botania.api.state.enums.AlfPortalState", "OFF");

    // ---- ModBlocks / ModItems (vazkii.botania.common) ----
    public static final Block BLOCK_LIVINGROCK =
            (Block) readStaticField("vazkii.botania.common.block.ModBlocks", "livingrock");
    public static final Block BLOCK_RUNE_ALTAR =
            (Block) readStaticField("vazkii.botania.common.block.ModBlocks", "runeAltar");
    public static final Item ITEM_MANA_RESOURCE =
            (Item) readStaticField("vazkii.botania.common.item.ModItems", "manaResource");
    public static final Item ITEM_RUNE =
            (Item) readStaticField("vazkii.botania.common.item.ModItems", "rune");
    public static final Item ITEM_LEXICON =
            (Item) readStaticField("vazkii.botania.common.item.ModItems", "lexicon");

    // ---- BotaniaAPI 配方列表(static final List,引用稳定,直接缓存) ----
    public static final List<?> PETAL_RECIPES =
            (List<?>) readStaticField("vazkii.botania.api.BotaniaAPI", "petalRecipes");
    public static final List<?> MANA_INFUSION_RECIPES =
            (List<?>) readStaticField("vazkii.botania.api.BotaniaAPI", "manaInfusionRecipes");
    public static final List<?> RUNE_ALTAR_RECIPES =
            (List<?>) readStaticField("vazkii.botania.api.BotaniaAPI", "runeAltarRecipes");
    public static final List<?> ELVEN_TRADE_RECIPES =
            (List<?>) readStaticField("vazkii.botania.api.BotaniaAPI", "elvenTradeRecipes");

    // ---- Recipe 类 ----
    public static final Class<?> CLASS_RECIPE_RUNE_ALTAR =
            ReflectionCache.clazz("vazkii.botania.api.recipe.RecipeRuneAltar");

    private static final Class<?> CLASS_RECIPE_MANA_INFUSION =
            ReflectionCache.clazz("vazkii.botania.api.recipe.RecipeManaInfusion");
    private static final Class<?> CLASS_RECIPE_ELVEN_TRADE =
            ReflectionCache.clazz("vazkii.botania.api.recipe.RecipeElvenTrade");
    private static final Class<?> CLASS_RECIPE_PETALS =
            ReflectionCache.clazz("vazkii.botania.api.recipe.RecipePetals");

    public static final Method METHOD_MANA_INFUSION_GET_INPUT =
            ReflectionCache.method(CLASS_RECIPE_MANA_INFUSION, "getInput");
    public static final Method METHOD_MANA_INFUSION_GET_OUTPUT =
            ReflectionCache.method(CLASS_RECIPE_MANA_INFUSION, "getOutput");
    public static final Method METHOD_MANA_INFUSION_GET_MANA_TO_CONSUME =
            ReflectionCache.method(CLASS_RECIPE_MANA_INFUSION, "getManaToConsume");
    public static final Method METHOD_ELVEN_TRADE_GET_INPUTS =
            ReflectionCache.method(CLASS_RECIPE_ELVEN_TRADE, "getInputs");
    public static final Method METHOD_ELVEN_TRADE_GET_OUTPUTS =
            ReflectionCache.method(CLASS_RECIPE_ELVEN_TRADE, "getOutputs");
    public static final Method METHOD_PETALS_MATCHES =
            ReflectionCache.method(CLASS_RECIPE_PETALS, "matches", IItemHandler.class);
    public static final Method METHOD_PETALS_GET_INPUTS =
            ReflectionCache.method(CLASS_RECIPE_PETALS, "getInputs");
    public static final Method METHOD_PETALS_GET_OUTPUT =
            ReflectionCache.method(CLASS_RECIPE_PETALS, "getOutput");
    public static final Method METHOD_RUNE_ALTAR_GET_MANA_USAGE =
            ReflectionCache.method(CLASS_RECIPE_RUNE_ALTAR, "getManaUsage");

    static {
        // 查找失败只 warn 不抛 —— 缺 Botania / 版本不匹配时各缓存项保持 null,
        // 由各 handler 自行降级(类型检查 / 拒绝计价);逐项未命中的原因由 ReflectionCache 按 debug 记录。
        if (CLASS_TILE_POOL == null || METHOD_HAS_VALID_PLATFORM == null || FIELD_MANA_TO_GET == null
                || CLASS_TILE_ALTAR == null || ALFPORTAL_STATE_PROP == null || ITEM_RUNE == null
                || PETAL_RECIPES == null || METHOD_PETALS_MATCHES == null
                || METHOD_RUNE_ALTAR_GET_MANA_USAGE == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] BotaniaReflectionHelper: Botania 反射缓存不完整"
                    + "(未安装或版本不匹配),相关方块将退化为类型检查且无法计价");
        }
    }

    /**
     * 读取静态字段的值(ModBlocks / ModItems / BotaniaAPI / 状态枚举这类"缓存对象引用"的场景).
     *
     * @return 字段值;类或字段不存在、或读取抛异常时返回 null
     */
    @Nullable
    private static Object readStaticField(String ownerClassName, String fieldName) {
        Field field = ReflectionCache.field(ReflectionCache.clazz(ownerClassName), fieldName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 通用调用封装 ----

    public static boolean isInstance(Class<?> clazz, Object obj) {
        return clazz != null && clazz.isInstance(obj);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            throw new IllegalStateException("[AE2E] BotaniaReflectionHelper: method not cached");
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[AE2E] BotaniaReflectionHelper invoke failed: " + method, e);
        }
    }

    private static boolean invokeBool(Method method, Object target, Object... args) {
        return (Boolean) invoke(method, target, args);
    }

    private static int invokeInt(Method method, Object target, Object... args) {
        return (Integer) invoke(method, target, args);
    }

    // ---- TilePool ----

    public static Object getPoolMatchingRecipe(ItemStack stack, IBlockState state) {
        return ITilePoolAccessor.ae2e$getMatchingRecipe(stack, state);
    }

    public static int getPoolCurrentMana(Object pool) {
        return ((ITilePoolAccessor) pool).ae2e$getCurrentMana();
    }

    public static boolean poolCollideEntityItem(Object pool, EntityItem item) {
        return ((ITilePoolAccessor) pool).ae2e$collideEntityItem(item);
    }

    // ---- TileRuneAltar ----

    public static int getRuneAltarCooldown(Object altar) {
        if (FIELD_COOLDOWN == null) return 0;
        try {
            return FIELD_COOLDOWN.getInt(altar);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    public static int getRuneAltarMana(Object altar) {
        if (FIELD_MANA == null) return runeAltarGetCurrentMana(altar);
        try {
            return FIELD_MANA.getInt(altar);
        } catch (IllegalAccessException e) {
            return runeAltarGetCurrentMana(altar);
        }
    }

    public static Object getRuneAltarCurrentRecipe(Object altar) {
        if (FIELD_CURRENT_RECIPE == null) return null;
        try {
            return FIELD_CURRENT_RECIPE.get(altar);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    public static void setRuneAltarCurrentRecipe(Object altar, Object recipe) {
        if (FIELD_CURRENT_RECIPE == null) return;
        try {
            FIELD_CURRENT_RECIPE.set(altar, recipe);
        } catch (IllegalAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to set RuneAltar currentRecipe", e);
        }
    }

    public static int runeAltarGetManaToGet(Object altar) {
        if (FIELD_MANA_TO_GET == null) {
            throw new IllegalStateException("[AE2E] BotaniaReflectionHelper: manaToGet field not cached");
        }
        try {
            return FIELD_MANA_TO_GET.getInt(altar);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("[AE2E] BotaniaReflectionHelper failed to read manaToGet", e);
        }
    }

    public static boolean runeAltarIsEmpty(Object altar) {
        return ((ITileRuneAltarAccessor) altar).ae2e$isEmpty();
    }

    public static boolean runeAltarAddItem(Object altar, ItemStack stack) {
        return ((ITileRuneAltarAccessor) altar).ae2e$addItem(null, stack, null);
    }

    public static IItemHandlerModifiable runeAltarGetItemHandler(Object altar) {
        return ((ITileSimpleInventoryAccessor) altar).ae2e$getItemHandler();
    }

    public static int runeAltarGetCurrentMana(Object altar) {
        return ((ITileRuneAltarAccessor) altar).ae2e$getCurrentMana();
    }

    public static void runeAltarRecieveMana(Object altar, int mana) {
        ((ITileRuneAltarAccessor) altar).ae2e$recieveMana(mana);
    }

    public static void runeAltarSaveLastRecipe(Object altar) {
        ((ITileRuneAltarAccessor) altar).ae2e$saveLastRecipe();
    }

    public static int runeAltarGetSizeInventory(Object altar) {
        return ((ITileRuneAltarAccessor) altar).ae2e$getSizeInventory();
    }

    // ---- TileAltar ----

    public static boolean altarIsEmpty(Object altar) {
        return ((ITileAltarAccessor) altar).ae2e$isEmpty();
    }

    public static boolean altarHasLava(Object altar) {
        return ((ITileAltarAccessor) altar).ae2e$hasLava();
    }

    public static boolean altarHasWater(Object altar) {
        return ((ITileAltarAccessor) altar).ae2e$hasWater();
    }

    public static void altarSetWater(Object altar, boolean water) {
        ((ITileAltarAccessor) altar).ae2e$setWater(water);
    }

    public static boolean altarCollideEntityItem(Object altar, EntityItem item) {
        return ((ITileAltarAccessor) altar).ae2e$collideEntityItem(item);
    }

    public static IItemHandlerModifiable altarGetItemHandler(Object altar) {
        return ((ITileSimpleInventoryAccessor) altar).ae2e$getItemHandler();
    }

    // ---- BotaniaStateProps ----

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object getAlfPortalState(IBlockState state) {
        if (ALFPORTAL_STATE_PROP == null) return null;
        return state.getValue(ALFPORTAL_STATE_PROP);
    }

    // ---- Recipe 访问 ----

    public static Object manaInfusionGetInput(Object recipe) {
        return invoke(METHOD_MANA_INFUSION_GET_INPUT, recipe);
    }

    public static ItemStack manaInfusionGetOutput(Object recipe) {
        return (ItemStack) invoke(METHOD_MANA_INFUSION_GET_OUTPUT, recipe);
    }

    public static int manaInfusionGetManaToConsume(Object recipe) {
        return invokeInt(METHOD_MANA_INFUSION_GET_MANA_TO_CONSUME, recipe);
    }

    public static List<?> elvenTradeGetInputs(Object recipe) {
        return (List<?>) invoke(METHOD_ELVEN_TRADE_GET_INPUTS, recipe);
    }

    public static List<?> elvenTradeGetOutputs(Object recipe) {
        return (List<?>) invoke(METHOD_ELVEN_TRADE_GET_OUTPUTS, recipe);
    }

    public static boolean petalsMatches(Object recipe, IItemHandler handler) {
        return invokeBool(METHOD_PETALS_MATCHES, recipe, handler);
    }

    public static List<?> petalsGetInputs(Object recipe) {
        return (List<?>) invoke(METHOD_PETALS_GET_INPUTS, recipe);
    }

    public static ItemStack petalsGetOutput(Object recipe) {
        return (ItemStack) invoke(METHOD_PETALS_GET_OUTPUT, recipe);
    }

    public static int runeAltarRecipeGetManaUsage(Object recipe) {
        return invokeInt(METHOD_RUNE_ALTAR_GET_MANA_USAGE, recipe);
    }
}
