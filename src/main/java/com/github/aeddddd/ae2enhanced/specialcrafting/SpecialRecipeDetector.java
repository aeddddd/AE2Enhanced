package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.List;

import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

/**
 * 特殊配方预扫描（路由点 A 的判定逻辑,移植自 1.20.1）.
 * <p>检测范围:阶段 1 净产出自引用样板（如 A+2B→2A)+ 阶段 2 跨样板增殖环
 * （简单环且净乘积率 > 1,如 A→2B,B→A).未命中零副作用,请求完全走原生计算.</p>
 */
public final class SpecialRecipeDetector {

    private SpecialRecipeDetector() {
    }

    /**
     * 请求 {@code what} 是否可能涉及特殊配方.
     * <p>结果只依赖网络样板集(与库存无关),按请求键 memo 于 {@link NetworkPatternIndex},
     * 随样板集重建一并失效——每个计算请求都会经过本判定,memo 消除重复的全网络扫描.</p>
     */
    public static boolean mayInvolveSpecialRecipes(ICraftingGrid cc, IAEItemStack what, World world) {
        NetworkPatternIndex index = NetworkPatternIndex.of(cc);
        IAEItemStack memoKey = null;
        if (index != null) {
            memoKey = RecursiveCraftingHelper.canon(what);
            Boolean memo = index.detectorVerdict(memoKey);
            if (memo != null) {
                return memo;
            }
        }
        boolean verdict = detect(cc, what, world);
        if (index != null) {
            index.memoDetectorVerdict(memoKey, verdict);
        }
        return verdict;
    }

    private static boolean detect(ICraftingGrid cc, IAEItemStack what, World world) {
        // 阶段 1:候选样板含自引用(净产出自引用,或任意精确自引用 key)
        for (ICraftingPatternDetails pattern : cc.getCraftingFor(what, null, -1, world)) {
            if (RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, what)) {
                return true;
            }
            if (RecursiveCraftingHelper.findSelfRefKey(pattern) != null) {
                return true;
            }
        }
        // 阶段 2:经过请求物的候选环中存在可解增殖环(含 θ 形共享结构的并集分析)
        List<List<CycleAnalyzer.CycleStep>> cycles = CycleAnalyzer.findCyclesThrough(cc, what, world);
        for (List<CycleAnalyzer.CycleStep> cycle : cycles) {
            CycleAnalyzer.Analysis analysis = CycleAnalyzer.analyze(cycle);
            if (analysis != null && analysis.rateClass() == CycleAnalyzer.RateClass.PRODUCTIVE) {
                return true;
            }
        }
        CycleAnalyzer.Analysis union = CycleAnalyzer.analyzeUnion(cycles);
        if (union != null && union.rateClass() == CycleAnalyzer.RateClass.PRODUCTIVE) {
            return true;
        }
        // 催化环:what 不在任何环上(否则按环键语义处理,中性环不接管),
        // 且生产 what 的样板本身是环步骤——环发射 what 为环外副产物
        if (cycles.isEmpty()) {
            for (ICraftingPatternDetails pattern : cc.getCraftingFor(what, null, -1, world)) {
                if (CycleAnalyzer.isCycleStep(cc, world, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }
}
