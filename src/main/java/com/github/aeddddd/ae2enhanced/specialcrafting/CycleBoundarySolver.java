package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.MECraftingInventory;

/**
 * 深层循环边界求解器（DAG 引擎 4.3,1.12.2 移植）:把含环子图当黑盒,
 * 以**当前模拟库存状态**求解 what×target 子需求并就地记账
 * （种子贷款、环外输入经原生树、产出回插、crafts 记账）.
 * <p>支持:① 净增殖自引用(selfKey == 边界 key,贷款法闭式);
 * ② 跨样板增殖环（并集联立优先,逐环迭代兜底,复用 {@link CycleSolver});
 * ③ 催化环（边界 key 是某中性/增殖环发射的环外副产物）.</p>
 * <p>结算语义与根请求求解一致:交付量（= 边界需求量）从库存取走,
 * 种子保留——防止同一批产出被 DAG 其他节点重复取用.</p>
 */
public final class CycleBoundarySolver {

    /**
     * 边界求解结果.
     */
    public enum BoundaryResult {
        /** 求解成功并已记账. */
        SOLVED,
        /** 数值不可表示（天文数字需求）:调用方应就地把边界需求记为缺料(O(1)),
         * 而非整单回落原生——回落在大网络上即高请求计算卡死. */
        MISSING,
        /** 不适用（种子不足/输入不足等）,调用方应整单回落原生. */
        FALLBACK
    }

    private CycleBoundarySolver() {
    }

    /**
     * @param budget 单趟 DAG 执行内所有循环边界共享的分析预算(总开销封顶);
     *        超预算按 {@link BoundaryResult#FALLBACK} 回落(与不可解同语义)
     * @return 求解结果;仅 {@link BoundaryResult#FALLBACK} 时调用方才整单回落原生.
     */
    public static BoundaryResult solveInto(ICraftingGrid cc, CraftingJob job, IAEItemStack what, long target,
            MECraftingInventory inv, CraftingTreeNode rootNode, IActionSource src, World world,
            AnalysisBudget budget) throws InterruptedException {
        // ① 净增殖自引用（单节点自环）
        for (ICraftingPatternDetails pattern : cc.getCraftingFor(what, null, -1, world)) {
            if (RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, what)) {
                return solveDup(cc, job, pattern, what, target, inv, rootNode, src);
            }
        }
        NetworkPatternIndex index = NetworkPatternIndex.of(cc);
        // ② 跨样板环:并集优先(θ 形共享结构),再逐环迭代
        boolean overflow = false;
        List<List<CycleAnalyzer.CycleStep>> cycles = CycleAnalyzer.findCyclesThrough(cc, what, world);
        CycleAnalyzer.Analysis union = budget.expired() ? null
                : CycleAnalyzer.analyzeUnionMemo(index, cycles);
        if (union != null && union.rateClass() == CycleAnalyzer.RateClass.PRODUCTIVE) {
            CycleSolver.SolveResult r = CycleSolver.trySolve(cc, job, union, inv, what, target, rootNode,
                    src);
            if (r == CycleSolver.SolveResult.SUCCESS) {
                return BoundaryResult.SOLVED;
            }
            overflow |= r == CycleSolver.SolveResult.OVERFLOW;
        }
        for (List<CycleAnalyzer.CycleStep> cycle : cycles) {
            if (budget.expired()) {
                SpecialLog.info("[DAG] 循环边界分析超共享预算(>{}ms),整单回落: {}×{}",
                        AnalysisBudget.SOLVE_BUDGET_MS, what, target);
                return BoundaryResult.FALLBACK;
            }
            CycleAnalyzer.Analysis analysis = CycleAnalyzer.analyzeMemo(index, cycle);
            if (analysis == null || analysis.rateClass() != CycleAnalyzer.RateClass.PRODUCTIVE) {
                continue;
            }
            CycleSolver.SolveResult r = CycleSolver.trySolve(cc, job, analysis, inv, what, target, rootNode,
                    src);
            if (r == CycleSolver.SolveResult.SUCCESS) {
                return BoundaryResult.SOLVED;
            }
            overflow |= r == CycleSolver.SolveResult.OVERFLOW;
        }
        // ③ 催化环:边界 key 是某中性/增殖环发射的环外副产物(深层 A→X+B、B→A 中的 X)
        for (List<CycleAnalyzer.CycleStep> cycle : CycleAnalyzer.findCatalyticCycles(cc, what, world)) {
            if (budget.expired()) {
                SpecialLog.info("[DAG] 催化环分析超共享预算(>{}ms),整单回落: {}×{}",
                        AnalysisBudget.SOLVE_BUDGET_MS, what, target);
                return BoundaryResult.FALLBACK;
            }
            CycleAnalyzer.Analysis analysis = CycleAnalyzer.analyzeMemo(index, cycle);
            if (analysis == null || analysis.rateClass() == CycleAnalyzer.RateClass.DISSIPATIVE) {
                continue;
            }
            long xPerRound = CycleAnalyzer.byproductPerRound(analysis, what);
            if (xPerRound <= 0) {
                continue;
            }
            CycleSolver.SolveResult r = CycleSolver.trySolveCatalytic(cc, job, analysis, xPerRound, inv,
                    what, target, rootNode, src);
            if (r == CycleSolver.SolveResult.SUCCESS) {
                return BoundaryResult.SOLVED;
            }
            overflow |= r == CycleSolver.SolveResult.OVERFLOW;
        }
        if (overflow) {
            // 天文数字边界需求(轮数/贷款量超 long):对齐根请求路径的 O(1) 缺料语义
            SpecialLog.info("[DAG] 循环边界天文数字需求,记缺料: {}×{}", what, target);
            return BoundaryResult.MISSING;
        }
        SpecialLog.info("[DAG] 循环边界不可解: {}×{}", what, target);
        return BoundaryResult.FALLBACK;
    }

    /**
     * 净增殖自引用闭式解（贷款法）,与 SpecialCraftingJob 根路径同语义.
     */
    private static BoundaryResult solveDup(ICraftingGrid cc, CraftingJob job,
            ICraftingPatternDetails selfRef, IAEItemStack what, long target, MECraftingInventory inv,
            CraftingTreeNode rootNode, IActionSource src) throws InterruptedException {
        long inPer = RecursiveCraftingHelper.selfInputPerCraft(selfRef, what);
        long outPer = RecursiveCraftingHelper.selfOutputPerCraft(selfRef, what);
        long gain = outPer - inPer;
        if (gain <= 0 || inPer <= 0) {
            return BoundaryResult.FALLBACK;
        }
        // 种子校验(不 ignore,边界 key 的库存可见)
        long stock = CycleSolver.invAmount(inv, what);
        if (stock < inPer) {
            return BoundaryResult.FALLBACK;
        }
        // 溢出安全 ceilDiv(target、gain 为正,必得 crafts ≥ 1)
        long crafts = target / gain + (target % gain != 0 ? 1 : 0);
        // 产出侧守卫:批量模拟经原生 CraftingTreeProcess.request(无饱和乘法),
        // crafts×outPer 超 long 会使产出回绕成负数、库存记账错乱、结算必败 → 整单回落原生.
        // (outPer ≥ inPer+1,故该守卫同时覆盖贷款量 inPer×(crafts-1) 的可表示性)
        if (crafts > Long.MAX_VALUE / outPer) {
            return BoundaryResult.MISSING; // 天文数字子需求 → O(1) 缺料
        }

        CraftingTreeProcess pro = new CraftingTreeProcess(cc, job, selfRef, rootNode, 1);
        Ae2CraftingReflect.addProcessToNode(rootNode, pro);

        long loan = inPer * (crafts - 1);
        if (loan > 0) {
            IAEItemStack loanStack = RecursiveCraftingHelper.canon(what);
            loanStack.setStackSize(loan);
            inv.injectItems(loanStack, Actionable.MODULATE, src);
        }
        // CrT 不消耗配方(同根请求路径):催化剂预注入虚拟返还(不归还)
        Map<IAEItemStack, Long> catalystInject = new LinkedHashMap<>();
        Map<IAEItemStack, Long> catalystRebate = new LinkedHashMap<>();
        Set<IAEItemStack> catalystExcluded = new HashSet<>();
        catalystExcluded.add(RecursiveCraftingHelper.canon(what));
        CatalystReturns.collect(selfRef, crafts, catalystExcluded, catalystInject, catalystRebate);
        CatalystReturns.inject(catalystInject, inv, src);
        try {
            Ae2CraftingReflect.treeProcessRequest(pro, inv, crafts, src);
        } catch (CraftBranchFailure failure) {
            return BoundaryResult.FALLBACK; // 非自输入不足 → 整单回落(缺料报告)
        } finally {
            if (loan > 0) {
                IAEItemStack payback = RecursiveCraftingHelper.canon(what);
                payback.setStackSize(loan);
                inv.extractItems(payback, Actionable.MODULATE, src);
            }
        }

        // used 返利:种子语义 = inPer(与根请求路径一致)
        Map<IAEItemStack, Long> seeds = new LinkedHashMap<>();
        seeds.put(RecursiveCraftingHelper.canon(what), inPer);
        seeds.putAll(catalystRebate); // 催化剂种子语义:净消耗+单次投入
        TreeUsedRebate.rebate(rootNode, seeds);

        // 结算:取走交付量(边界需求),种子保留
        long avail = CycleSolver.invAmount(inv, what);
        long keep = avail > target ? inPer : 0;
        long drainable = Math.min(target, Math.max(0, avail - keep));
        if (drainable > 0) {
            IAEItemStack drainStack = RecursiveCraftingHelper.canon(what);
            drainStack.setStackSize(drainable);
            inv.extractItems(drainStack, Actionable.MODULATE, src);
        }
        return drainable == target ? BoundaryResult.SOLVED : BoundaryResult.FALLBACK;
    }
}
