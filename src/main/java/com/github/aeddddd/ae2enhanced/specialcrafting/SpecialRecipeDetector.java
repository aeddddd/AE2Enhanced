package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;

import com.github.aeddddd.ae2enhanced.util.RecursiveCraftingHelper;

/**
 * 特殊配方预扫描（路由点 A 的判定逻辑）.
 * <p>阶段 1 范围：仅检测"请求物品的候选样板中存在净产出自引用样板"
 * （如 1 水→2 水、A+2B→2A 的锻造模板复制类）.检测成本为一次 O(k) 候选遍历,
 * 未命中零副作用,请求完全走原生计算.</p>
 * <p>阶段 2 将扩展为跨样板循环链检测（RecipeGraph + SCC).</p>
 */
public final class SpecialRecipeDetector {

    private SpecialRecipeDetector() {
    }

    /**
     * 请求 {@code what} 是否可能涉及特殊配方（当前：候选样板含净产出自引用）.
     */
    public static boolean mayInvolveSpecialRecipes(ICraftingService craftingService, AEKey what) {
        for (var pattern : craftingService.getCraftingFor(what)) {
            if (RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, what)) {
                return true;
            }
        }
        return false;
    }
}
