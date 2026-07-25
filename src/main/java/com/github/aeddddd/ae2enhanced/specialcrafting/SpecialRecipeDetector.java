package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;

import com.github.aeddddd.ae2enhanced.util.RecursiveCraftingHelper;

/**
 * 特殊配方预扫描（路由点 A 的判定逻辑）.
 * <p>检测范围：阶段 1 净产出自引用样板（如 A+2B→2A)+ 阶段 2 跨样板增殖环
 * （简单环且净乘积率 > 1,如 A→2B,B→A).未命中零副作用,请求完全走原生计算.</p>
 */
public final class SpecialRecipeDetector {

    private SpecialRecipeDetector() {
    }

    /**
     * 请求 {@code what} 是否可能涉及特殊配方.
     */
    public static boolean mayInvolveSpecialRecipes(ICraftingService craftingService, AEKey what) {
        // 阶段 1:候选样板含自引用(净产出自引用,或任意精确自引用 key——
        // 含催化剂型与"自引用 key ≠ 请求 key"的情形,这些场景原生 limitQty
        // 逐份展开会在超大订单下挂起,必须走闭式解)
        for (var pattern : craftingService.getCraftingFor(what)) {
            if (RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, what)) {
                return true;
            }
            if (RecursiveCraftingHelper.findSelfRefKey(pattern) != null) {
                return true;
            }
        }
        // 阶段 2:经过请求物的候选环中存在可解增殖环
        for (var cycle : CycleAnalyzer.findCyclesThrough(craftingService, what)) {
            var analysis = CycleAnalyzer.analyze(cycle);
            if (analysis != null && analysis.rateClass() == CycleAnalyzer.RateClass.PRODUCTIVE) {
                return true;
            }
        }
        return false;
    }
}
