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

    private CycleBoundarySolver() {
    }

    /**
     * @return true = 求解成功并已记账;false = 不适用(调用方应整单回落原生).
     */
    public static boolean solve(ICraftingService craftingService, CraftingCalculation calc,
            AEKey what, long target, ChildCraftingSimulationState inv) throws InterruptedException {
        // ① 净增殖自引用(单节点自环)
        for (var pattern : craftingService.getCraftingFor(what)) {
            if (RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, what)) {
                return solveDup(craftingService, calc, pattern, what, target, inv);
            }
        }
        // ② 跨样板环:并集优先(θ 形共享结构),再逐环迭代
        var cycles = CycleAnalyzer.findCyclesThrough(craftingService, what);
        var union = CycleAnalyzer.analyzeUnion(cycles);
        if (union != null && union.rateClass() == CycleAnalyzer.RateClass.PRODUCTIVE
                && CycleSolver.trySolve(craftingService, calc, union, inv, what,
                        target) == CycleSolver.SolveResult.SUCCESS) {
            return true;
        }
        for (var cycle : cycles) {
            var analysis = CycleAnalyzer.analyze(cycle);
            if (analysis == null || analysis.rateClass() != CycleAnalyzer.RateClass.PRODUCTIVE) {
                continue;
            }
            if (CycleSolver.trySolve(craftingService, calc, analysis, inv, what,
                    target) == CycleSolver.SolveResult.SUCCESS) {
                return true;
            }
        }
        SpecialLog.info("[DAG] 循环边界不可解: {}×{}", what, target);
        return false;
    }

    /**
     * 净增殖自引用闭式解(贷款法),与 SpecialCraftingCalculation 根路径同语义.
     */
    private static boolean solveDup(ICraftingService craftingService, CraftingCalculation calc,
            IPatternDetails selfRef, AEKey what, long target, ChildCraftingSimulationState inv)
            throws InterruptedException {
        long inPer = RecursiveCraftingHelper.selfInputPerCraft(selfRef, what);
        long outPer = RecursiveCraftingHelper.selfOutputPerCraft(selfRef, what);
        long gain = outPer - inPer;
        if (gain <= 0 || inPer <= 0) {
            return false;
        }
        // 种子校验(不 ignore,边界 key 的库存可见)
        long stock = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        if (stock < inPer) {
            return false;
        }
        long crafts = (target + gain - 1) / gain;
        if (crafts <= 0 || crafts > Long.MAX_VALUE / Math.max(1, inPer)) {
            return false; // 天文数字子需求:回落(整单走原生/缺料语义)
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
            return false; // 非自输入不足 → 整单回落(缺料报告)
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
        return drain == target;
    }

    /**
     * 供 DAG 执行器调用前的类型断言辅助:边界求解需要 Child 状态.
     */
    public static boolean solveInto(ICraftingService craftingService, CraftingCalculation calc,
            AEKey what, long target, appeng.crafting.inv.CraftingSimulationState inv)
            throws InterruptedException {
        if (!(inv instanceof ChildCraftingSimulationState child)) {
            return false;
        }
        return solve(craftingService, calc, what, target, child);
    }
}
