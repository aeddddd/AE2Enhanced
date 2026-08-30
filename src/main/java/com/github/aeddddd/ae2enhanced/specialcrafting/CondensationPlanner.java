package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpResult;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpModelBuilder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SeedBootstrapCheck;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.SccSolution;

/**
 * 冷凝分层驱动器（方案 L §10.3 骨架层,M3）.
 * <p>把"请求 → 逐键展开"组织为冷凝 DAG 上的逐单元求解:</p>
 * <ul>
 * <li><b>求解单元</b>:SCC 经"输出共享并查集"合并后的组——同样板的全部凝聚输出
 * 必属同一单元,故每个样板恰好归属一个单元（跨单元样板/committed 语义不需要;
 * 且冷凝图按构造无环:单元间若互达,其键早已同属一个 SCC）;</li>
 * <li><b>需求传播</b>:边 U→V = "U 的样板消耗 V 的键";按冷凝拓扑序（消费方先解）
 * 逐单元求解,LP 环外折算(externalDemands)累加为上游单元需求——拓扑序保证
 * 单元被解时其全部下游需求已知,共享库存/原料键只被结算一次;</li>
 * <li><b>逐单元求解</b>:统一走 {@link SccLpSolve} 字典序两阶段 LP（单键单元
 * 亦精确,阶段② ε·rank 体现样板优先级）;无样板单元走快速路径
 * （发射台零成本,否则 赤字 = 需求 − 库存）;</li>
 * <li><b>降级(D4)</b>:单元 LP 非 OPTIMAL → 该单元整体"库存直通"
 * （赤字 = 需求 − 库存,O(单元) 一次性记账,不再传播上游需求）;</li>
 * <li><b>根库存语义</b>:请求物自身库存计入 stock（净增环种子自举需要真实库存;
 * 与 DAG 路径 invIgnore 的原生镜像语义不同——LP 路径的守恒校验在 M5 对账层）.</li>
 * </ul>
 * 产物为扁平计数解（执行记录 + 赤字 + 统计）,计划树物化在 M5 完成。
 * 本类纯计算、线程安全;所有键均为 canon（{@link RecursiveCraftingHelper#canon}）。
 */
public final class CondensationPlanner {

    private CondensationPlanner() {
    }

    /** 单元解(M5 对账重演需要单元边界与求解序;顺序 = 求解序,消费方在前). */
    public static final class UnitSolution {
        public final List<IAEItemStack> keys;
        public final List<Execution> executions;
        public final Map<IAEItemStack, Double> deficits;

        UnitSolution(List<IAEItemStack> keys, List<Execution> executions,
                Map<IAEItemStack, Double> deficits) {
            this.keys = keys;
            this.executions = executions;
            this.deficits = deficits;
        }
    }

    /** 驱动器产物(扁平计数解 + 逐单元解). */
    public static final class LpPlanOutcome {
        /** 全部单元 OPTIMAL = true;任一单元降级(库存直通/截断) = false. */
        public final boolean allOptimal;
        /** 执行记录(跨单元聚合;同一样板在不同单元不会出现——单元唯一归属). */
        public final List<Execution> executions;
        /** 赤字(canon 键 → 缺料量;LP 意义精确最优 + 降级直通). */
        public final Map<IAEItemStack, Double> deficits;
        /** 逐单元解(求解序,消费方在前;对账重演按逆序 = 上游先产). */
        public final List<UnitSolution> unitSolutions;
        /** 求解单元数(诊断). */
        public final int units;
        /** LP 单元数(含两阶段;诊断). */
        public final int lpUnits;
        /** 单纯形总迭代数(诊断埋点). */
        public final int iterations;
        /** 降级单元数(D4 库存直通/截断;验收口径要求恒 0). */
        public final int degradedUnits;
        /** 降级原因清单(诊断;每条 = 单元代表键 + 原因). */
        public final List<String> degradedReasons;
        /** 墙钟毫秒(诊断). */
        public final long wallMs;

        LpPlanOutcome(boolean allOptimal, List<Execution> executions, Map<IAEItemStack, Double> deficits,
                List<UnitSolution> unitSolutions, int units, int lpUnits, int iterations, int degradedUnits,
                List<String> degradedReasons, long wallMs) {
            this.allOptimal = allOptimal;
            this.executions = executions;
            this.deficits = deficits;
            this.unitSolutions = unitSolutions;
            this.units = units;
            this.lpUnits = lpUnits;
            this.iterations = iterations;
            this.degradedUnits = degradedUnits;
            this.degradedReasons = degradedReasons;
            this.wallMs = wallMs;
        }
    }

    /**
     * 求解整单:从根请求出发,冷凝 DAG 拓扑逐单元 LP.
     *
     * @param stock 网络库存快照(canon 键 → 数量;调用方一次性构建)
     */
    public static LpPlanOutcome solve(ICraftingGrid cc, NetworkPatternIndex index,
            IAEItemStack what, long target, Map<IAEItemStack, Long> stock) {
        long start = System.nanoTime();
        IAEItemStack rootKey = RecursiveCraftingHelper.canon(what);

        // 1) 求解单元划分:SCC 键图 + "同样板输出共享"并查集合并
        UnionFind units = buildUnits(cc, index);

        // 2) 从根单元发现可达单元与单元间边(U→V:U 的样板消耗 V 的键)
        IAEItemStack rootUnit = units.find(rootKey);
        Map<IAEItemStack, List<IAEItemStack>> unitKeys = new LinkedHashMap<>();
        Map<IAEItemStack, Set<IAEItemStack>> upstream = new LinkedHashMap<>(); // U → 上游单元集
        Map<IAEItemStack, Set<IAEItemStack>> downstream = new LinkedHashMap<>(); // V → 下游单元集
        Map<IAEItemStack, List<ICraftingPatternDetails>> unitPatterns = new HashMap<>();
        ArrayDeque<IAEItemStack> queue = new ArrayDeque<>();
        queue.add(rootUnit);
        Set<IAEItemStack> seenUnits = new java.util.HashSet<>();
        seenUnits.add(rootUnit);
        while (!queue.isEmpty()) {
            IAEItemStack unit = queue.poll();
            List<IAEItemStack> keys = units.members(unit);
            unitKeys.put(unit, keys);
            List<ICraftingPatternDetails> patterns = collectPatterns(cc, index, keys);
            unitPatterns.put(unit, patterns);
            Set<IAEItemStack> ups = new java.util.LinkedHashSet<>();
            for (ICraftingPatternDetails p : patterns) {
                for (IAEItemStack in : p.getCondensedInputs()) {
                    if (in == null || in.getStackSize() <= 0) {
                        continue;
                    }
                    IAEItemStack up = units.find(RecursiveCraftingHelper.canon(in));
                    if (!up.equals(unit) && ups.add(up)) {
                        downstream.computeIfAbsent(up, k -> new java.util.LinkedHashSet<>()).add(unit);
                        if (seenUnits.add(up)) {
                            queue.add(up);
                        }
                    }
                }
            }
            upstream.put(unit, ups);
        }

        // 3) 冷凝拓扑序(Kahn:消费方先解;按构造无环)
        List<IAEItemStack> order = topologicalOrder(rootUnit, upstream, downstream);

        // 4) 逐单元求解:需求累加 → LP/快速路径 → 环外折算向后传播
        Map<IAEItemStack, Map<IAEItemStack, Double>> demands = new HashMap<>();
        demands.computeIfAbsent(rootUnit, k -> new HashMap<>()).put(rootKey, (double) target);
        List<Execution> executions = new ArrayList<>();
        Map<IAEItemStack, Double> deficits = new LinkedHashMap<>();
        List<UnitSolution> unitSolutions = new ArrayList<>();
        List<String> degradedReasons = new ArrayList<>();
        int lpUnits = 0;
        int iterations = 0;
        int degradedUnits = 0;
        for (IAEItemStack unit : order) {
            Map<IAEItemStack, Double> unitDemand = demands.getOrDefault(unit, Collections.emptyMap());
            List<ICraftingPatternDetails> patterns = unitPatterns.getOrDefault(unit, Collections.emptyList());
            if (patterns.isEmpty()) {
                // 快速路径:无样板单元(原料/发射台)——赤字 = 需求 − 库存
                for (Map.Entry<IAEItemStack, Double> d : unitDemand.entrySet()) {
                    if (d.getValue() <= 0 || cc.canEmitFor(d.getKey())) {
                        continue;
                    }
                    double deficit = d.getValue() - stock.getOrDefault(d.getKey(), 0L);
                    if (deficit > 0) {
                        deficits.merge(d.getKey(), deficit, Double::sum);
                    }
                }
                continue;
            }
            lpUnits++;
            UnitResult result = solveUnitWithBootstrap(cc, index, unitKeys.get(unit), stock, unitDemand);
            iterations += result.iterations;
            if (result.degraded) {
                degradedUnits++;
                degradedReasons.add(result.degradedReason);
            }
            executions.addAll(result.executions);
            for (Map.Entry<IAEItemStack, Double> deficit : result.deficits.entrySet()) {
                deficits.merge(deficit.getKey(), deficit.getValue(), Double::sum);
            }
            for (Map.Entry<IAEItemStack, Double> ext : result.externalDemands.entrySet()) {
                IAEItemStack up = units.find(ext.getKey());
                demands.computeIfAbsent(up, k -> new HashMap<>())
                        .merge(ext.getKey(), ext.getValue(), Double::sum);
            }
            unitSolutions.add(new UnitSolution(unitKeys.get(unit), result.executions, result.deficits));
        }

        // 赤字噪声地板:实数传播/禁行泄漏的亚毫级尾差不是真实缺料(整数化会放大为 1)
        deficits.values().removeIf(v -> v < 1e-3);

        long wallMs = (System.nanoTime() - start) / 1_000_000;
        return new LpPlanOutcome(degradedUnits == 0, executions, deficits, unitSolutions, order.size(),
                lpUnits, iterations, degradedUnits, degradedReasons, wallMs);
    }

    /** 种子不足禁约束重解上限(M4,§4.4)的底数;实际上限 = min(2×单元键数, 64) 取大底数——
     * 每轮至少压掉一个卡住增益方向且上界单调不收,多层压缩链(8 键 4+ 层)需要
     * 超过底数 4 的轮数;上限封顶保证病态单元终止(超限截断转赤字). */
    private static final int MIN_BOOTSTRAP_RESOLVE = 4;
    private static final int MAX_BOOTSTRAP_RESOLVE = 64;
    /** 逐轮诊断输出(-Dae2e.lpDebug=true,开发期). */
    private static final boolean DEBUG = Boolean.getBoolean("ae2e.lpDebug");

    /** 单元求解产物(含降级/截断标记与原因). */
    private static final class UnitResult {
        final List<Execution> executions;
        final Map<IAEItemStack, Double> deficits;
        final Map<IAEItemStack, Double> externalDemands;
        final boolean degraded;
        @Nullable
        final String degradedReason;
        final int iterations;

        UnitResult(List<Execution> executions, Map<IAEItemStack, Double> deficits,
                Map<IAEItemStack, Double> externalDemands, boolean degraded, String degradedReason,
                int iterations) {
            this.executions = executions;
            this.deficits = deficits;
            this.externalDemands = externalDemands;
            this.degraded = degraded;
            this.degradedReason = degradedReason;
            this.iterations = iterations;
        }
    }

    /**
     * 单元求解闭环(M4):LP 两阶段 → 种子自举校验 → 卡住方向上界压至库存可行水位重解
     * (限 {@value #MAX_BOOTSTRAP_RESOLVE} 轮) → 仍不可行则按可行水位截断,
     * 交付缺口如实转赤字;LP 数值失败按 D4 整体库存直通.
     */
    private static UnitResult solveUnitWithBootstrap(ICraftingGrid cc, NetworkPatternIndex index,
            List<IAEItemStack> keys, Map<IAEItemStack, Long> stock, Map<IAEItemStack, Double> unitDemand) {
        Map<ICraftingPatternDetails, Double> upperCaps = new HashMap<>();
        int maxRounds = Math.max(MIN_BOOTSTRAP_RESOLVE, Math.min(2 * keys.size(), MAX_BOOTSTRAP_RESOLVE));
        int iterations = 0;
        for (int round = 0;; round++) {
            SccSolution sol = SccLpSolve.solve(cc, index, keys, stock, unitDemand, upperCaps,
                    Collections.emptyMap());
            iterations += sol.iterations;
            if (sol.status != LpResult.Status.OPTIMAL) {
                // D4 降级:单元整体库存直通(赤字 = 需求 − 库存,不再传播上游需求)
                Map<IAEItemStack, Double> deficits = new LinkedHashMap<>();
                for (Map.Entry<IAEItemStack, Double> d : unitDemand.entrySet()) {
                    double deficit = d.getValue() - stock.getOrDefault(d.getKey(), 0L);
                    if (deficit > 0 && !cc.canEmitFor(d.getKey())) {
                        deficits.merge(d.getKey(), deficit, Double::sum);
                    }
                }
                String reason = "LP非最优(" + sol.status + "/" + sol.reason + ") 键数=" + keys.size()
                        + " 代表键=" + (keys.isEmpty() ? "-" : keys.get(0));
                SpecialLog.info("[LP计划] 单元降级库存直通: {}", reason);
                return new UnitResult(Collections.emptyList(), deficits, Collections.emptyMap(), true,
                        reason, iterations);
            }
            SeedBootstrapCheck.Verdict verdict = SeedBootstrapCheck.check(cc, keys, stock, sol.executions);
            if (verdict.feasible) {
                return new UnitResult(sol.executions, sol.deficits, sol.externalDemands, false, null,
                        iterations);
            }
            if (DEBUG) {
                System.out.println("[LP-DEBUG] 种子校验失败 round=" + round + "/" + maxRounds + " 键数="
                        + keys.size() + " 代表键=" + (keys.isEmpty() ? "-" : keys.get(0))
                        + " 模拟轮数=" + verdict.roundsUsed + " 原因=" + verdict.failReason
                        + " 执行记录=" + sol.executions.size() + " 赤字=" + sol.deficits.size());
                for (int j = 0; j < sol.executions.size(); j++) {
                    Execution exec = sol.executions.get(j);
                    System.out.println("[LP-DEBUG]   exec" + j + " 输入=" + SccLpModelBuilder
                            .condensedInputs(exec.pattern, exec.variantInputs)
                            + " 输出=" + java.util.Arrays.toString(exec.pattern.getCondensedOutputs())
                            + " count=" + exec.count + " level=" + verdict.levels[j]
                            + " 增益源=" + isUnitGainSource(exec, new java.util.HashSet<>(keys))
                            + " cap=" + upperCaps.getOrDefault(exec.pattern, Double.MAX_VALUE));
                }
            }
            if (round >= maxRounds - 1) {
                // 重解预算耗尽:按可行水位截断——交付缺口(demand − 终态可用量)如实转赤字
                return truncateToFeasibleLevel(cc, keys, unitDemand, sol, verdict, iterations,
                        "种子自举重解超" + maxRounds + "轮 键数=" + keys.size()
                                + " 代表键=" + (keys.isEmpty() ? "-" : keys.get(0)));
            }
            // 禁约束(两级,§4.4 细化):
            // ① 有卡住的净增益源(Σ单元产出 > Σ单元投入)时只压增益源——守恒决定
            //    增益≤1 的环无法凭空造物,凭空交付必经增益源;传导性卡住的非增益
            //    样板(只是上游未产出,如蛛网环流)不压界,否则误杀合法交付路径;
            // ② 无卡住增益源 = "批量量子"死锁(质量守恒环但种子 < 单次点火批量,
            //    如 2A→B+C 而库存只有 1A)——压全部卡住样板到可行水位;
            // 每轮至少一个样板的上界被压到低于本次解值且单调不收 → 解空间严格
            // 收缩,有限步内必收敛(可行或截断),保证终止.
            Set<IAEItemStack> keySet = new java.util.HashSet<>(keys);
            Map<ICraftingPatternDetails, Double> levels = verdict.levelByPattern(sol.executions);
            boolean anyStuckGain = false;
            for (int j = 0; j < sol.executions.size(); j++) {
                if (isStuck(verdict.levels[j], sol.executions.get(j).count)
                        && isUnitGainSource(sol.executions.get(j), keySet)) {
                    anyStuckGain = true;
                    break;
                }
            }
            for (int j = 0; j < sol.executions.size(); j++) {
                if (isStuck(verdict.levels[j], sol.executions.get(j).count)) {
                    Execution exec = sol.executions.get(j);
                    if (anyStuckGain && !isUnitGainSource(exec, keySet)) {
                        continue;
                    }
                    double level = levels.getOrDefault(exec.pattern, 0.0);
                    upperCaps.merge(exec.pattern, level, Math::min);
                }
            }
        }
    }

    /** 卡住判定(相对容差):完成度差距 > max(1e-4 绝对, 1e-6×count 相对) 才算卡住——
     * 浮点尘埃(≈1e-8 相对)不视为卡住. */
    private static boolean isStuck(double level, double count) {
        return level < count - Math.max(1e-4, 1e-6 * count);
    }

    /** 变量是否单元净增益源(Σ单元键产出 > Σ单元键投入). */
    private static boolean isUnitGainSource(Execution exec, Set<IAEItemStack> keySet) {
        double in = 0;
        for (Map.Entry<IAEItemStack, Long> e : SccLpModelBuilder
                .condensedInputs(exec.pattern, exec.variantInputs).entrySet()) {
            if (keySet.contains(e.getKey())) {
                in += e.getValue();
            }
        }
        double out = 0;
        for (IAEItemStack o : exec.pattern.getCondensedOutputs()) {
            if (o != null && keySet.contains(RecursiveCraftingHelper.canon(o))) {
                out += o.getStackSize();
            }
        }
        return out > in;
    }

    /** 截断接受:执行数取可行水位,环外折算按水位重算,交付缺口转赤字. */
    private static UnitResult truncateToFeasibleLevel(ICraftingGrid cc, List<IAEItemStack> keys,
            Map<IAEItemStack, Double> unitDemand, SccSolution sol, SeedBootstrapCheck.Verdict verdict,
            int iterations, String reason) {
        List<Execution> executions = new ArrayList<>();
        Map<IAEItemStack, Double> externalDemands = new LinkedHashMap<>();
        Set<IAEItemStack> keySet = new java.util.HashSet<>(keys);
        for (int j = 0; j < sol.executions.size(); j++) {
            Execution exec = sol.executions.get(j);
            double level = verdict.levels[j];
            if (level <= 1e-6) {
                continue;
            }
            executions.add(new Execution(exec.pattern, exec.variantInputs, level));
            for (Map.Entry<IAEItemStack, Long> in : SccLpModelBuilder
                    .condensedInputs(exec.pattern, exec.variantInputs).entrySet()) {
                if (!keySet.contains(in.getKey())) {
                    externalDemands.merge(in.getKey(), in.getValue() * (double) level, Double::sum);
                }
            }
        }
        Map<IAEItemStack, Double> deficits = new LinkedHashMap<>();
        for (Map.Entry<IAEItemStack, Double> d : unitDemand.entrySet()) {
            if (cc.canEmitFor(d.getKey())) {
                continue;
            }
            double shortfall = d.getValue() - verdict.finalAvail.getOrDefault(d.getKey(), 0.0);
            if (shortfall > 1e-6) {
                deficits.put(d.getKey(), shortfall);
            }
        }
        SpecialLog.info("[LP计划] 单元截断: {},赤字 {} 种", reason, deficits.size());
        return new UnitResult(executions, deficits, externalDemands, true, reason, iterations);
    }

    // ===== 求解单元划分(SCC × 输出共享并查集) =====

    /**
     * 构建并查集:同样板的全部凝聚输出键合并为一个单元(跨 SCC 输出样板归并),
     * 不在键图中的键(纯原料)find 时按自身独立单元处理.
     */
    private static UnionFind buildUnits(ICraftingGrid cc, NetworkPatternIndex index) {
        UnionFind uf = new UnionFind();
        // 先按 SCC 合并:同分量键必须同属一个单元(环守恒不可拆)
        Map<Integer, IAEItemStack> sccRep = new HashMap<>();
        for (IAEItemStack key : index.keyGraphKeys()) {
            Integer id = index.sccIdOf(key);
            if (id == null) {
                continue;
            }
            IAEItemStack rep = sccRep.putIfAbsent(id, key);
            if (rep != null) {
                uf.union(rep, key);
            }
        }
        // 再按"同样板输出共享"合并:跨 SCC 输出的样板归并为一个单元
        Set<ICraftingPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IAEItemStack key : index.keyGraphKeys()) {
            List<ICraftingPatternDetails> producers = new ArrayList<>(cc.getCraftingFor(key, null, -1, null));
            producers.addAll(index.byproductMap().getOrDefault(key, Collections.emptyList()));
            for (ICraftingPatternDetails p : producers) {
                if (!seen.add(p)) {
                    continue;
                }
                IAEItemStack first = null;
                for (IAEItemStack out : p.getCondensedOutputs()) {
                    if (out == null) {
                        continue;
                    }
                    IAEItemStack canon = RecursiveCraftingHelper.canon(out);
                    if (first == null) {
                        first = canon;
                    } else {
                        uf.union(first, canon);
                    }
                }
            }
        }
        return uf;
    }

    /** 单元的全部样板(主输出 + 副产物,身份去重). */
    private static List<ICraftingPatternDetails> collectPatterns(ICraftingGrid cc, NetworkPatternIndex index,
            List<IAEItemStack> keys) {
        Set<ICraftingPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ICraftingPatternDetails> patterns = new ArrayList<>();
        for (IAEItemStack key : keys) {
            for (ICraftingPatternDetails p : cc.getCraftingFor(key, null, -1, null)) {
                if (seen.add(p)) {
                    patterns.add(p);
                }
            }
            for (ICraftingPatternDetails p : index.byproductMap().getOrDefault(key, Collections.emptyList())) {
                if (seen.add(p)) {
                    patterns.add(p);
                }
            }
        }
        return patterns;
    }

    /**
     * Kahn 拓扑（消费方先解）:单元的全部下游单元已解才可解（需求先齐备）.
     * 根单元无下游,最先入队;解完 U 后其上游 V 的未解下游数 −1,归 0 即入队.
     * 冷凝图按构造无环,故所有可达单元都会出队.
     */
    private static List<IAEItemStack> topologicalOrder(IAEItemStack rootUnit,
            Map<IAEItemStack, Set<IAEItemStack>> upstream, Map<IAEItemStack, Set<IAEItemStack>> downstream) {
        Map<IAEItemStack, Integer> remainingDownstream = new HashMap<>();
        for (IAEItemStack unit : upstream.keySet()) {
            remainingDownstream.put(unit, downstream.getOrDefault(unit, Collections.emptySet()).size());
        }
        List<IAEItemStack> order = new ArrayList<>();
        ArrayDeque<IAEItemStack> ready = new ArrayDeque<>();
        ready.add(rootUnit);
        while (!ready.isEmpty()) {
            IAEItemStack unit = ready.poll();
            order.add(unit);
            for (IAEItemStack up : upstream.getOrDefault(unit, Collections.emptySet())) {
                int rem = remainingDownstream.merge(up, -1, Integer::sum);
                if (rem == 0) {
                    ready.add(up);
                }
            }
        }
        return order;
    }

    /** 键并查集(canon 键;路径压缩;单元成员表惰性汇总). */
    private static final class UnionFind {
        private final Map<IAEItemStack, IAEItemStack> parent = new HashMap<>();
        @Nullable
        private Map<IAEItemStack, List<IAEItemStack>> members;

        IAEItemStack find(IAEItemStack key) {
            IAEItemStack p = this.parent.get(key);
            if (p == null) {
                return key; // 不在键图/未合并:自身即单元
            }
            if (p.equals(key)) {
                return key;
            }
            IAEItemStack root = find(p);
            this.parent.put(key, root); // 路径压缩
            return root;
        }

        void union(IAEItemStack a, IAEItemStack b) {
            IAEItemStack ra = find(a);
            IAEItemStack rb = find(b);
            if (ra.equals(rb)) {
                return;
            }
            this.parent.put(ra, rb);
            // 确保根自指(members 汇总依赖显式键集)
            this.parent.putIfAbsent(rb, rb);
            this.members = null;
        }

        /** 单元成员键表;未出现在任何合并中的键(纯原料)返回单键表. */
        List<IAEItemStack> members(IAEItemStack unit) {
            if (this.members == null) {
                this.members = new HashMap<>();
                for (IAEItemStack key : this.parent.keySet()) {
                    this.members.computeIfAbsent(find(key), k -> new ArrayList<>()).add(key);
                }
            }
            return this.members.getOrDefault(unit, Collections.singletonList(unit));
        }
    }
}
