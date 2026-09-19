package com.github.aeddddd.ae2enhanced.crafting;

import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 微型奇点燃料配方注册表.
 */
public class SingularityFuelRegistry {

    private static final List<SingularityFuelRecipe> RECIPES = new CopyOnWriteArrayList<>();

    /** 延迟移除队列: CraftTweaker 脚本可能在配方注册前执行. */
    private static final Set<String> PENDING_REMOVALS = ConcurrentHashMap.newKeySet();

    public static void register(SingularityFuelRecipe recipe) {
        // 同 id 覆盖语义: 先移除已有同 id 条目, 保证 id 唯一,
        // 与 AssemblyHubUpgradeRegistry 的覆盖语义一致
        RECIPES.removeIf(r -> r.getId().equals(recipe.getId()));
        RECIPES.add(recipe);
    }

    /**
     * 查找第一个匹配该手持物品的燃料配方.
     */
    @Nullable
    public static SingularityFuelRecipe findFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (SingularityFuelRecipe recipe : RECIPES) {
            if (recipe.matches(stack)) {
                return recipe;
            }
        }
        return null;
    }

    public static boolean removeById(String id) {
        return RECIPES.removeIf(r -> r.getId().equals(id));
    }

    public static List<SingularityFuelRecipe> getRecipes() {
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
