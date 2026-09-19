package com.github.aeddddd.ae2enhanced.crafting;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 黑洞合成配方注册表.
 */
public class BlackHoleRecipeRegistry {

    private static final List<BlackHoleRecipe> RECIPES = new CopyOnWriteArrayList<>();

    /** 延迟移除队列: CraftTweaker 脚本可能在配方注册之前执行. */
    private static final Set<String> PENDING_REMOVALS = ConcurrentHashMap.newKeySet();

    public static void register(BlackHoleRecipe recipe) {
        // 同 id 覆盖语义: 先移除已有同 id 条目再添加, 保证 id 唯一,
        // 与 AssemblyHubUpgradeRegistry 的 Map 覆盖语义一致
        RECIPES.removeIf(r -> r.getId().equals(recipe.getId()));
        RECIPES.add(recipe);
    }

    /**
     * 在 found 中寻找第一个匹配的配方.
     */
    public static BlackHoleRecipe findMatching(Map<String, Integer> found) {
        for (BlackHoleRecipe recipe : RECIPES) {
            if (recipe.matches(found)) {
                return recipe;
            }
        }
        return null;
    }

    public static boolean removeById(String id) {
        return RECIPES.removeIf(r -> r.getId().equals(id));
    }

    public static List<BlackHoleRecipe> getRecipes() {
        return new java.util.ArrayList<>(RECIPES);
    }

    public static void queueRemoval(String id) {
        PENDING_REMOVALS.add(id);
    }

    public static void applyPendingRemovals() {
        if (PENDING_REMOVALS.isEmpty()) return;
        for (String id : PENDING_REMOVALS) {
            removeById(id);
        }
        PENDING_REMOVALS.clear();
    }
}
