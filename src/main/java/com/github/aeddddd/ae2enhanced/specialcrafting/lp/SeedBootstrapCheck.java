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
 * 种子自举校验, 方案 L §4.4. LP 是实数松弛, 守恒方程不感知启动可行性, 零库存下净增环
 * 的正执行数是 LP 合法解但物理上无法点火, 本类对单元 LP 解做逐轮可行执行模拟, 回答
 * 是否存在可行调度. 模拟成功必可行, 失败可能假阴性, 由上层禁约束重解兜底.
 */
public final class SeedBootstrapCheck {

    /** 轮数硬上限, 防御用, 正常收敛远低于此. */
    private static final int MAX_ROUNDS = 4096;

    private SeedBootstrapCheck() {
    }

    /** 校验结果. */
    public static final class Verdict {
        /** true = 全部执行数均可调度, 存在可行执行序. */
        public final boolean feasible;
        /** 可行执行水位, 与输入 executions 对齐, feasible=true 时等于原计数. */
        public final double[] levels;
        /** 单元键终态可用量, canon 键, 截断路径赤字补差用. */
        public final Map<IAEItemStack, Double> finalAvail;
        /** 实际消耗轮数, 诊断用, 区分零进展卡死与轮数耗尽. */
        public final int roundsUsed;
        /** 失败原因, stuck=零进展, roundLimit=轮数耗尽, 成功为 null. */
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

        /** 按样板汇总的可行水位, 禁约束上界用, 供变体合并. */
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
     * @param cc 合成网格, 发射台判定
     * @param unitKeys 单元键集, canon, 库存种子取自 stock
     * @param stock 网络库存, canon 键到数量
     * @param executions LP 执行记录, 实数计数
     * @param seedSupply 自返还种子供给, canon 键到一次性种子量, 催化剂型容器净消耗为零
     */
    public static Verdict check(ICraftingGrid cc, com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex index,
            List<IAEItemStack> unitKeys, Map<IAEItemStack, Long> stock, List<Execution> executions,
            Map<IAEItemStack, Double> seedSupply) {
        int n = executions.size();
        // 变量输入/输出表, 变体按 builder 同口径还原
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
            // 容器与配方返还同为产出, 自举可行性取决于返还回记, 如蛋糕返还空桶供给奶桶合成
            for (Map.Entry<IAEItemStack, Long> ret : FlowReconciler.returnsPerCraft(exec.pattern).entrySet()) {
                out.merge(ret.getKey(), (double) ret.getValue(), Double::sum);
            }
            inputs.add(in);
            outputs.add(out);
        }

        // 可用量初始化: 单元内键取库存, 发射台键视为无限, 环外键按 LP 折算供给按需到位
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
        // 自返还种子供给, 催化剂型容器净消耗为零, 折算供给不含, 但首次点火需要实物种子
        for (Map.Entry<IAEItemStack, Double> seed : seedSupply.entrySet()) {
            if (!infinite.containsKey(seed.getKey())) {
                avail.merge(seed.getKey(), seed.getValue(), Double::sum);
            }
        }

        double[] remaining = new double[n];
        // 逐变量容差为 max(绝对 1e-6, 相对 1e-6×count), 大数量级解的浮点尘埃
        // 不得误判为卡住剩余, 否则禁约束空转
        double[] tol = new double[n];
        for (int j = 0; j < n; j++) {
            remaining[j] = executions.get(j).count;
            tol[j] = Math.max(1e-6, 1e-6 * executions.get(j).count);
        }
        double[] executed = new double[n];

        // 增益优先访问序: 净增单元材料的变量先执行, 净增环先放大种子再被转移消费,
        // 避免转移变量先消费种子造成假阴性
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
        boolean reserveForGain = true; // 卡死防御, 保留过激导致整轮零进展时退化为无保留贪心
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
            // 增益相: 增益源反复执行至耗尽, 净增环先把种子放大到目标水位再交给转移消费.
            // 竞争键按趟首快照的剩余需求比例分配, 防止先访问的增益源占光共享种子,
            // 假阴性会经禁约束压界把真实解空间压没
            boolean gainProgress = true;
            int gainPasses = 0;
            while (gainProgress && gainPasses++ < MAX_ROUNDS) {
                gainProgress = false;
                // 快照: 增益变量对竞争键的总需求与消费者数, 催化/放大器的自键仍计入需求,
                // 但其可行量另行豁免, 见下方自补充分支
                // 水塘分配预处理: 逐键统计小额与大额消费者合计
                Map<IAEItemStack, Double> gainContested = new HashMap<>();
                Map<IAEItemStack, Integer> gainConsumers = new HashMap<>();
                Map<IAEItemStack, Double> gainSmallNeed = new HashMap<>();
                Map<IAEItemStack, Double> gainBigDemand = new HashMap<>();
                for (int j = 0; j < n; j++) {
                    if (netGain[j] <= 0 || remaining[j] <= tol[j]) {
                        continue;
                    }
                    for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                        if (!infinite.containsKey(e.getKey())) {
                            double fullNeed = e.getValue() * remaining[j];
                            gainContested.merge(e.getKey(), fullNeed, Double::sum);
                            gainConsumers.merge(e.getKey(), 1, Integer::sum);
                            if (fullNeed <= avail.getOrDefault(e.getKey(), 0.0)) {
                                gainSmallNeed.merge(e.getKey(), fullNeed, Double::sum);
                            } else {
                                gainBigDemand.merge(e.getKey(), fullNeed, Double::sum);
                            }
                        }
                    }
                }
                Map<IAEItemStack, Double> passStart = new HashMap<>(avail);
                // 逐变量即算即入账, 净增益降序. 自补充键免比例截流, 按比例节流会让
                // 放大器微步衰减无法点火. 竞争键按趟首快照比例分配, 独占与非竞争键按
                // 入账时刻可用量即算, 同趟下游立见上游产物, 否则趟首零库存会把唯一
                // 消费者锁死在零份额上, 振荡环无法同趟闭合
                double[] passExec = new double[n];
                Map<IAEItemStack, Double> passDelta = new HashMap<>();
                for (int oi = 0; oi < n; oi++) {
                    int j = order[oi];
                    if (netGain[j] <= 0 || remaining[j] <= tol[j]) {
                        continue;
                    }
                    double cap = remaining[j];
                    for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                        if (infinite.containsKey(e.getKey())) {
                            continue;
                        }
                        double available = avail.getOrDefault(e.getKey(), 0.0);
                        double limit;
                        if (outputs.get(j).getOrDefault(e.getKey(), 0.0) >= e.getValue()) {
                            limit = available / e.getValue(); // 催化/放大, 非净消耗, 免截流
                        } else {
                            double base = passStart.getOrDefault(e.getKey(), 0.0);
                            double totalDemand = gainContested.getOrDefault(e.getKey(), 0.0);
                            limit = gainConsumers.getOrDefault(e.getKey(), 0) > 1 && totalDemand > base
                                    ? waterShare(base, remaining[j], e.getValue(), totalDemand,
                                            gainSmallNeed.getOrDefault(e.getKey(), 0.0),
                                            gainBigDemand.getOrDefault(e.getKey(), 0.0))
                                    : available / e.getValue();
                        }
                        cap = Math.min(cap, limit);
                    }
                    if (cap >= remaining[j] - tol[j]) {
                        cap = remaining[j]; // 尘埃内收齐, 整批完成不留浮点尾巴
                    }
                    // 安全钳: 不超过入账时刻可用量, 防快照份额尾差, 自补充执行只放大后续可用量
                    for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                        if (!infinite.containsKey(e.getKey())) {
                            cap = Math.min(cap, avail.getOrDefault(e.getKey(), 0.0) / e.getValue());
                        }
                    }
                    if (cap >= remaining[j] - tol[j]) {
                        cap = remaining[j];
                    }
                    if (cap <= 1e-9) {
                        continue;
                    }
                    gainProgress = progressed = true;
                    passExec[j] = cap;
                    roundExec[j] += cap;
                    apply(j, cap, remaining, executed, inputs, outputs, avail, infinite);
                    for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                        if (unitMembership.containsKey(e.getKey())) {
                            passDelta.merge(e.getKey(), -cap * e.getValue(), Double::sum);
                        }
                    }
                    for (Map.Entry<IAEItemStack, Double> e : outputs.get(j).entrySet()) {
                        if (unitMembership.containsKey(e.getKey())) {
                            passDelta.merge(e.getKey(), cap * e.getValue(), Double::sum);
                        }
                    }
                }
                // 趟级外推: 按剩余完成数与键漂移预算一次性放大本趟向量.
                // 本趟向量对单元键净非减时按稳态外推, 允许微小负 delta, 外推末端按 tol 级尾巴收齐
                if (gainProgress) {
                    double roundsLeft = extrapolateRounds(passExec, passDelta, remaining, tol, avail, n);
                    if (roundsLeft > 0) {
                        for (int j = 0; j < n; j++) {
                            if (passExec[j] <= 1e-9) {
                                continue;
                            }
                            double extra = Math.min(roundsLeft * passExec[j], remaining[j]);
                            extra = snapExtra(extra, remaining, executed, tol, j);
                            if (extra <= 1e-9) {
                                continue;
                            }
                            roundExec[j] += extra;
                            apply(j, extra, remaining, executed, inputs, outputs, avail, infinite);
                        }
                    }
                }
            }
            // 中性相: 其余变量按增益降序执行至收敛, 竞争键按剩余需求比例快照分配.
            // 对每个仍可启动的增益变量保留一次批量所需, 否则中性变量会占光共享
            // 种子, 使尚未点火的增益源分不到份额
            boolean neutralProgress = true;
            int neutralPasses = 0;
            while (neutralProgress && neutralPasses++ < MAX_ROUNDS) {
                neutralProgress = false;
                // 快照: 竞争键总需求仅计中性变量, 另加增益变量一次批量保留, 基准取趟首可用量
                Map<IAEItemStack, Double> contested = new HashMap<>();
                Map<IAEItemStack, Integer> neutralConsumers = new HashMap<>();
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
                            neutralConsumers.merge(e.getKey(), 1, Integer::sum);
                        }
                    }
                }
                Map<IAEItemStack, Double> passStart = new HashMap<>(avail);
                // 水塘分配预处理, 基准为趟首可用量减去增益变量一次批量保留
                Map<IAEItemStack, Double> smallNeed = new HashMap<>();
                Map<IAEItemStack, Double> bigDemand = new HashMap<>();
                for (int j = 0; j < n; j++) {
                    if (netGain[j] > 0 || remaining[j] <= tol[j]) {
                        continue;
                    }
                    for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                        if (infinite.containsKey(e.getKey())) {
                            continue;
                        }
                        double fullNeed = e.getValue() * remaining[j];
                        double base = Math.max(0.0, passStart.getOrDefault(e.getKey(), 0.0)
                                - reserved.getOrDefault(e.getKey(), 0.0));
                        if (fullNeed <= base) {
                            smallNeed.merge(e.getKey(), fullNeed, Double::sum);
                        } else {
                            bigDemand.merge(e.getKey(), fullNeed, Double::sum);
                        }
                    }
                }
                // 逐变量即算即入账, 口径与增益相相同: 自补充键免保留免截流,
                // 竞争键按扣除保留后的趟首快照水塘分配
                double[] passExec = new double[n];
                Map<IAEItemStack, Double> passDelta = new HashMap<>();
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
                        double limit;
                        if (outputs.get(j).getOrDefault(e.getKey(), 0.0) >= e.getValue()) {
                            limit = avail.getOrDefault(e.getKey(), 0.0) / e.getValue();
                        } else {
                            double totalDemand = contested.getOrDefault(e.getKey(), 0.0);
                            double available = Math.max(0.0, passStart.getOrDefault(e.getKey(), 0.0)
                                    - reserved.getOrDefault(e.getKey(), 0.0));
                            if (neutralConsumers.getOrDefault(e.getKey(), 0) > 1
                                    && totalDemand > available) {
                                limit = waterShare(available, remaining[j], e.getValue(), totalDemand,
                                        smallNeed.getOrDefault(e.getKey(), 0.0),
                                        bigDemand.getOrDefault(e.getKey(), 0.0));
                            } else {
                                limit = Math.max(0.0, avail.getOrDefault(e.getKey(), 0.0)
                                        - reserved.getOrDefault(e.getKey(), 0.0)) / e.getValue();
                            }
                        }
                        cap = Math.min(cap, limit);
                    }
                    if (cap >= remaining[j] - tol[j]) {
                        cap = remaining[j]; // 尘埃内收齐
                    }
                    // 安全钳: 不超过入账时刻可用量, 与增益相相同口径
                    for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                        if (!infinite.containsKey(e.getKey())) {
                            cap = Math.min(cap, avail.getOrDefault(e.getKey(), 0.0) / e.getValue());
                        }
                    }
                    if (cap >= remaining[j] - tol[j]) {
                        cap = remaining[j];
                    }
                    if (cap <= 1e-9) {
                        continue;
                    }
                    neutralProgress = progressed = true;
                    passExec[j] = cap;
                    roundExec[j] += cap;
                    apply(j, cap, remaining, executed, inputs, outputs, avail, infinite);
                    for (Map.Entry<IAEItemStack, Double> e : inputs.get(j).entrySet()) {
                        if (unitMembership.containsKey(e.getKey())) {
                            passDelta.merge(e.getKey(), -cap * e.getValue(), Double::sum);
                        }
                    }
                    for (Map.Entry<IAEItemStack, Double> e : outputs.get(j).entrySet()) {
                        if (unitMembership.containsKey(e.getKey())) {
                            passDelta.merge(e.getKey(), cap * e.getValue(), Double::sum);
                        }
                    }
                }
                // 趟级外推, 与增益相相同口径
                if (neutralProgress) {
                    double roundsLeft = extrapolateRounds(passExec, passDelta, remaining, tol, avail, n);
                    if (roundsLeft > 0) {
                        for (int j = 0; j < n; j++) {
                            if (passExec[j] <= 1e-9) {
                                continue;
                            }
                            double extra = Math.min(roundsLeft * passExec[j], remaining[j]);
                            extra = snapExtra(extra, remaining, executed, tol, j);
                            if (extra <= 1e-9) {
                                continue;
                            }
                            roundExec[j] += extra;
                            apply(j, extra, remaining, executed, inputs, outputs, avail, infinite);
                        }
                    }
                }
            }
            if (!progressed) {
                if (reserveForGain) {
                    // 保留过激导致整轮零进展, 退化为无保留贪心重试
                    reserveForGain = false;
                    continue;
                }
                return new Verdict(false, executed, avail, rounds, "stuck"); // 种子不足, 无任何执行可启动
            }
            // 轮级外推, 与趟级同口径. 漂移判定只看单元键: 单元外键的种子池按构造
            // 恒足量, 其消耗是预期行为, 纳入判定会让含外部输入的解失去外推资格
            {
                Map<IAEItemStack, Double> roundDelta = new HashMap<>();
                for (Map.Entry<IAEItemStack, Double> e : avail.entrySet()) {
                    if (unitMembership.containsKey(e.getKey())) {
                        roundDelta.merge(e.getKey(), e.getValue()
                                - roundStart.getOrDefault(e.getKey(), 0.0), Double::sum);
                    }
                }
                double roundsLeft = extrapolateRounds(roundExec, roundDelta, remaining, tol, avail, n);
                if (roundsLeft > 0) {
                    for (int j = 0; j < n; j++) {
                        if (roundExec[j] <= 1e-9) {
                            continue;
                        }
                        double extra = Math.min(roundsLeft * roundExec[j], remaining[j]);
                        extra = snapExtra(extra, remaining, executed, tol, j);
                        if (extra <= 1e-9) {
                            continue;
                        }
                        apply(j, extra, remaining, executed, inputs, outputs, avail, infinite);
                    }
                }
            }
        }
        return new Verdict(false, executed, avail, rounds, "roundLimit"); // 超轮数上限, 按失败处理, 安全方向
    }

    /**
     * 外推收齐: 剩余 − extra ≤ max(tol, 1e-5×总数) 时收齐为完成.
     * <p>零松弛环的外推末端会残留 tol 级尾巴, 与尘埃内收齐同语义,
     * 尾巴量相对变量总数不超过 1e-5, 对禁约束水位与截断口径无影响.</p>
     */
    private static double snapExtra(double extra, double[] remaining, double[] executed, double[] tol, int j) {
        if (extra > 0 && remaining[j] - extra <= Math.max(tol[j], 1e-5 * (executed[j] + remaining[j]))) {
            return remaining[j];
        }
        return extra;
    }

    /** 执行 cap 次: 扣剩余, 记已执行, 消费输入, 产出入账. */
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

    /**
     * 水塘分配, 返回本变量在该竞争键上本趟的可执行次数上限.
     * <p>小额消费者直接足额放行, 比例分配会把小额份额稀释到无法点火;
     * 大额消费者按比例分剩余可用量. 小额合计超可用量时退化为全体按比例.</p>
     *
     * @param base 趟首可用量
     * @param remaining 本变量剩余次数
     * @param perCraft 单次消耗
     * @param totalDemand 全体消费者全额需求合计
     * @param smallNeed 小额消费者全额需求合计
     * @param bigDemand 大额消费者全额需求合计
     */
    private static double waterShare(double base, double remaining, double perCraft, double totalDemand,
            double smallNeed, double bigDemand) {
        double fullNeed = perCraft * remaining;
        if (smallNeed <= base && fullNeed <= base) {
            return remaining; // 小额且小额合计不超可用量, 足额完成
        }
        if (smallNeed <= base) {
            // 大额按比例分可用量减去小额合计的部分
            double availForBig = base - smallNeed;
            return bigDemand > 0 ? availForBig * fullNeed / bigDemand / perCraft : 0.0;
        }
        // 小额合计超可用量, 退化为全体按比例
        return base * remaining / totalDemand;
    }

    /**
     * 外推趟数: 剩余完成数与键漂移预算的最小值.
     * <p>本趟向量与剩余近似同比, 按完成数放大即全体同步完成; 漂移预算允许
     * 微小负 delta 外推到可用量耗尽为止, 避免零进展硬磨. 无可外推向量时返回 0.</p>
     */
    private static double extrapolateRounds(double[] passExec, Map<IAEItemStack, Double> passDelta,
            double[] remaining, double[] tol, Map<IAEItemStack, Double> avail, int n) {
        double roundsLeft = Double.MAX_VALUE;
        for (int j = 0; j < n; j++) {
            if (passExec[j] > 1e-9 && remaining[j] > tol[j]) {
                roundsLeft = Math.min(roundsLeft, remaining[j] / passExec[j]);
            }
        }
        if (roundsLeft == Double.MAX_VALUE) {
            return 0; // 无可外推的执行向量, 全部接近完成或未执行
        }
        for (Map.Entry<IAEItemStack, Double> e : passDelta.entrySet()) {
            if (e.getValue() < -1e-9) {
                roundsLeft = Math.min(roundsLeft, avail.getOrDefault(e.getKey(), 0.0) / (-e.getValue()));
            }
        }
        return Math.max(0.0, roundsLeft);
    }
}
