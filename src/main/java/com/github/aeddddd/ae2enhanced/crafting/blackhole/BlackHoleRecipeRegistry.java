package com.github.aeddddd.ae2enhanced.crafting.blackhole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 黑洞配方代码级注册表。
 * <p>供脚本或动态注册使用，同时支持延迟移除。</p>
 */
public class BlackHoleRecipeRegistry {

    public static final List<BlackHoleRecipe> RECIPES = new ArrayList<>();
    public static final Set<String> PENDING_REMOVALS = new HashSet<>();

    public static void register(BlackHoleRecipe recipe) {
        RECIPES.add(recipe);
    }

    /**
     * 在已注册配方中查找首个匹配当前物品聚合的配方。
     */
    public static BlackHoleRecipe findMatching(Map<String, Integer> found) {
        for (BlackHoleRecipe recipe : RECIPES) {
            if (recipe.matches(found)) {
                return recipe;
            }
        }
        return null;
    }

    public static void removeById(String id) {
        RECIPES.removeIf(recipe -> recipe.getStringId().equals(id));
        PENDING_REMOVALS.remove(id);
    }

    public static List<BlackHoleRecipe> getRecipes() {
        return Collections.unmodifiableList(RECIPES);
    }

    public static void queueRemoval(String id) {
        PENDING_REMOVALS.add(id);
    }

    public static void applyPendingRemovals() {
        for (String id : PENDING_REMOVALS) {
            removeById(id);
        }
        PENDING_REMOVALS.clear();
    }
}
