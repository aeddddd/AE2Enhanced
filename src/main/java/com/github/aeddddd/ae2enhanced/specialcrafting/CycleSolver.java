package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.ChildCraftingSimulationState;

/**
 * 跨样板增殖环求解器（阶段 2）.
 * <p>对 {@link CycleAnalyzer.Analysis} 判定为增殖环的简单环求闭式解:
 * 种子保留 + 贷款法整批模拟（与阶段 1 自引用同一模式）——沿环正向依次以
 * 原生 {@code CraftingTreeProcess.request} 整批执行各样板,环中间产物的消耗/产出
 * 在模拟库存内闭合（每步产出恰好等于下步消耗）,仅请求物有净增益.</p>
 */
public final class CycleSolver {

    /**
     * 求解结果.
     */
    public enum SolveResult {
        /** 成功,模拟库存已记账（调用方构建计划）. */
        SUCCESS,
        /** 不适用（无种子/输入不足等）,调用方应回落原生. */
        FALLBACK,
        /** 数值溢出（天文数字订单）,调用方应产出 O(1) 缺料计划. */
        OVERFLOW
    }

    private CycleSolver() {
    }

    /**
     * 尝试以增殖环闭式解满足请求.
     *
     * @param inv 以网络快照为父的子模拟库存（不得 ignore 请求物）
     */
    public static SolveResult trySolve(ICraftingService craftingService, CraftingCalculation job,
            CycleAnalyzer.Analysis analysis, ChildCraftingSimulationState inv, AEKey what, long target)
            throws InterruptedException {
        long seed = analysis.seed();

        // 1) 种子校验
        long stock = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        if (stock < seed) {
            return SolveResult.FALLBACK;
        }

        // 注意:不做"库存直接交付"(fromStock)——AE2 执行模型只认样板产出作为交付来源,
        // 交付量一律由环运转产出,种子保留,余量执行结束返回网络.
        long remaining = target;

        if (remaining > 0) {
            long rounds = (remaining + analysis.netGain() - 1) / analysis.netGain();
            // T_i = rounds × timesPerRound[i],任一溢出即天文数字订单
            long[] totalTimes = new long[analysis.timesPerRound().length];
            for (int i = 0; i < totalTimes.length; i++) {
                if (analysis.timesPerRound()[i] != 0 && rounds > Long.MAX_VALUE / analysis.timesPerRound()[i]) {
                    return SolveResult.OVERFLOW;
                }
                totalTimes[i] = rounds * analysis.timesPerRound()[i];
            }

            // 3) 贷款法:首步消耗 totalTimes[0]×inPer 的请求物,而净产出在末步才回到库存,
            // 借入 (总消耗 - 种子) 使整批通过,产出后归还.
            long firstStepInPer = analysis.steps().get(0).inPer();
            if (totalTimes[0] > 0 && firstStepInPer > Long.MAX_VALUE / totalTimes[0]) {
                return SolveResult.OVERFLOW;
            }
            long totalConsume = totalTimes[0] * firstStepInPer;
            long loan = totalConsume - seed;
            if (loan < 0 || (loan > 0 && seed > totalConsume)) {
                return SolveResult.OVERFLOW; // 防御,理论不可达
            }

            CraftingTreeNode rootNode = new CraftingTreeNode(craftingService, job, what, 1, null, -1);
            if (loan > 0) {
                inv.insert(what, loan, Actionable.MODULATE);
            }
            try {
                for (int i = 0; i < analysis.steps().size(); i++) {
                    if (totalTimes[i] <= 0) {
                        continue;
                    }
                    CraftingTreeProcess pro = new CraftingTreeProcess(craftingService, job,
                            analysis.steps().get(i).pattern(), rootNode);
                    Ae2CraftingReflect.treeProcessRequest(pro, inv, totalTimes[i]);
                }
            } catch (CraftBranchFailure failure) {
                return SolveResult.FALLBACK; // 环外输入不足 → 原生兜底(缺料报告)
            } finally {
                if (loan > 0) {
                    inv.extract(what, loan, Actionable.MODULATE);
                }
            }

            // 4) 结算:模拟库存 = 种子 + rounds×netGain,取走交付量,种子保留
            long avail = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
            long keep = avail > remaining ? seed : 0;
            long drain = inv.extract(what, Math.min(remaining, Math.max(0, avail - keep)),
                    Actionable.MODULATE);
            remaining -= drain;
            if (remaining > 0) {
                return SolveResult.FALLBACK; // 理论不可达,保险起见回落
            }
        }
        return SolveResult.SUCCESS;
    }
}
