package com.github.aeddddd.ae2enhanced.centralinterface.handler.botania;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import vazkii.botania.common.block.tile.TileRuneAltar;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Botania Handler 的反射辅助类.
 *
 * 缓存对 Botania 非公共 API 的反射引用,避免重复反射带来的性能损耗.
 * 所有缓存均在静态初始化块中完成,失败时记录警告但不应导致崩溃
 * (对应 handler 会在 isValidTarget 中回退到类型检查).
 */
public class BotaniaReflectionHelper {

    // ---- TileTerraPlate ----
    public static final Class<?> CLASS_TILE_TERRA_PLATE;
    public static final Method METHOD_HAS_VALID_PLATFORM;
    public static final Method METHOD_ARE_ITEMS_VALID;
    public static final Method METHOD_GET_ITEMS;

    // ---- TileRuneAltar ----
    public static final Class<?> CLASS_TILE_RUNE_ALTAR;
    public static final Field FIELD_CURRENT_RECIPE;
    public static final Field FIELD_COOLDOWN;
    public static final Field FIELD_MANA;

    static {
        Class<?> tileTerraPlate = null;
        Method hasValidPlatform = null;
        Method areItemsValid = null;
        Method getItems = null;

        Class<?> tileRuneAltar = null;
        Field currentRecipe = null;
        Field cooldown = null;
        Field mana = null;

        try {
            tileTerraPlate = Class.forName("vazkii.botania.common.block.tile.TileTerraPlate");
            hasValidPlatform = tileTerraPlate.getDeclaredMethod("hasValidPlatform");
            hasValidPlatform.setAccessible(true);
            areItemsValid = tileTerraPlate.getDeclaredMethod("areItemsValid", List.class);
            areItemsValid.setAccessible(true);
            getItems = tileTerraPlate.getDeclaredMethod("getItems");
            getItems.setAccessible(true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] BotaniaReflectionHelper failed to cache TileTerraPlate", e);
        }

        try {
            tileRuneAltar = Class.forName("vazkii.botania.common.block.tile.TileRuneAltar");
            currentRecipe = tileRuneAltar.getDeclaredField("currentRecipe");
            currentRecipe.setAccessible(true);
            cooldown = tileRuneAltar.getDeclaredField("cooldown");
            cooldown.setAccessible(true);
            mana = tileRuneAltar.getDeclaredField("mana");
            mana.setAccessible(true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] BotaniaReflectionHelper failed to cache TileRuneAltar", e);
        }

        CLASS_TILE_TERRA_PLATE = tileTerraPlate;
        METHOD_HAS_VALID_PLATFORM = hasValidPlatform;
        METHOD_ARE_ITEMS_VALID = areItemsValid;
        METHOD_GET_ITEMS = getItems;

        CLASS_TILE_RUNE_ALTAR = tileRuneAltar;
        FIELD_CURRENT_RECIPE = currentRecipe;
        FIELD_COOLDOWN = cooldown;
        FIELD_MANA = mana;
    }

    // ---- Helper methods ----

    public static int getRuneAltarCooldown(TileRuneAltar altar) {
        if (FIELD_COOLDOWN == null) return 0;
        try {
            return FIELD_COOLDOWN.getInt(altar);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    public static void setRuneAltarCooldown(TileRuneAltar altar, int value) {
        if (FIELD_COOLDOWN == null) return;
        try {
            FIELD_COOLDOWN.setInt(altar, value);
        } catch (IllegalAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to set RuneAltar cooldown", e);
        }
    }

    public static int getRuneAltarMana(TileRuneAltar altar) {
        if (FIELD_MANA == null) return altar.getCurrentMana();
        try {
            return FIELD_MANA.getInt(altar);
        } catch (IllegalAccessException e) {
            return altar.getCurrentMana();
        }
    }

    public static Object getRuneAltarCurrentRecipe(TileRuneAltar altar) {
        if (FIELD_CURRENT_RECIPE == null) return null;
        try {
            return FIELD_CURRENT_RECIPE.get(altar);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    public static void setRuneAltarCurrentRecipe(TileRuneAltar altar, Object recipe) {
        if (FIELD_CURRENT_RECIPE == null) return;
        try {
            FIELD_CURRENT_RECIPE.set(altar, recipe);
        } catch (IllegalAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to set RuneAltar currentRecipe", e);
        }
    }
}
