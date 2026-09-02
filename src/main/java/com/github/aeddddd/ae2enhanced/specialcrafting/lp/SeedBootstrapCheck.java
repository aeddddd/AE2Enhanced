package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.FlowReconciler;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;

/**
 * 种子自举校验（方案 L §4.4,M4）.
 * <p>LP 是实数松弛:守恒方程不感知"启动可行性"——净增环（如 1A→2A）在零库存下的
 * 正执行数是 LP 合法解，但物理上无法启动（第一次执行无输入）。本类对单元 LP 解做
 * <b>逐轮可行执行模拟</b>,回答"是否存在可行调度":</p>
 * <ul>
 * <li>每轮按登记序访问尚有剩余次数的变量,执行 {@code min(剩余, 库存可行量)}
 * 并即时记账（消费输入/产出入账）;</li>
 * <li><b>稳态外推</b>:若一轮结束后所有键可用量净非减,则该轮执行向量可无限重复
 * （归纳:可用量只增不减,下轮同向量仍可行）——按剩余次数一次性放大,
 * 净零催化环（每轮恒 1 次）与净增环（每轮翻倍）都在 O(1)~O(log) 轮收敛;</li>
 * <li>一轮零进展 = 种子不足（无任何执行可启动）;轮数超 {@value #MAX_ROUNDS}
 * 防御上限同样按失败处理（安全方向:误判只导致禁约束重解/赤字,不产生错计划）;</li>
 * <li>环外输入按 LP 折算量视为到位（冷凝序保证上游单元先解先产）;发射台键
 * （canEmitFor）视为无限供给（原生同语义）.</li>
 * </ul>
 * 顺序敏感的原料竞争下本判定是<b>充分条件</b>（模拟成功必可行;失败可能假阴性,
 * 由上层禁约束重解/降级赤字兜底,终止性由重解轮数上限保证）。
 */
public final class SeedBootstrapCheck {

    /** 轮数硬上限(防御;稳态外推下正常收敛远低于此). */
    private static final int MAX_ROUNDS = 4096;

    private SeedBootstrapCheck() {
    }

    /** 校验结果. */
    public static final class Verdict {
        /** true = 全部执行数均可调度(存在可行执行序). */
        public final boolean feasible;
        /** 可行执行水位(与输入 executions 对齐;feasible=true 时等于原计数). */
        public final double[] levels;
        /** 单元键终态可用量(canon 键;截断路径赤字补差用). */
        public final Map<IAEItemStack, Double> finalAvail;
        /** 实际消耗轮数(诊断;区分"零进展卡死"与"轮数耗尽"). */
        public final int roundsUsed;
        /** 失败原因(诊断:stuck=零进展, roundLimit=轮数耗尽;成功为 null). */
        @javax.annotation.Nullable
        public final String failReason;

        Verdict(boolean feasible, double[] levels, Map<IAEItemStack, Double> finalAvail, int roundsUsed,
                String failReason) {
            this.feasible = feasible;
            this.levels = levels;
            this.finalAvail = finalAvail;
            this.roundsUsed = roundsUsed;
            this.failReason = failReason;
        }

        /** 按样板汇总的可行水位(禁约束上界用;变体合并). */
        public Map<ICraftingPatternDetails, Double> levelByPattern(List<Execution> executions) {
            Map<ICraftingPatternDetails, Double> byPattern = new java.util.IdentityHashMap<>();
            for (int j = 0; j < executions.size(); j++) {
                byPattern.merge(executions.get(j).pattern, this.levels[j], Double::sum);
            }
            return byPattern;
        }
    }

    /**
     * 校验单元 LP 解的可调度性.
     *
     * @param cc 合成网格(发射台判定)
     * @param unitKeys 单元键集(canon;库存种子取自 stock)
     * @param stock 网络库存(canon 键 → 数量)
     * @param executions LP 执行记录(实数计数)
     * @param seedSupply 自返还种子供给(canon 键 → 一次性种子量;催化剂型容器
     *                   净消耗为零,折算供给不含它,但首次点火需要实物种子)
     */
    public static Verdict check(ICraftingGrid cc, com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex index,
            List<IAEItemStack> unitKeys, Map<IAEItemStack, Long> stock, List<Execution> executions,
            Map<IAEItemStack, Double> seedSupply) {
        int n = executions.size();
        // 变量输入/输出表(变体按 builder 同口径还原)
        List<Map<IAEItemStack, Double>> inputs = new ArrayList<>(n);
        List<Map<IAEItemStack, Double>> outputs = new ArrayList<>(n);
        Map<IAEItemStack, Boolean> unitMembership = new HashMap<>();
        for (IAEItemStack key : unitKeys) {
            unitMembership.put(key, Boolean.TRUE);
        }
        for (Execution exec : executions) {
            Map<IAEItemStack, Double> in = new LinkedHashMap<>();
            for (Map.Entry<IAEItemStack, Long> e : SccLpModelBuilder
                    .condensedInputs(exec.pattern, exec.variantInputs).entrySet()) {
                in.put(e.getKey(), (double) e.getValue());
            }
            Map<IAEItemStack, Double> out = new LinkedHashMap<>();
            for (IAEItemStack o : exec.pattern.getCondensedOutputs()) {
                if (o != null) {
                    out.merge(RecursiveCraftingHelper.canon(o), (double) o.getStackSize(), Double::sum);
                }
            }
            // 容器/配方返还同为产出(三层同口径):催化环/桶复用的自举可行性
            // 取决于返还回记(如蛋糕返还空桶供给奶桶合成)
            for (Map.Entry<IAEItemStack, Long> ret : FlowReconciler.returnsPerCraft(exec.pattern).entrySet()) {
                out.merge(ret.getKey(), (double) ret.getValue(), Double::sum);
            }
            inputs.add(in);
            outputs.add(out);
        }

        // 可用量初始化:单元内键 = 库存(发射台 = 无限);环外键 = LP 折算供给(按需到位)
        Map<IAEItemStack, Double> avail = new HashMap<>();
        Map<IAEItemStack, Boolean> infinite = new HashMap<>();
        for (int j = 0; j < n; j++) {
            double count = executions.get(j).count;
            for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                IAEItemStack key = e.getKey();
                if (index.canEmit(key)) {
                    infinite.put(key, Boolean.TRUE);
                } else if (!unitMembership.containsKey(key)) {
                    avail.merge(key, e.getValue() * count, Double::sum);
                }
            }
        }
        for (IAEItemStack key : unitKeys) {
            if (index.canEmit(key)) {
                infinite.put(key, Boolean.TRUE);
            } else {
                avail.put(key, (double) stock.getOrDefault(key, 0L));
            }
        }
        // 自返还种子供给:催化剂型容器净零消耗,折算供给不含,但点火需要实物种子
        for (Map.Entry<IAEItemStack, Double> seed : seedSupply.entrySet()) {
            if (!infinite.containsKey(seed.getKey())) {
                avail.merge(seed.getKey(), seed.getValue(), Double::sum);
            }
        }

        double[] remaining = new double[n];
        // 逐变量容差:max(绝对 1e-6, 相对 1e-6×count)——大数量级(1e5+)解的浮点尘埃
        // (≈1e-8 相对)不得误判为"卡住剩余"(尘埃卡死会导致禁约束空转)
        double[] tol = new double[n];
        for (int j = 0; j < n; j++) {
            remaining[j] = executions.get(j).count;
            tol[j] = Math.max(1e-6, 1e-6 * executions.get(j).count);
        }
        double[] executed = new double[n];

        // 增益优先访问序:净增单元材料的变量先执行——净增环先放大种子再被转移消费,
        // 避免"转移样板抢跑种子"造成的假阴性(顺序敏感竞争仍是充分条件,见类注释)
        Integer[] order = new Integer[n];
        for (int j = 0; j < n; j++) {
            order[j] = j;
        }
        double[] netGain = new double[n];
        for (int j = 0; j < n; j++) {
            double in = 0;
            for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                if (unitMembership.containsKey(e.getKey())) {
                    in += e.getValue();
                }
            }
            double out = 0;
            for (Map.Entry<IAEItemStack, Double> e : outputs.get(j).entrySet()) {
                if (unitMembership.containsKey(e.getKey())) {
                    out += e.getValue();
                }
            }
            netGain[j] = out - in;
        }
        java.util.Arrays.sort(order, (u, v) -> Double.compare(netGain[v], netGain[u]));

        int rounds = 0;
        boolean reserveForGain = true; // 卡死防御:保留过激致整轮零进展时退化为无保留贪心
        while (rounds++ < MAX_ROUNDS) {
            boolean anyRemaining = false;
            for (int j = 0; j < n; j++) {
                if (remaining[j] > tol[j]) {
                    anyRemaining = true;
                    break;
                }
            }
            if (!anyRemaining) {
                return new Verdict(true, executed, avail, rounds, null);
            }
            Map<IAEItemStack, Double> roundStart = new HashMap<>(avail);
            double[] roundExec = new double[n];
            boolean progressed = false;
            // 增益相:增益源(净增单元材料)反复执行至耗尽——净增环先把种子放大到
            // 目标水位,再交给转移消费;混在同趟会被中性方向抢跑种子造成假阴性
            boolean gainProgress = true;
            int gainPasses = 0;
            while (gainProgress && gainPasses++ < MAX_ROUNDS) {
                gainProgress = false;
                for (int j = 0; j < n; j++) {
                    if (netGain[j] <= 0 || remaining[j] <= tol[j]) {
                        continue;
                    }
                    double cap = capacity(j, remaining, inputs, avail, infinite);
                    if (cap >= remaining[j] - tol[j]) {
                        cap = remaining[j]; // 尘埃内收齐:整批完成,不留浮点尾巴
                    }
                    if (cap <= 1e-9) {
                        continue;
                    }
                    gainProgress = progressed = true;
                    roundExec[j] += cap;
                    apply(j, cap, remaining, executed, inputs, outputs, avail, infinite);
                }
            }
            // 中性相:其余变量按增益降序执行至收敛.竞争键(多个可启动变量的共同
            // 输入)按各变量剩余需求比例快照分配——贪心全量访问会让先访问变量吃光
            // 共享种子、饿死增益源的上游(θ 环 crush/charge 争同一原料的假阴性);
            // 另对每个仍可启动的增益变量保留"一次批量"所需(增益相已先跑,
            // 未点火即等待中性产物)——否则中性变量会吃光共享种子饿死尚未点火的
            // 增益源(X4:中性 p1 吃光 32 石,增益 p2 无法启动;分母计入全部剩余
            // 需求的比例式会几何衰减,同样到不了点火水位)
            boolean neutralProgress = true;
            int neutralPasses = 0;
            while (neutralProgress && neutralPasses++ < MAX_ROUNDS) {
                neutralProgress = false;
                // 快照:竞争键总需求(仅中性变量)+ 增益变量一次批量保留
                Map<IAEItemStack, Double> contested = new HashMap<>();
                Map<IAEItemStack, Double> reserved = new HashMap<>();
                for (int j = 0; j < n; j++) {
                    if (remaining[j] <= tol[j]) {
                        continue;
                    }
                    for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                        if (infinite.containsKey(e.getKey())) {
                            continue;
                        }
                        if (netGain[j] > 0) {
                            if (reserveForGain) {
                                reserved.merge(e.getKey(), e.getValue(), Double::sum);
                            }
                        } else {
                            contested.merge(e.getKey(), e.getValue() * remaining[j], Double::sum);
                        }
                    }
                }
                // 快照式分配:先按快照算出全部变量的本趟可行量,再统一入账
                // (中性总需求 ≤ 保留后可用量时退化为全量贪心;否则按需求比例分配)
                double[] caps = new double[n];
                for (int oi = 0; oi < n; oi++) {
                    int j = order[oi];
                    if (netGain[j] > 0 || remaining[j] <= tol[j]) {
                        continue;
                    }
                    double cap = remaining[j];
                    for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                        if (infinite.containsKey(e.getKey())) {
                            continue;
                        }
                        double available = Math.max(0.0, avail.getOrDefault(e.getKey(), 0.0)
                                - reserved.getOrDefault(e.getKey(), 0.0));
                        double totalDemand = contested.getOrDefault(e.getKey(), 0.0);
                        double share = totalDemand > available
                                ? available * (e.getValue() * remaining[j]) / totalDemand
                                : available;
                        cap = Math.min(cap, share / e.getValue());
                    }
                    if (cap >= remaining[j] - tol[j]) {
                        cap = remaining[j]; // 尘埃内收齐
                    }
                    caps[j] = cap;
                }
                for (int oi = 0; oi < n; oi++) {
                    int j = order[oi];
                    if (caps[j] <= 1e-9) {
                        continue;
                    }
                    neutralProgress = progressed = true;
                    roundExec[j] += caps[j];
                    apply(j, caps[j], remaining, executed, inputs, outputs, avail, infinite);
                }
            }
            if (!progressed) {
                if (reserveForGain) {
                    // 保留过激致整轮零进展:退化为无保留贪心重试(总比误判卡死强)
                    reserveForGain = false;
                    continue;
                }
                return new Verdict(false, executed, avail, rounds, "stuck"); // 种子不足:无任何执行可启动
            }
            // 稳态外推:所有键可用量净非减 → 本轮执行向量可无限重复(归纳)
            boolean nonDecreasing = true;
            for (Map.Entry<IAEItemStack, Double> e : avail.entrySet()) {
                if (e.getValue() < roundStart.getOrDefault(e.getKey(), 0.0) - 1e-9) {
                    nonDecreasing = false;
                    break;
                }
            }
            if (nonDecreasing) {
                double roundsLeft = Double.MAX_VALUE;
                for (int j = 0; j < n; j++) {
                    if (roundExec[j] > 1e-9 && remaining[j] > tol[j]) {
                        roundsLeft = Math.min(roundsLeft, Math.ceil(remaining[j] / roundExec[j]));
                    }
                }
                if (roundsLeft >= 1 && roundsLeft != Double.MAX_VALUE) {
                    for (int j = 0; j < n; j++) {
                        if (roundExec[j] <= 1e-9) {
                            continue;
                        }
                        double extra = Math.min(roundsLeft * roundExec[j], remaining[j]);
                        apply(j, extra, remaining, executed, inputs, outputs, avail, infinite);
                    }
                }
            }
        }
        return new Verdict(false, executed, avail, rounds, "roundLimit"); // 超轮数上限:按失败处理(安全方向)
    }

    /** 变量当前可行执行量:min(剩余次数, 逐输入键 可用量/单耗). */
    private static double capacity(int j, double[] remaining, List<Map<IAEItemStack, Double>> inputs,
            Map<IAEItemStack, Double> avail, Map<IAEItemStack, Boolean> infinite) {
        double cap = remaining[j];
        for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
            if (infinite.containsKey(e.getKey())) {
                continue;
            }
            cap = Math.min(cap, avail.getOrDefault(e.getKey(), 0.0) / e.getValue());
        }
        return cap;
    }

    /** 执行 cap 次:扣剩余/记已执行/消费输入/产出入账. */
    private static void apply(int j, double cap, double[] remaining, double[] executed,
            List<Map<IAEItemStack, Double>> inputs, List<Map<IAEItemStack, Double>> outputs,
            Map<IAEItemStack, Double> avail, Map<IAEItemStack, Boolean> infinite) {
        remaining[j] -= cap;
        executed[j] += cap;
        for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
            if (!infinite.containsKey(e.getKey())) {
                avail.merge(e.getKey(), -cap * e.getValue(), Double::sum);
            }
        }
        for (Map.Entry<IAEItemStack, Double> e : outputs.get(j).entrySet()) {
            avail.merge(e.getKey(), cap * e.getValue(), Double::sum);
        }
    }
}
