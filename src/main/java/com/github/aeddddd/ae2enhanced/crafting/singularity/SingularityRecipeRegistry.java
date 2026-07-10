package com.github.aeddddd.ae2enhanced.crafting.singularity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 微型奇点仪式配方注册表。
 */
public class SingularityRecipeRegistry {

    private static final List<SingularityRecipe> RECIPES = new CopyOnWriteArrayList<>();

    /** 延迟移除队列：CraftTweaker 脚本可能在配方注册前执行 */
    private static final Set<String> PENDING_REMOVALS = ConcurrentHashMap.newKeySet();

    public static void register(SingularityRecipe recipe) {
        RECIPES.add(recipe);
    }

    /**
     * 在以 center 为中心，玩家手持 heldItem 的情况下寻找第一个匹配的配方。
     */
    public static SingularityRecipe findMatching(Level level, BlockPos center, ItemStack heldItem) {
        for (SingularityRecipe recipe : RECIPES) {
            if (recipe.matches(level, center, heldItem)) {
                return recipe;
            }
        }
        return null;
    }

    public static boolean removeById(String id) {
        return RECIPES.removeIf(r -> r.getId().equals(id));
    }

    public static List<SingularityRecipe> getRecipes() {
        return new ArrayList<>(RECIPES);
    }

    public static void queueRemoval(String id) {
        PENDING_REMOVALS.add(id);
    }

    public static void applyPendingRemovals() {
        if (PENDING_REMOVALS.isEmpty()) {
            return;
        }
        for (String id : PENDING_REMOVALS) {
            removeById(id);
        }
        PENDING_REMOVALS.clear();
    }
}
