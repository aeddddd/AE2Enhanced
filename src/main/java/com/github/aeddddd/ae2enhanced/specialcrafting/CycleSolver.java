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

        long rounds = (remaining + analysis.netGain() - 1) / analysis.netGain();
        // T_i = rounds × timesPerRound[i],任一溢出即天文数字订单
        long[] totalTimes = new long[times.length];
        for (int i = 0; i < totalTimes.length; i++) {
            if (times[i] != 0 && rounds > Long.MAX_VALUE / times[i]) {
                return SolveResult.OVERFLOW;
            }
            totalTimes[i] = rounds * times[i];
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
                    com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.info(
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
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.info(
                    "[特殊配方] 环求解回落:环外输入不足({})", failure.toString());
            return SolveResult.FALLBACK; // 环外输入不足 → 原生兜底(缺料报告)
        } finally {
            for (int i = 0; i < seeds.length; i++) {
                if (loans[i] > 0) {
                    inv.extract(keys.get(i), loans[i], Actionable.MODULATE);
                }
            }
        }

        // 3) 结算:模拟库存请求物 = 种子 + rounds×netGain,取走交付量,种子保留
        long avail = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        long keep = avail > remaining ? seeds[0] : 0;
        long drain = inv.extract(what, Math.min(remaining, Math.max(0, avail - keep)),
                Actionable.MODULATE);
        remaining -= drain;
        if (remaining > 0) {
            return SolveResult.FALLBACK; // 理论不可达,保险起见回落
        }
        return SolveResult.SUCCESS;
    }
}
