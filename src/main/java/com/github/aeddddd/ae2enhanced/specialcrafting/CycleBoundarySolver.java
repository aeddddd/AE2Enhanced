package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.ChildCraftingSimulationState;

import com.github.aeddddd.ae2enhanced.util.RecursiveCraftingHelper;

/**
 * 深层循环边界求解器(DAG 引擎 4.3):把含环子图当黑盒,
 * 以**当前模拟库存状态**求解 what×target 子需求并就地记账
 * (种子贷款、环外输入经原生树、产出回插、addCrafting).
 * <p>支持:① 净增殖自引用(selfKey == 边界 key,贷款法闭式);
 * ② 跨样板增殖环(并集联立优先,逐环迭代兜底,复用 {@link CycleSolver}).</p>
 * <p>结算语义与根请求求解一致:交付量(= 边界需求量)从库存取走,
 * 种子保留——防止同一批产出被 DAG 其他节点重复取用.</p>
 */
public final class CycleBoundarySolver {

    /**
     * 边界求解结果.
     */
    public enum BoundaryResult {
        /** 求解成功并已记账. */
        SOLVED,
        /** 数值不可表示(天文数字需求):调用方应就地把边界需求记为缺料(O(1)),
         * 而非整单回落原生——回落在大网络上即高请求计算卡死. */
        MISSING,
        /** 不适用(种子不足/输入不足等),调用方应整单回落原生. */
        FALLBACK
    }

    private CycleBoundarySolver() {
    }

    /**
     * @return 求解结果;仅 {@link BoundaryResult#FALLBACK} 时调用方才整单回落原生.
     */
    public static BoundaryResult solve(ICraftingService craftingService, CraftingCalculation calc,
            AEKey what, long target, ChildCraftingSimulationState inv) throws InterruptedException {
        // ① 净增殖自引用(单节点自环)
        for (var pattern : craftingService.getCraftingFor(what)) {
            if (RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, what)) {
                return solveDup(craftingService, calc, pattern, what, target, inv);
            }
        }
        // ② 跨样板环:并集优先(θ 形共享结构),再逐环迭代
        boolean overflow = false;
        var cycles = CycleAnalyzer.findCyclesThrough(craftingService, what);
        var union = CycleAnalyzer.analyzeUnion(cycles);
        if (union != null && union.rateClass() == CycleAnalyzer.RateClass.PRODUCTIVE) {
            var r = CycleSolver.trySolve(craftingService, calc, union, inv, what, target);
            if (r == CycleSolver.SolveResult.SUCCESS) {
                return BoundaryResult.SOLVED;
            }
            overflow |= r == CycleSolver.SolveResult.OVERFLOW;
        }
        for (var cycle : cycles) {
            var analysis = CycleAnalyzer.analyze(cycle);
            if (analysis == null || analysis.rateClass() != CycleAnalyzer.RateClass.PRODUCTIVE) {
                continue;
            }
            var r = CycleSolver.trySolve(craftingService, calc, analysis, inv, what, target);
            if (r == CycleSolver.SolveResult.SUCCESS) {
                return BoundaryResult.SOLVED;
            }
            overflow |= r == CycleSolver.SolveResult.OVERFLOW;
        }
        // ③ 催化环:边界 key 是某中性/增殖环发射的环外副产物(深层 A→X+B、1B→1A 中的 X)
        for (var cycle : CycleAnalyzer.findCatalyticCycles(craftingService, what)) {
            var analysis = CycleAnalyzer.analyze(cycle);
            if (analysis == null || analysis.rateClass() == CycleAnalyzer.RateClass.DISSIPATIVE) {
                continue;
            }
            long xPerRound = CycleAnalyzer.byproductPerRound(analysis, what);
            if (xPerRound <= 0) {
                continue;
            }
            var r = CycleSolver.trySolveCatalytic(craftingService, calc, analysis, xPerRound, inv,
                    what, target);
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
     * 净增殖自引用闭式解(贷款法),与 SpecialCraftingCalculation 根路径同语义.
     */
    private static BoundaryResult solveDup(ICraftingService craftingService, CraftingCalculation calc,
            IPatternDetails selfRef, AEKey what, long target, ChildCraftingSimulationState inv)
            throws InterruptedException {
        long inPer = RecursiveCraftingHelper.selfInputPerCraft(selfRef, what);
        long outPer = RecursiveCraftingHelper.selfOutputPerCraft(selfRef, what);
        long gain = outPer - inPer;
        if (gain <= 0 || inPer <= 0) {
            return BoundaryResult.FALLBACK;
        }
        // 种子校验(不 ignore,边界 key 的库存可见)
        long stock = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        if (stock < inPer) {
            return BoundaryResult.FALLBACK;
        }
        // 溢出安全 ceilDiv(target、gain 为正,必得 crafts ≥ 1)
        long crafts = target / gain + (target % gain != 0 ? 1 : 0);
        // 产出侧守卫:批量模拟经原生 CraftingTreeProcess.request(无饱和乘法),
        // crafts×outPer 超 long 会使产出回绕成负数、库存记账错乱、结算必败.
        // (outPer ≥ inPer+1,故该守卫同时覆盖贷款量 inPer×(crafts-1) 的可表示性)
        if (crafts > Long.MAX_VALUE / outPer) {
            return BoundaryResult.MISSING; // 天文数字子需求 → O(1) 缺料
        }

        CraftingTreeNode rootNode = new CraftingTreeNode(craftingService, calc, what, 1, null, -1);
        CraftingTreeProcess pro = new CraftingTreeProcess(craftingService, calc, selfRef, rootNode);

        long loan = inPer * (crafts - 1);
        if (loan > 0) {
            inv.insert(what, loan, Actionable.MODULATE);
        }
        try {
            Ae2CraftingReflect.treeProcessRequest(pro, inv, crafts);
        } catch (CraftBranchFailure failure) {
            return BoundaryResult.FALLBACK; // 非自输入不足 → 整单回落(缺料报告)
        } finally {
            if (loan > 0) {
                inv.extract(what, loan, Actionable.MODULATE);
            }
        }

        // 结算:取走交付量(边界需求),种子保留
        long avail = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        long keep = avail > target ? inPer : 0;
        long drain = inv.extract(what, Math.min(target, Math.max(0, avail - keep)),
                Actionable.MODULATE);
        return drain == target ? BoundaryResult.SOLVED : BoundaryResult.FALLBACK;
    }

    /**
     * 供 DAG 执行器调用前的类型断言辅助:边界求解需要 Child 状态.
     */
    public static BoundaryResult solveInto(ICraftingService craftingService, CraftingCalculation calc,
            AEKey what, long target, appeng.crafting.inv.CraftingSimulationState inv)
            throws InterruptedException {
        if (!(inv instanceof ChildCraftingSimulationState child)) {
            return BoundaryResult.FALLBACK;
        }
        return solve(craftingService, calc, what, target, child);
    }
}
