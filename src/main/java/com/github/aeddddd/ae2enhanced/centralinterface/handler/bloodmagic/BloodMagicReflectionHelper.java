package com.github.aeddddd.ae2enhanced.centralinterface.handler.bloodmagic;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.reflection.ReflectionCache;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Blood Magic Handler 的反射辅助类.
 *
 * 缓存对 Blood Magic 全部使用到的类/方法/字段的反射引用,避免重复反射带来的性能损耗.
 * 查找统一走 {@link ReflectionCache}:全部缓存由静态字段初始化一次性完成,失败时只记一条警告、
 * 不抛异常,未命中的缓存项保持 null,由对应 handler 自行降级
 * (isValidTarget 回退到类型检查 / 配方无法计价).
 *
 * 本类自身不引用任何 WayofTime.bloodmagic 类型,因此可以安全地被无条件加载.
 */
public class BloodMagicReflectionHelper {

    // ---- TileAlchemyTable ----
    public static final Class<?> CLASS_TILE_ALCHEMY_TABLE =
            ReflectionCache.clazz("WayofTime.bloodmagic.tile.TileAlchemyTable");
    public static final Method METHOD_ALCHEMY_TABLE_IS_SLAVE =
            ReflectionCache.method(CLASS_TILE_ALCHEMY_TABLE, "isSlave");
    public static final Method METHOD_ALCHEMY_TABLE_GET_BURN_TIME =
            ReflectionCache.method(CLASS_TILE_ALCHEMY_TABLE, "getBurnTime");
    public static final Method METHOD_ALCHEMY_TABLE_IS_INPUT_SLOT_ACCESSIBLE =
            ReflectionCache.method(CLASS_TILE_ALCHEMY_TABLE, "isInputSlotAccessible", int.class);

    // ---- TileSoulForge ----
    public static final Class<?> CLASS_TILE_SOUL_FORGE =
            ReflectionCache.clazz("WayofTime.bloodmagic.tile.TileSoulForge");
    public static final Field FIELD_SOUL_FORGE_BURN_TIME =
            ReflectionCache.field(CLASS_TILE_SOUL_FORGE, "burnTime");

    // ---- TileAltar ----
    public static final Class<?> CLASS_TILE_ALTAR =
            ReflectionCache.clazz("WayofTime.bloodmagic.tile.TileAltar");
    public static final Method METHOD_ALTAR_START_CYCLE =
            ReflectionCache.method(CLASS_TILE_ALTAR, "startCycle");
    public static final Method METHOD_ALTAR_IS_ACTIVE =
            ReflectionCache.method(CLASS_TILE_ALTAR, "isActive");
    public static final Method METHOD_ALTAR_GET_PROGRESS =
            ReflectionCache.method(CLASS_TILE_ALTAR, "getProgress");

    // ---- BloodMagicAPI / RecipeRegistrar ----
    private static final Class<?> CLASS_BLOOD_MAGIC_API =
            ReflectionCache.clazz("WayofTime.bloodmagic.api.impl.BloodMagicAPI");
    private static final Class<?> CLASS_RECIPE_REGISTRAR =
            ReflectionCache.clazz("WayofTime.bloodmagic.api.impl.BloodMagicRecipeRegistrar");
    public static final Field FIELD_API_INSTANCE =
            ReflectionCache.field(CLASS_BLOOD_MAGIC_API, "INSTANCE");
    public static final Method METHOD_API_GET_RECIPE_REGISTRAR =
            ReflectionCache.method(CLASS_BLOOD_MAGIC_API, "getRecipeRegistrar");
    public static final Method METHOD_REGISTRAR_GET_ALCHEMY_RECIPES =
            ReflectionCache.method(CLASS_RECIPE_REGISTRAR, "getAlchemyRecipes");
    public static final Method METHOD_REGISTRAR_GET_ALTAR_RECIPES =
            ReflectionCache.method(CLASS_RECIPE_REGISTRAR, "getAltarRecipes");

    // ---- RecipeAlchemyTable ----
    private static final Class<?> CLASS_RECIPE_ALCHEMY_TABLE =
            ReflectionCache.clazz("WayofTime.bloodmagic.api.impl.recipe.RecipeAlchemyTable");
    public static final Method METHOD_RECIPE_ALCHEMY_TABLE_GET_INPUT =
            ReflectionCache.method(CLASS_RECIPE_ALCHEMY_TABLE, "getInput");
    public static final Method METHOD_RECIPE_ALCHEMY_TABLE_GET_OUTPUT =
            ReflectionCache.method(CLASS_RECIPE_ALCHEMY_TABLE, "getOutput");
    public static final Method METHOD_RECIPE_ALCHEMY_TABLE_GET_SYPHON =
            ReflectionCache.method(CLASS_RECIPE_ALCHEMY_TABLE, "getSyphon");

    // ---- RecipeBloodAltar ----
    private static final Class<?> CLASS_RECIPE_BLOOD_ALTAR =
            ReflectionCache.clazz("WayofTime.bloodmagic.api.impl.recipe.RecipeBloodAltar");
    public static final Method METHOD_RECIPE_BLOOD_ALTAR_GET_INPUT =
            ReflectionCache.method(CLASS_RECIPE_BLOOD_ALTAR, "getInput");
    public static final Method METHOD_RECIPE_BLOOD_ALTAR_GET_OUTPUT =
            ReflectionCache.method(CLASS_RECIPE_BLOOD_ALTAR, "getOutput");
    public static final Method METHOD_RECIPE_BLOOD_ALTAR_GET_SYPHON =
            ReflectionCache.method(CLASS_RECIPE_BLOOD_ALTAR, "getSyphon");

    static {
        // 查找失败只 warn 不抛 —— 缺 Blood Magic / 版本不匹配时各缓存项保持 null,
        // 由 handler 自行降级(类型检查 / 拒绝计价);逐项未命中的原因由 ReflectionCache 按 debug 记录。
        if (CLASS_TILE_ALCHEMY_TABLE == null || METHOD_ALCHEMY_TABLE_GET_BURN_TIME == null
                || CLASS_TILE_SOUL_FORGE == null || CLASS_TILE_ALTAR == null
                || FIELD_API_INSTANCE == null || METHOD_REGISTRAR_GET_ALCHEMY_RECIPES == null
                || METHOD_RECIPE_ALCHEMY_TABLE_GET_OUTPUT == null
                || METHOD_RECIPE_BLOOD_ALTAR_GET_OUTPUT == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] BloodMagicReflectionHelper: Blood Magic 反射缓存不完整"
                    + "(未安装或版本不匹配),相关方块将退化为类型检查且无法计价");
        }
    }

    // ---- 通用调用封装 ----

    public static boolean isInstance(Class<?> clazz, Object obj) {
        return clazz != null && clazz.isInstance(obj);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            throw new IllegalStateException("[AE2E] BloodMagicReflectionHelper: method not cached");
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[AE2E] BloodMagicReflectionHelper invoke failed: " + method, e);
        }
    }

    private static boolean invokeBool(Method method, Object target, Object... args) {
        return (Boolean) invoke(method, target, args);
    }

    private static int invokeInt(Method method, Object target, Object... args) {
        return (Integer) invoke(method, target, args);
    }

    // ---- TileAlchemyTable ----

    public static boolean isAlchemyTableSlave(Object table) {
        return invokeBool(METHOD_ALCHEMY_TABLE_IS_SLAVE, table);
    }

    public static int getAlchemyTableBurnTime(Object table) {
        return invokeInt(METHOD_ALCHEMY_TABLE_GET_BURN_TIME, table);
    }

    public static boolean isAlchemyTableInputSlotAccessible(Object table, int slot) {
        return invokeBool(METHOD_ALCHEMY_TABLE_IS_INPUT_SLOT_ACCESSIBLE, table, slot);
    }

    // ---- TileSoulForge ----

    public static int getSoulForgeBurnTime(Object forge) {
        if (FIELD_SOUL_FORGE_BURN_TIME == null) {
            throw new IllegalStateException("[AE2E] BloodMagicReflectionHelper: burnTime field not cached");
        }
        try {
            return FIELD_SOUL_FORGE_BURN_TIME.getInt(forge);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("[AE2E] BloodMagicReflectionHelper failed to read SoulForge burnTime", e);
        }
    }

    // ---- TileAltar ----

    public static void altarStartCycle(Object altar) {
        invoke(METHOD_ALTAR_START_CYCLE, altar);
    }

    public static boolean isAltarActive(Object altar) {
        return invokeBool(METHOD_ALTAR_IS_ACTIVE, altar);
    }

    public static int getAltarProgress(Object altar) {
        return invokeInt(METHOD_ALTAR_GET_PROGRESS, altar);
    }

    // ---- BloodMagicAPI / RecipeRegistrar ----

    private static Object getRecipeRegistrar() {
        if (FIELD_API_INSTANCE == null) {
            throw new IllegalStateException("[AE2E] BloodMagicReflectionHelper: API INSTANCE field not cached");
        }
        try {
            Object api = FIELD_API_INSTANCE.get(null);
            return invoke(METHOD_API_GET_RECIPE_REGISTRAR, api);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("[AE2E] BloodMagicReflectionHelper failed to read API INSTANCE", e);
        }
    }

    public static Set<?> getAlchemyRecipes() {
        return (Set<?>) invoke(METHOD_REGISTRAR_GET_ALCHEMY_RECIPES, getRecipeRegistrar());
    }

    public static Set<?> getAltarRecipes() {
        return (Set<?>) invoke(METHOD_REGISTRAR_GET_ALTAR_RECIPES, getRecipeRegistrar());
    }

    // ---- RecipeAlchemyTable ----

    public static List<?> alchemyRecipeGetInput(Object recipe) {
        return (List<?>) invoke(METHOD_RECIPE_ALCHEMY_TABLE_GET_INPUT, recipe);
    }

    public static ItemStack alchemyRecipeGetOutput(Object recipe) {
        return (ItemStack) invoke(METHOD_RECIPE_ALCHEMY_TABLE_GET_OUTPUT, recipe);
    }

    public static int alchemyRecipeGetSyphon(Object recipe) {
        return invokeInt(METHOD_RECIPE_ALCHEMY_TABLE_GET_SYPHON, recipe);
    }

    // ---- RecipeBloodAltar ----

    public static Ingredient bloodAltarRecipeGetInput(Object recipe) {
        return (Ingredient) invoke(METHOD_RECIPE_BLOOD_ALTAR_GET_INPUT, recipe);
    }

    public static ItemStack bloodAltarRecipeGetOutput(Object recipe) {
        return (ItemStack) invoke(METHOD_RECIPE_BLOOD_ALTAR_GET_OUTPUT, recipe);
    }

    public static int bloodAltarRecipeGetSyphon(Object recipe) {
        return invokeInt(METHOD_RECIPE_BLOOD_ALTAR_GET_SYPHON, recipe);
    }
}
