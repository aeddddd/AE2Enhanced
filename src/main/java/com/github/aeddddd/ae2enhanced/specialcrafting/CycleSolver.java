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
 * 跨样板增殖环求解器（阶段 2,泛化版）.
 * <p>对 {@link CycleAnalyzer.Analysis} 判定为增殖环的环键集求闭式解:
 * 各环内物品按前缀分析得出的种子保留 + 贷款法整批模拟——沿环执行顺序依次以
 * 原生 {@code CraftingTreeProcess.request} 整批执行各样板,环内物品的消耗/产出
 * 在模拟库存内闭合（非 root 键每超轮净变化为零）,仅请求物有净增益.
 * 环外输入（辅材等）由子节点原生解析（库存/外部子合成）.</p>
 */
public final class CycleSolver {

    /**
     * 求解结果.
     */
    public enum SolveResult {
        /** 成功,模拟库存已记账（调用方构建计划）. */
        SUCCESS,
        /** 不适用（种子不足/输入不足等）,调用方应回落原生. */
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
        return solveCore(craftingService, job, analysis, inv, what, target, analysis.netGain(),
                analysis.seedsPerKey()[0]);
    }

    /**
     * 尝试以催化环闭式解满足请求:what 不在环键上,是环每超轮发射的环外副产物
     * (如 1A→1X+1B、1B→1A 请求 X).环键种子语义与增殖环一致;what 无种子、
     * 每超轮净得 {@code xPerRound}.
     */
    public static SolveResult trySolveCatalytic(ICraftingService craftingService,
            CraftingCalculation job, CycleAnalyzer.Analysis analysis, long xPerRound,
            ChildCraftingSimulationState inv, AEKey what, long target) throws InterruptedException {
        if (xPerRound <= 0) {
            return SolveResult.FALLBACK;
        }
        return solveCore(craftingService, job, analysis, inv, what, target, xPerRound, 0);
    }

    /**
     * 环闭式解共用内核.
     *
     * @param gainPerRound 每超轮交付键的净得数量(增殖环=环键净增益;催化环=副产物/轮)
     * @param deliverSeed 交付键的保留种子(催化环为 0)
     */
    private static SolveResult solveCore(ICraftingService craftingService, CraftingCalculation job,
            CycleAnalyzer.Analysis analysis, ChildCraftingSimulationState inv, AEKey what, long target,
            long gainPerRound, long deliverSeed) throws InterruptedException {
        var keys = analysis.keys();
        var seeds = analysis.seedsPerKey();
        var batchSeeds = analysis.batchSeedPerKey();
        var times = analysis.timesPerRound();

        // 注意:不做"库存直接交付"(fromStock)——AE2 执行模型只认样板产出作为交付来源,
        // 交付量一律由环运转产出,种子保留,余量执行结束返回网络.
        long remaining = target;
        if (remaining <= 0) {
            return SolveResult.SUCCESS;
        }

        // 溢出安全 ceilDiv:(remaining + gain - 1) 形式在近 Long.MAX 需求下加法回绕成负数,
        // 会被下游误判为"求解失败"而整单回落原生(大网络上即高请求计算卡死)
        long rounds = remaining / gainPerRound + (remaining % gainPerRound != 0 ? 1 : 0);
        // T_i = rounds × timesPerRound[i],任一溢出即天文数字订单
        long[] totalTimes = new long[times.length];
        for (int i = 0; i < totalTimes.length; i++) {
            if (times[i] != 0 && rounds > Long.MAX_VALUE / times[i]) {
                return SolveResult.OVERFLOW;
            }
            totalTimes[i] = rounds * times[i];
        }
        // 贷款水位预检:各环键的"每轮量级"(前缀种子与每轮总消耗取大者)×轮数同样必须
        // 可表示——否则批量模拟的库存水位本身超 long,贷款公式无法补足,模拟必欠资失败
        // (CraftBranchFailure),提前按天文数字处理,避免无效模拟与整单回落原生
        for (int i = 0; i < keys.size(); i++) {
            long perRound = Math.max(seeds[i], batchSeeds[i]);
            if (perRound > 0 && rounds > Long.MAX_VALUE / perRound) {
                return SolveResult.OVERFLOW;
            }
        }
        // IO 侧守卫:批量模拟经原生 CraftingTreeProcess.request(无饱和乘法),每步的
        // 输入/输出×总次数同样必须可表示,否则记账回绕错乱、结算必败
        var stepsForCheck = analysis.steps();
        for (int i = 0; i < stepsForCheck.size(); i++) {
            if (totalTimes[i] <= 0) {
                continue;
            }
            var pattern = stepsForCheck.get(i).pattern();
            for (var input : pattern.getInputs()) {
                long mult = input.getMultiplier();
                if (mult <= 0) {
                    continue;
                }
                for (var candidate : input.getPossibleInputs()) {
                    long amt = candidate.amount();
                    if (amt <= 0) {
                        continue;
                    }
                    if (amt > Long.MAX_VALUE / mult) {
                        return SolveResult.OVERFLOW;
                    }
                    if (totalTimes[i] > Long.MAX_VALUE / (amt * mult)) {
                        return SolveResult.OVERFLOW;
                    }
                }
            }
            for (var output : pattern.getOutputs()) {
                long amt = output.amount();
                if (amt > 0 && totalTimes[i] > Long.MAX_VALUE / amt) {
                    return SolveResult.OVERFLOW;
                }
            }
        }

        // 1) 各环内物品种子校验:
        // - 多消费者键(batchSeeds>0):仅需 max(前缀种子, 每轮总消耗)——运行时并发消耗
        //   由超轮配额调度器(RoundQuotaScheduler)闸在每轮以内,不再需要全批次库存;
        // - 单消费者键:仅需前缀启动种子(需求超种子时自我节流,运行时自然交错爬坡).
        long[] requiredStock = new long[seeds.length];
        for (int i = 0; i < keys.size(); i++) {
            requiredStock[i] = batchSeeds[i] > 0 ? Math.max(seeds[i], batchSeeds[i]) : seeds[i];
            if (requiredStock[i] > 0) {
                long stock = inv.extract(keys.get(i), Long.MAX_VALUE, Actionable.SIMULATE);
                if (stock < requiredStock[i]) {
                    SpecialLog.info(
                            "[特殊配方] 环求解回落: {} 种子不足(需要 {},库存 {}{})",
                            keys.get(i), requiredStock[i], stock,
                            batchSeeds[i] > 0 ? ",多消费者键按每轮消耗记账" : "");
                    return SolveResult.FALLBACK;
                }
            }
        }

        // 2) 贷款法(计划期模拟技巧,借还精确对冲,只抬高水位):
        // - 单消费者键:批处理前缀缺口为 (rounds-1)×seed;
        // - 多消费者键:批处理按执行顺序先消耗后产出,缺口为 rounds×前缀种子,但
        //   usedItems 必须恰好 = requiredStock(运行时只需每轮库存),贷款把水位最低点
        //   钳在 库存-requiredStock,深于该值的批量下探由贷款吸收.
        long[] loans = new long[seeds.length];
        for (int i = 0; i < seeds.length; i++) {
            if (seeds[i] <= 0 || rounds - 1 <= 0) {
                continue;
            }
            long dip;
            if (batchSeeds[i] > 0) {
                if (seeds[i] > Long.MAX_VALUE / rounds) {
                    return SolveResult.OVERFLOW;
                }
                dip = rounds * seeds[i] - requiredStock[i];
            } else {
                if (seeds[i] > Long.MAX_VALUE / (rounds - 1)) {
                    return SolveResult.OVERFLOW;
                }
                dip = (rounds - 1) * seeds[i];
            }
            if (dip > 0) {
                loans[i] = dip;
                inv.insert(keys.get(i), loans[i], Actionable.MODULATE);
            }
        }
        try {
            CraftingTreeNode rootNode = new CraftingTreeNode(craftingService, job, what, 1, null, -1);
            for (int i = 0; i < analysis.steps().size(); i++) {
                if (totalTimes[i] <= 0) {
                    continue;
                }
                CraftingTreeProcess pro = new CraftingTreeProcess(craftingService, job,
                        analysis.steps().get(i).pattern(), rootNode);
                Ae2CraftingReflect.treeProcessRequest(pro, inv, totalTimes[i]);
            }
        } catch (CraftBranchFailure failure) {
            SpecialLog.info(
                    "[特殊配方] 环求解回落:环外输入不足({})", failure.toString());
            return SolveResult.FALLBACK; // 环外输入不足 → 原生兜底(缺料报告)
        } finally {
            for (int i = 0; i < seeds.length; i++) {
                if (loans[i] > 0) {
                    inv.extract(keys.get(i), loans[i], Actionable.MODULATE);
                }
            }
        }

        // 3) 结算:模拟库存请求物 = 种子 + rounds×gainPerRound,取走交付量,种子保留
        long avail = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        long keep = avail > remaining ? deliverSeed : 0;
        long drain = inv.extract(what, Math.min(remaining, Math.max(0, avail - keep)),
                Actionable.MODULATE);
        remaining -= drain;
        if (remaining > 0) {
            return SolveResult.FALLBACK; // 理论不可达,保险起见回落
        }
        return SolveResult.SUCCESS;
    }
}
