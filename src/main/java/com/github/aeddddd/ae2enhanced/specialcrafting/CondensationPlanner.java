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

import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpHotStart;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpResult;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpModelBuilder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SeedBootstrapCheck;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.UnitTraceDump;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.SccSolution;

/**
 * 冷凝分层驱动器.
 * <p>把根请求逐键展开为冷凝 DAG 上的逐单元求解:键图上的 SCC 经"输出共享并查集"
 * 合并为求解单元(同样板的全部凝聚输出必属同一单元,故每个样板恰属一个单元,
 * 冷凝图按构造无环);边 U→V 表示"U 的样板消耗 V 的键",按冷凝拓扑序(消费方先解)
 * 逐单元求解,LP 环外折算(externalDemands)累加为上游单元需求,共享库存/原料键
 * 只被结算一次。单元求解统一走 {@link SccLpSolve} 字典序两阶段 LP(单键单元亦
 * 精确,阶段② ε·rank 体现样板优先级);无样板单元走快速路径(赤字 = 需求 − 库存)。
 * LP 非 OPTIMAL 时该单元降级为库存直通(赤字 = 需求 − 库存,不再传播上游需求)。
 * 请求物自身库存计入 stock(净增环种子自举需要真实库存)。
 * 产物为扁平计数解(执行记录 + 赤字 + 统计),计划树物化在 M5 完成。
 * 本类纯计算、线程安全;所有键均为 canon({@link RecursiveCraftingHelper#canon}).
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

    /**
     * 驱动器产物(扁平计数解 + 逐单元解).
     * <p><b>不可变契约(任务 4)</b>:四个集合字段一律包成只读视图——产物会被 C1 计划
     * 结果缓存跨请求共享,任何就地修改都会污染缓存(任务 4 的 R1 正是为此把
     * {@code FlowReconciler} 的活跃执行回补改为局部列表);包只读后误改会立刻抛异常,
     * 而不是静默改变后续请求的计划.</p>
     */
    public static final class LpPlanOutcome {
        /** 全部单元 OPTIMAL = true;任一单元降级(库存直通/截断) = false. */
        public final boolean allOptimal;
        /** 执行记录(跨单元聚合;同一样板在不同单元不会出现——单元唯一归属;只读). */
        public final List<Execution> executions;
        /** 赤字(canon 键 → 缺料量;LP 意义精确最优 + 降级直通;只读). */
        public final Map<IAEItemStack, Double> deficits;
        /** 逐单元解(求解序,消费方在前;对账重演按逆序 = 上游先产;只读). */
        public final List<UnitSolution> unitSolutions;
        /** 求解单元数(诊断). */
        public final int units;
        /** LP 单元数(含两阶段;诊断). */
        public final int lpUnits;
        /** 单纯形总迭代数(诊断埋点;缓存命中时为首算值——产物即首算产物). */
        public final int iterations;
        /** 降级单元数(D4 库存直通/截断;验收口径要求恒 0). */
        public final int degradedUnits;
        /** 降级原因清单(诊断;每条 = 单元代表键 + 原因;只读). */
        public final List<String> degradedReasons;
        /** 墙钟毫秒(诊断;缓存命中时为首算值). */
        public final long wallMs;

        LpPlanOutcome(boolean allOptimal, List<Execution> executions, Map<IAEItemStack, Double> deficits,
                List<UnitSolution> unitSolutions, int units, int lpUnits, int iterations, int degradedUnits,
                List<String> degradedReasons, long wallMs) {
            this.allOptimal = allOptimal;
            this.executions = Collections.unmodifiableList(executions);
            this.deficits = Collections.unmodifiableMap(deficits);
            this.unitSolutions = Collections.unmodifiableList(unitSolutions);
            this.units = units;
            this.lpUnits = lpUnits;
            this.iterations = iterations;
            this.degradedUnits = degradedUnits;
            this.degradedReasons = Collections.unmodifiableList(degradedReasons);
            this.wallMs = wallMs;
        }
    }

    /**
     * 库存来源(任务 4):把"库存快照"抽象为可稀疏查询的口径,缓存开启时求解器只按
     * 覆盖键逐键查询(避免每单多次全量遍历),关闭时退回全量快照(对拍基线).
     */
    public interface StockSource {
        /**
         * 全量库存快照(canon 键 → 数量;仅缓存关闭或诊断落盘时调用,代价 = 一次全量遍历).
         */
        Map<IAEItemStack, Long> fullStock();

        /**
         * 单键存量(canon 键).契约:{@code amountOf(k) == fullStock().getOrDefault(k, 0L)}
         * 逐位一致(无存量必须返回 0,不得用 -1 等哨兵).
         */
        long amountOf(IAEItemStack canonKey);
    }

    /** Map 支撑的库存来源(旧 API 适配层:测试/离线对拍直接把库存表当来源). */
    private static final class MapStockSource implements StockSource {
        private final Map<IAEItemStack, Long> stock;

        MapStockSource(Map<IAEItemStack, Long> stock) {
            this.stock = stock;
        }

        @Override
        public Map<IAEItemStack, Long> fullStock() {
            return this.stock;
        }

        @Override
        public long amountOf(IAEItemStack canonKey) {
            Long value = this.stock.get(canonKey);
            return value == null ? 0L : value;
        }
    }

    /**
     * 稀疏库存表:覆盖键在构建期一次查询(闭包覆盖集 = 求解器会读取的键集),
     * 其余键<b>读穿兜底</b>——若覆盖集分析有漏,读到的仍是与全量快照逐位一致的真值
     * (等价性由构造保证,而不是仅靠注释承诺);读穿次数计入
     * {@code plan.cache.sparse.fallback} 指标,正常为 0(测试即以此验证覆盖集完备).
     * <p>不可迭代({@link #entrySet()} 抛异常):需要全量库存的诊断路径显式改用
     * {@link StockSource#fullStock()},避免无声拿到半张表.</p>
     */
    private static final class SparseStock extends java.util.AbstractMap<IAEItemStack, Long> {
        private final StockSource source;
        private final Map<IAEItemStack, Long> memo;
        private final LpPlanCache cache;

        SparseStock(StockSource source, List<IAEItemStack> coverageKeys, LpPlanCache cache) {
            this.source = source;
            this.cache = cache;
            this.memo = new HashMap<>(Math.max(16, coverageKeys.size() * 2));
            for (IAEItemStack key : coverageKeys) {
                this.memo.put(key, source.amountOf(key));
            }
        }

        @Override
        public Long get(Object key) {
            Long value = this.memo.get(key);
            if (value != null) {
                return value;
            }
            // 覆盖集外读穿兜底(带键类型防护:非 IAEItemStack 直接判缺省)
            if (!(key instanceof IAEItemStack)) {
                return 0L;
            }
            IAEItemStack canonKey = RecursiveCraftingHelper.canon((IAEItemStack) key);
            value = this.memo.get(canonKey);
            if (value == null) {
                value = this.source.amountOf(canonKey);
                this.memo.put(canonKey, value);
                if (this.cache != null) {
                    this.cache.noteCoverageFallback();
                }
            }
            return value;
        }

        @Override
        public boolean containsKey(Object key) {
            // 稀疏表语义:0 = 无存量(与全量快照的 getOrDefault == 0 一致)
            Long value = get(key);
            return value != null && value > 0;
        }

        @Override
        public Set<Entry<IAEItemStack, Long>> entrySet() {
            throw new UnsupportedOperationException("稀疏库存表不可迭代:诊断全量请用 StockSource.fullStock()");
        }
    }

    /**
     * 求解整单:从根请求出发,冷凝 DAG 拓扑逐单元 LP.
     *
     * @param stock 网络库存快照(canon 键 → 数量;调用方一次性构建)
     */
    public static LpPlanOutcome solve(ICraftingGrid cc, NetworkPatternIndex index,
            IAEItemStack what, long target, Map<IAEItemStack, Long> stock) {
        return solve(cc, index, what, target, new MapStockSource(stock),
                PruneSettings.fromSystemProperties(), LpHotStart.Chain.fromSystemProperties(),
                LpPlanCache.Settings.fromSystemProperties());
    }

    /**
     * 求解整单(剪枝开关由调用方一次性解析后传入).
     * <p>系统属性只在入口解析这一次:循环内反复读会让同一次求解混用多种剪枝策略
     * (轨迹不可复现),确定性由 {@link PruneSettings} 的传参口径守住。</p>
     */
    public static LpPlanOutcome solve(ICraftingGrid cc, NetworkPatternIndex index,
            IAEItemStack what, long target, Map<IAEItemStack, Long> stock, PruneSettings settings) {
        // P-A 热启动链: 按整单持有一条(逐单元内跨自举轮复用), 档位在此一次性解析
        return solve(cc, index, what, target, new MapStockSource(stock), settings,
                LpHotStart.Chain.fromSystemProperties(), LpPlanCache.Settings.fromSystemProperties());
    }

    /**
     * 求解整单(热启动链由调用方给定; 单元自举轮之间按 (rows, cols) 槽位复用基)。
     *
     * @param chain P-A 热启动链; 传 {@link LpHotStart.Chain#disabled()} 即冷启动基线
     */
    public static LpPlanOutcome solve(ICraftingGrid cc, NetworkPatternIndex index,
            IAEItemStack what, long target, Map<IAEItemStack, Long> stock, PruneSettings settings,
            LpHotStart.Chain chain) {
        return solve(cc, index, what, target, new MapStockSource(stock), settings, chain,
                LpPlanCache.Settings.fromSystemProperties());
    }

    /**
     * 求解整单(任务 4 缓存版主入口):闭包缓存(C3)+ 稀疏库存(C2)+ 计划结果缓存(C1).
     *
     * <p><b>开关一律由调用方在入口一次性解析后传入</b>(剪枝档/热启动链/缓存档),
     * 本方法内只额外解析求解器后端(与 {@link SccLpSolve} 同一口径,并作为 C1 键的一部分).</p>
     *
     * @param stockSource 库存来源(稀疏口径:{@code amountOf} 须与全量快照逐位一致)
     * @param cacheSettings 缓存档位;{@link LpPlanCache.Mode#OFF} 时退回旧全量路径(对拍基线)
     */
    public static LpPlanOutcome solve(ICraftingGrid cc, NetworkPatternIndex index,
            IAEItemStack what, long target, StockSource stockSource, PruneSettings settings,
            LpHotStart.Chain chain, LpPlanCache.Settings cacheSettings) {
        long start = System.nanoTime();
        IAEItemStack rootKey = RecursiveCraftingHelper.canon(what);
        // 求解器后端在入口一次性解析并向下传参(SccLpSolve 内不再读系统属性 ⇒ 同一次
        // 求解不会混用多后端);它同时进 C1 键——同 JVM 切属性不得跨模式命中
        final String solverMode = SccLpSolve.solverModeFromSystemProperties();

        // 1) 求解闭包:单元划分 + 可达单元 + 单元间边 + 冷凝拓扑序
        // (纯 (index, rootKey) 函数 ⇒ 可跨求解复用;C3)
        LpPlanCache planCache = cacheSettings.closureEnabled() ? index.planCache() : null;
        LpPlanCache.Closure closure = planCache != null
                ? planCache.closure(rootKey, () -> buildClosure(cc, index, rootKey), cacheSettings)
                : buildClosure(cc, index, rootKey);
        List<IAEItemStack> order = closure.order;
        Map<IAEItemStack, List<IAEItemStack>> unitKeys = closure.unitKeys;
        Map<IAEItemStack, List<ICraftingPatternDetails>> unitPatterns = closure.unitPatterns;
        IAEItemStack rootUnit = closure.rootUnit;
        UnitPartition units = index.unitPartition();

        // 2) 库存:缓存开启 = 稀疏(覆盖键一次查询;其余读穿兜底),关闭 = 旧全量路径
        Map<IAEItemStack, Long> stock = planCache != null
                ? new SparseStock(stockSource, closure.allKeys, planCache)
                : stockSource.fullStock();

        // 3) C1 计划结果缓存:指纹(覆盖键的 库存/发射位 混淆值)先否决,命中候选再
        // 逐元素精确比较——两者都相同才算命中,返回同一次计算的产物(零误返).
        // 发射位(setEmitable)不进库存但改 b 与快速路径,故必须进指纹(方案地基事实 3)
        long[] coverageValues = null;
        long[] coverageEmits = null;
        long fingerprint = 0;
        if (cacheSettings.planEnabled()) {
            int coverage = closure.allKeys.size();
            coverageValues = new long[coverage];
            coverageEmits = new long[coverage];
            long fingerprintStart = System.nanoTime();
            for (int i = 0; i < coverage; i++) {
                IAEItemStack key = closure.allKeys.get(i);
                coverageValues[i] = stock.getOrDefault(key, 0L);
                coverageEmits[i] = index.canEmit(key) ? 1L : 0L;
            }
            fingerprint = LpPlanCache.fingerprint(closure, coverageValues, coverageEmits);
            planCache.noteFingerprintNanos(System.nanoTime() - fingerprintStart);
            LpPlanOutcome cached = planCache.planLookup(closure, target, settings, solverMode, fingerprint,
                    coverageValues, coverageEmits, cacheSettings);
            if (cached != null) {
                // 命中 = 同一次计算的产物(不可变,见 LpPlanOutcome 的只读契约);
                // 求解期诊断(计划快照/单元画像/剪枝轨迹)不在命中路径重跑
                return cached;
            }
        }

        // 4) 逐单元求解:需求累加 → LP/快速路径 → 环外折算向后传播
        Map<IAEItemStack, Map<IAEItemStack, Double>> demands = new HashMap<>();
        // 根需求口径 = 全额生产(CraftingJob.ignore 同语义):请求物自身库存不抵扣
        // 交付,需求端加回库存量;种子点火仍可用根库存,期末由 FlowReconciler 偿还
        demands.computeIfAbsent(rootUnit, k -> new HashMap<>())
                .put(rootKey, (double) target + (double) stock.getOrDefault(rootKey, 0L));
        List<Execution> executions = new ArrayList<>();
        Map<IAEItemStack, Double> deficits = new LinkedHashMap<>();
        List<UnitSolution> unitSolutions = new ArrayList<>();
        List<String> degradedReasons = new ArrayList<>();
        int lpUnits = 0;
        int iterations = 0;
        int degradedUnits = 0;
        // P0 埋点与剪枝审计计数(仅 unitProfile/pruneTrace 时收集,默认零开销)
        boolean profiling = settings.unitProfile;
        List<UnitStat> unitStats = profiling ? new ArrayList<>() : null;
        int gatedUnits = 0;
        int closedFormUnits = 0;
        int fastPathUnits = 0;
        for (IAEItemStack unit : order) {
            Map<IAEItemStack, Double> unitDemand = demands.getOrDefault(unit, Collections.emptyMap());
            List<ICraftingPatternDetails> patterns = unitPatterns.getOrDefault(unit, Collections.emptyList());
            if (patterns.isEmpty()) {
                // 快速路径:无样板单元(原料/发射台)——赤字 = 需求 − 库存
                if (unit.equals(rootUnit)) {
                    dumpFastPathRoot(index, unitKeys.get(unit), stock, unitDemand);
                }
                for (Map.Entry<IAEItemStack, Double> d : StableOrder.sortedEntries(unitDemand)) {
                    if (d.getValue() <= 0 || index.canEmit(d.getKey())) {
                        continue;
                    }
                    double deficit = d.getValue() - stock.getOrDefault(d.getKey(), 0L);
                    if (deficit > 0) {
                        deficits.merge(d.getKey(), deficit, Double::sum);
                    }
                }
                fastPathUnits++;
                if (unitStats != null) {
                    unitStats.add(new UnitStat(unitKeys.get(unit), patterns, 0, 0L, "fastpath"));
                }
                continue;
            }
            // P1 需求门控:全键需求都被库存/发射台覆盖(b ≤ 0)时 x≡0 是 LP 最优解——
            // 执行记录全被 count≤1e-6 过滤、环外折算/种子需求按 count 加权全空、
            // 赤字恒 0,故"跳过"与"解出全零"在交付面上逐位等价(唯一允许差异:
            // 降级单元可能减少 = 行为改善)。
            // 判定顺序约束:必须与 LP 模型看到的需求同口径(committed 跨分量折算在
            // 模型 b 上生效)——当前调用方恒传空表,故直接用 unitDemand/stock 判定;
            // 若将来有调用方传入非空 committed,此处必须同步扣除后才可门控。
            if (settings.demandGate() && demandsSatisfied(index, unitDemand, stock)) {
                gatedUnits++;
                if (settings.trace) {
                    System.out.println("[PRUNE] 门控跳过单元 键数=" + unitKeys.get(unit).size()
                            + " 样板数=" + patterns.size() + " 代表键="
                            + (unitKeys.get(unit).isEmpty() ? "-" : unitKeys.get(unit).get(0))
                            + " 需求=" + unitDemand);
                }
                if (unitStats != null) {
                    unitStats.add(new UnitStat(unitKeys.get(unit), patterns, 0, 0L, "gated"));
                }
                continue;
            }
            lpUnits++;
            long unitStartNanos = System.nanoTime();
            UnitResult result = solveUnitWithBootstrap(cc, index, unitKeys.get(unit), stock, unitDemand,
                    settings, chain, solverMode);
            long unitNanos = System.nanoTime() - unitStartNanos;
            if (result.closedForm) {
                closedFormUnits++;
            }
            if (unit.equals(rootUnit)) {
                dumpUnitDetail(index, unitKeys.get(unit), patterns, stock, unitDemand, result);
            }
            if (unitStats != null) {
                unitStats.add(new UnitStat(unitKeys.get(unit), patterns, result.iterations, unitNanos,
                        result.closedForm ? "closed" : "lp"));
            }
            iterations += result.iterations;
            if (result.degraded) {
                degradedUnits++;
                degradedReasons.add(result.degradedReason);
            }
            // 扁平列表只收活跃执行(公共契约:库存覆盖时为空);零计数记录保留在
            // UnitSolution 中,供对账层整数化后回补
            for (Execution exec : result.executions) {
                if (exec.count > 1e-6) {
                    executions.add(exec);
                }
            }
            // 降级/截断单元的缺口不进缺料表:求解器没证明"产不出",
            // 如实列缺料会把"规划未完成"谎报成"缺料"(UNKNOWN 语义,与
            // NeoECO 的 budget-exhausted → UNKNOWN_BUDGET 一致).缺口由
            // degradedUnits/degradedReasons 承担,计划按不可提交处理.
            if (!result.degraded) {
                for (Map.Entry<IAEItemStack, Double> deficit : result.deficits.entrySet()) {
                    deficits.merge(deficit.getKey(), deficit.getValue(), Double::sum);
                }
            }
            for (Map.Entry<IAEItemStack, Double> ext : result.externalDemands.entrySet()) {
                IAEItemStack up = units.find(ext.getKey());
                demands.computeIfAbsent(up, k -> new HashMap<>())
                        .merge(ext.getKey(), ext.getValue(), Double::sum);
            }
            // 自返还种子需求:与环外折算同路传播(一次性种子量,首点点火实物要求)
            for (Map.Entry<IAEItemStack, Double> seed : result.seedDemands.entrySet()) {
                IAEItemStack up = units.find(seed.getKey());
                demands.computeIfAbsent(up, k -> new HashMap<>())
                        .merge(seed.getKey(), seed.getValue(), Double::sum);
            }
            unitSolutions.add(new UnitSolution(unitKeys.get(unit), result.executions, result.deficits));
        }

        // 赤字噪声地板:实数传播/禁行泄漏的亚毫级尾差不是真实缺料(整数化会放大为 1)
        deficits.values().removeIf(v -> v < 1e-3);

        // 单元降级/截断时一次性落盘完整计划快照(全部网络样板含 NBT/替代/返还 +
        // 全网络库存 + 根请求),供离线复现该订单;与 .lpm 转储同一门控:
        // /ae2e debug specialcrafting(诊断写盘不进默认路径).
        // 库存落盘要全量(稀疏表不可迭代),故诊断分支显式取全量快照
        if (degradedUnits > 0 && SpecialLog.isEnabled()) {
            com.github.aeddddd.ae2enhanced.diag.plansnapshot.PlanSnapshot.dump(cc, index, what,
                    planCache != null ? stockSource.fullStock() : stock);
        }

        long wallMs = (System.nanoTime() - start) / 1_000_000;
        if (profiling) {
            printUnitProfile(unitStats, settings, order.size(), fastPathUnits, gatedUnits,
                    closedFormUnits, lpUnits, iterations, degradedUnits, wallMs);
        }
        if (settings.trace) {
            System.out.println("[PRUNE] 审计: 单元=" + order.size() + " 快速路径=" + fastPathUnits
                    + " 门控跳过=" + gatedUnits + " 闭式=" + closedFormUnits
                    + " 走LP=" + (lpUnits - closedFormUnits) + " LP单元=" + lpUnits
                    + " 迭代=" + iterations + " 降级=" + degradedUnits);
        }
        LpPlanOutcome outcome = new LpPlanOutcome(degradedUnits == 0, executions, deficits, unitSolutions,
                order.size(), lpUnits, iterations, degradedUnits, degradedReasons, wallMs);
        // C1 登记:指纹与精确值数组同源(同一次读取),故后续同输入必然逐位一致
        if (cacheSettings.planEnabled()) {
            planCache.planPut(closure, target, settings, solverMode, fingerprint, coverageValues,
                    coverageEmits, outcome, cacheSettings);
        }
        return outcome;
    }

    /**
     * 构建根键的求解闭包(C3 缓存值):SCC 键图 + "同样板输出共享"并查集给出的求解单元
     * 划分、从根单元可达的单元与单元间边(U→V:U 的样板消耗 V 的键)、冷凝拓扑序,
     * 以及覆盖键表(闭包键 ∪ 可达样板全部输入键 ∪ 根键).
     * <p>只依赖索引(单元成员键、样板凝聚输入与替代候选键),与库存/需求无关 ⇒ 纯
     * (index, rootKey) 函数,可按根键复用.</p>
     */
    static LpPlanCache.Closure buildClosure(ICraftingGrid cc, NetworkPatternIndex index,
            IAEItemStack rootKey) {
        // 求解单元划分:SCC 键图 + "同样板输出共享"并查集合并
        // (P3:划分只依赖索引,挂索引 memo 跨求解复用,随索引重建失效)
        UnitPartition units = index.unitPartition();
        // 从根单元发现可达单元与单元间边(U→V:U 的样板消耗 V 的键)
        IAEItemStack rootUnit = units.find(rootKey);
        Map<IAEItemStack, List<IAEItemStack>> unitKeys = new LinkedHashMap<>();
        Map<IAEItemStack, Set<IAEItemStack>> upstream = new LinkedHashMap<>(); // U → 上游单元集
        Map<IAEItemStack, Set<IAEItemStack>> downstream = new LinkedHashMap<>(); // V → 下游单元集
        Map<IAEItemStack, List<ICraftingPatternDetails>> unitPatterns = new HashMap<>();
        // 覆盖集:闭包键 ∪ 可达样板全部输入键 ∪ {根键}(canon 口径;makeClosure 统一排序)
        Set<IAEItemStack> coverage = new java.util.LinkedHashSet<>();
        coverage.add(rootKey);
        ArrayDeque<IAEItemStack> queue = new ArrayDeque<>();
        queue.add(rootUnit);
        Set<IAEItemStack> seenUnits = new java.util.HashSet<>();
        seenUnits.add(rootUnit);
        while (!queue.isEmpty()) {
            IAEItemStack unit = queue.poll();
            List<IAEItemStack> keys = units.members(unit);
            unitKeys.put(unit, keys);
            coverage.addAll(keys);
            List<ICraftingPatternDetails> patterns = collectPatterns(cc, index, keys);
            unitPatterns.put(unit, patterns);
            Set<IAEItemStack> ups = new java.util.LinkedHashSet<>();
            for (ICraftingPatternDetails p : patterns) {
                // unitInputKeys 返回的已是 canon 键(无数量口径),此处不得再按 stackSize 过滤
                for (IAEItemStack in : unitInputKeys(index, p)) {
                    coverage.add(in);
                    IAEItemStack up = units.find(in);
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

        // 冷凝拓扑序(Kahn:消费方先解;按构造无环)
        List<IAEItemStack> order = topologicalOrder(rootUnit, upstream, downstream);
        return LpPlanCache.makeClosure(rootKey, rootUnit, order, unitKeys, unitPatterns, coverage);
    }

    /**
     * 单元的上游输入键(P4 扩边后的口径):凝聚输入 ∪ 替代候选键。
     * <p>变体列的候选键是"LP 可见但凝聚输入不可见"的消耗口:若其单元不在
     * {@link #topologicalOrder} 内,LP 判可合成(环外折算)而该单元永不求解,
     * 需求被静默丢弃——重演缺料不记账,计划既不提交也无缺料线索(线上正确性缺口)。
     * 候选键按 canon 升序追加在凝聚输入之后(无替代槽样板与改造前逐位同序)。</p>
     */
    private static List<IAEItemStack> unitInputKeys(NetworkPatternIndex index,
            ICraftingPatternDetails pattern) {
        IAEItemStack[] condensed = pattern.getCondensedInputs();
        List<IAEItemStack> candidates = index.variantCandidateKeys(pattern);
        List<IAEItemStack> out = new ArrayList<>(condensed.length + candidates.size());
        for (IAEItemStack in : condensed) {
            if (in != null && in.getStackSize() > 0) {
                out.add(RecursiveCraftingHelper.canon(in));
            }
        }
        // 候选键已是 canon 口径(数量已清零),不得再按 stackSize 过滤
        out.addAll(candidates);
        return out;
    }

    /**
     * 需求门控(P1)判定:全部需求键的缺口都在噪声级以下(含被库存/发射台覆盖)时,
     * LP 的最优解恒为全零 ⇒ 可跳过本单元。
     * <p>闸门口径与 {@code SccLpModelBuilder} 的右端一致:发射台键视作无限供给,
     * 其余用网络库存;容差 1e-7×(1+|S|+|d|) 只吸收实数传播/禁行泄漏的噪声,
     * 有效需求(相对量级大于 1e-7)不会被误剪。</p>
     */
    private static boolean demandsSatisfied(NetworkPatternIndex index,
            Map<IAEItemStack, Double> unitDemand, Map<IAEItemStack, Long> stock) {
        for (Map.Entry<IAEItemStack, Double> d : unitDemand.entrySet()) {
            double need = d.getValue();
            if (need <= 0) {
                continue;
            }
            if (index.canEmit(d.getKey())) {
                continue;
            }
            double have = stock.getOrDefault(d.getKey(), 0L);
            if (need - have > 1e-7 * (1 + Math.abs(have) + Math.abs(need))) {
                return false;
            }
        }
        return true;
    }


    /** 种子自举重解轮数下限;轮数上限 = max(下限, min(2×单元键数, 上限)):多层压界
     * 级联需要的轮数可超过 4,另设迭代/墙钟预算兜底终止. */
    private static final int MIN_BOOTSTRAP_RESOLVE = 4;
    private static final int MAX_BOOTSTRAP_RESOLVE = 256;
    /**
     * 单单元自举重解的累计 LP 迭代预算(P-D 确定性终止判据,取代旧墙钟判据):
     * 迭代数与负载无关,因此同一逻辑输入的截断时机跨 JVM 一致.
     */
    private static final int BOOTSTRAP_ITER_BUDGET =
            Integer.getInteger("ae2e.bootstrapIterBudget", 200_000);
    /**
     * 单单元自举重解的墙钟紧急熔断(ns,默认 60 秒):只在 CPU 极端抢占等异常情况下兜底,
     * 触发即按截断处理并把"熔断"写进截断原因(确定性纪律的例外,可被测试检出).
     */
    private static final long BOOTSTRAP_FUSE_NS =
            Long.getLong("ae2e.bootstrapFuseNs", 60_000_000_000L);

    /** 三振禁路由阈值:同一样板被压界 {@value} 次后仍被判定卡住时,继续按水位压界
     * 只会空转,直接禁该路由逼 LP 换路;误禁的交付损失由历代最优水位截断兜底
     * (禁前轮的交付已留存). */
    private static final int CAP_STRIKE_LIMIT = 3;
    /** 逐轮诊断输出(-Dae2e.lpDebug=true,开发期). */
    private static final boolean DEBUG = Boolean.getBoolean("ae2e.lpDebug");

    /** 单元求解产物(含降级/截断标记与原因). */
    private static final class UnitResult {
        final List<Execution> executions;
        final Map<IAEItemStack, Double> deficits;
        final Map<IAEItemStack, Double> externalDemands;
        final Map<IAEItemStack, Double> seedDemands;
        final boolean degraded;
        @Nullable
        final String degradedReason;
        final int iterations;
        /** 本次单元解由闭式(P2)给出(诊断/审计用). */
        final boolean closedForm;

        UnitResult(List<Execution> executions, Map<IAEItemStack, Double> deficits,
                Map<IAEItemStack, Double> externalDemands, Map<IAEItemStack, Double> seedDemands,
                boolean degraded, String degradedReason, int iterations, boolean closedForm) {
            this.executions = executions;
            this.deficits = deficits;
            this.externalDemands = externalDemands;
            this.seedDemands = seedDemands;
            this.degraded = degraded;
            this.degradedReason = degradedReason;
            this.iterations = iterations;
            this.closedForm = closedForm;
        }
    }

    /**
     * 单元求解闭环(M4):LP 两阶段 → 种子自举校验 → 卡住方向上界压至库存可行水位重解
     * (限 {@value #MAX_BOOTSTRAP_RESOLVE} 轮) → 仍不可行则按可行水位截断,
     * 交付缺口如实转赤字;LP 数值失败按 D4 整体库存直通.
     */
    private static UnitResult solveUnitWithBootstrap(ICraftingGrid cc, NetworkPatternIndex index,
            List<IAEItemStack> keys, Map<IAEItemStack, Long> stock, Map<IAEItemStack, Double> unitDemand,
            PruneSettings settings, LpHotStart.Chain chain, String solverMode) {
        Map<ICraftingPatternDetails, Double> upperCaps = new HashMap<>();
        // 逐样板压界次数(身份键;三振禁路由用)
        Map<ICraftingPatternDetails, Integer> pressCounts = new IdentityHashMap<>();
        int maxRounds = Math.max(MIN_BOOTSTRAP_RESOLVE, Math.min(2 * keys.size(), MAX_BOOTSTRAP_RESOLVE));
        long unitStartNanos = System.nanoTime();
        int iterations = 0;
        // 闭式(P2)命中标记:仅首轮可能命中(压界后 upperCaps 非空 ⇒ 闭式不适用)
        boolean closedForm = false;
        // 截断诊断轨迹(SpecialLog 门控):逐轮卡住集/可行水位/禁约束变化,截断时落盘
        StringBuilder trace = SpecialLog.isEnabled() ? new StringBuilder() : null;
        Map<ICraftingPatternDetails, Integer> traceIds =
                trace != null ? new IdentityHashMap<>() : null;
        StringBuilder tracePatterns = trace != null ? new StringBuilder() : null;
        // 历代最优可行水位:各轮禁约束改路由后交付量非单调,保留赤字和最小的一届
        // 用于截断,不劣于只用末轮
        double bestDeficitSum = Double.MAX_VALUE;
        SccSolution bestSol = null;
        SeedBootstrapCheck.Verdict bestVerdict = null;
        for (int round = 0;; round++) {
            SccSolution sol = SccLpSolve.solve(cc, index, keys, stock, unitDemand, upperCaps,
                    Collections.emptyMap(), settings, chain, solverMode);
            iterations += sol.iterations;
            if (round == 0 && sol.closedForm) {
                closedForm = true;
            }
            if (sol.status != LpResult.Status.OPTIMAL) {
                // D4 降级:单元整体库存直通(赤字 = 需求 − 库存,不再传播上游需求)
                Map<IAEItemStack, Double> deficits = new LinkedHashMap<>();
                for (Map.Entry<IAEItemStack, Double> d : StableOrder.sortedEntries(unitDemand)) {
                    double deficit = d.getValue() - stock.getOrDefault(d.getKey(), 0L);
                    if (deficit > 0 && !index.canEmit(d.getKey())) {
                        deficits.merge(d.getKey(), deficit, Double::sum);
                    }
                }
                String reason = "LP非最优(" + sol.status + "/" + sol.reason + ") 键数=" + keys.size()
                        + " 代表键=" + (keys.isEmpty() ? "-" : keys.get(0));
                SpecialLog.info("[LP计划] 单元降级库存直通: {}", reason);
                return new UnitResult(Collections.emptyList(), deficits, Collections.emptyMap(),
                        Collections.emptyMap(), true, reason, iterations, false);
            }
            Set<IAEItemStack> rowKeys = new java.util.HashSet<>(keys);
            Map<IAEItemStack, Double> seedDemands = SccLpSolve.seedDemandsOf(sol.executions, rowKeys);
            SeedBootstrapCheck.Verdict verdict = SeedBootstrapCheck.check(cc, index, keys, stock,
                    sol.executions, seedDemands);
            if (verdict.feasible) {
                return new UnitResult(sol.executions, sol.deficits, sol.externalDemands, seedDemands,
                        false, null, iterations, closedForm);
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
            Set<IAEItemStack> keySet = new java.util.HashSet<>(keys);
            // 历代最优水位评选(与截断同口径的赤字和)
            double deficitSum = deficitSumOf(index, unitDemand, verdict);
            if (deficitSum < bestDeficitSum) {
                bestDeficitSum = deficitSum;
                bestSol = sol;
                bestVerdict = verdict;
            }
            if (trace != null) {
                appendRoundTrace(trace, traceIds, tracePatterns, round, sol, verdict, keySet,
                        upperCaps, pressCounts, deficitSum);
            }
            // P-D:终止判据 = 轮数上限 + 累计迭代预算(确定性);墙钟只剩紧急熔断
            boolean fuse = BOOTSTRAP_FUSE_NS > 0
                    && System.nanoTime() - unitStartNanos > BOOTSTRAP_FUSE_NS;
            boolean budgetUp = iterations >= BOOTSTRAP_ITER_BUDGET;
            if (round >= maxRounds - 1 || budgetUp || fuse) {
                // 重解预算(轮数/迭代/熔断)耗尽:按历代最优可行水位截断,交付缺口
                // (demand − 终态可用量)如实转赤字
                String reason;
                if (round < maxRounds - 1) {
                    reason = fuse
                            ? "种子自举重解触发墙钟紧急熔断(>" + BOOTSTRAP_FUSE_NS / 1_000_000 + "ms) round="
                                    + round + " 键数=" + keys.size()
                                    + " 代表键=" + (keys.isEmpty() ? "-" : keys.get(0))
                            : "种子自举重解超迭代预算(" + BOOTSTRAP_ITER_BUDGET + ") round=" + round
                                    + " 键数=" + keys.size()
                                    + " 代表键=" + (keys.isEmpty() ? "-" : keys.get(0));
                } else {
                    reason = "种子自举重解超" + maxRounds + "轮 键数=" + keys.size()
                            + " 代表键=" + (keys.isEmpty() ? "-" : keys.get(0));
                }
                UnitResult r = truncateToFeasibleLevel(cc, index, keys, unitDemand, bestSol,
                        bestVerdict, iterations, reason);
                if (trace != null) {
                    dumpTrace(trace, tracePatterns, reason, keys, stock, unitDemand, index, r,
                            bestVerdict == null ? null : bestVerdict.finalAvail);
                }
                return r;
            }
            // 禁约束(两级,§4.4):
            // ① 有卡住的净增益源(Σ单元产出 > Σ单元投入)时只压增益源——守恒决定
            //    增益≤1 的环无法凭空造物,凭空交付必经增益源;传导性卡住的非增益
            //    样板(只是上游未产出,如蛛网环流)不压界,否则误杀合法交付路径;
            // ② 无卡住增益源 = 批量量子死锁(质量守恒环但种子 < 单次点火批量,
            //    如 2A→B+C 而库存只有 1A)——压全部卡住样板到可行水位;
            // ③ 三振禁路由:同一样板压界 CAP_STRIKE_LIMIT 次后仍卡住 → 上界压 0
            //    逼 LP 换路;
            // 每轮至少一个样板的上界被压到低于本次解值且单调不收 → 解空间严格
            // 收缩,有限步内必收敛(可行或截断),保证终止.
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

    /** 截断口径赤字和(与 truncateToFeasibleLevel 同口径;历代最优水位评选用). */
    private static double deficitSumOf(NetworkPatternIndex index,
            Map<IAEItemStack, Double> unitDemand, SeedBootstrapCheck.Verdict verdict) {
        double sum = 0;
        for (Map.Entry<IAEItemStack, Double> d : StableOrder.sortedEntries(unitDemand)) {
            if (index.canEmit(d.getKey())) {
                continue;
            }
            double shortfall = d.getValue() - verdict.finalAvail.getOrDefault(d.getKey(), 0.0);
            if (shortfall > 1e-6) {
                sum += shortfall;
            }
        }
        return sum;
    }

    /** 追加一轮轨迹(仅列卡住执行;样板表按首见编号登记;附阻塞键分析——单元键
     * 终态可用量连一次都不足者列出,无则标"疑调度序"以区分真稀缺与调度假阴性). */
    private static void appendRoundTrace(StringBuilder trace,
            Map<ICraftingPatternDetails, Integer> ids, StringBuilder patternTable, int round,
            SccSolution sol, SeedBootstrapCheck.Verdict verdict, Set<IAEItemStack> keySet,
            Map<ICraftingPatternDetails, Double> upperCaps,
            Map<ICraftingPatternDetails, Integer> pressCounts, double deficitSum) {
        trace.append("== round ").append(round).append(" 模拟轮=").append(verdict.roundsUsed)
                .append(" 原因=").append(verdict.failReason).append(" 赤字和=").append(deficitSum)
                .append(" 执行数=").append(sol.executions.size()).append('\n');
        for (int j = 0; j < sol.executions.size(); j++) {
            Execution exec = sol.executions.get(j);
            if (!isStuck(verdict.levels[j], exec.count)) {
                continue;
            }
            Integer id = ids.get(exec.pattern);
            if (id == null) {
                id = ids.size();
                ids.put(exec.pattern, id);
                patternTable.append('#').append(id).append(" in=")
                        .append(fmtMap(SccLpModelBuilder.condensedInputs(exec.pattern, exec.variantInputs)))
                        .append(" out=")
                        .append(fmtArr(exec.pattern.getCondensedOutputs()))
                        .append(" returns=")
                        .append(fmtMap(FlowReconciler.returnsPerCraft(exec.pattern))).append('\n');
            }
            trace.append("  #").append(id).append(" count=").append(exec.count)
                    .append(" level=").append(verdict.levels[j])
                    .append(isUnitGainSource(exec, keySet) ? " 增益" : "")
                    .append(" cap=")
                    .append(upperCaps.getOrDefault(exec.pattern, Double.MAX_VALUE));
            // 阻塞键:单元键中终态可用量不足以再执行一次的输入
            StringBuilder blk = new StringBuilder();
            for (Map.Entry<IAEItemStack, Long> in : SccLpModelBuilder
                    .condensedInputs(exec.pattern, exec.variantInputs).entrySet()) {
                if (!keySet.contains(in.getKey())) {
                    continue;
                }
                double availK = verdict.finalAvail.getOrDefault(in.getKey(), 0.0);
                if (availK < in.getValue() * 1e-6) {
                    blk.append(' ').append(fmtKey(in.getKey())).append('=').append(availK);
                }
            }
            trace.append(blk.length() > 0 ? " 阻塞键:" + blk : " 阻塞键:(无-疑调度序)");
            trace.append('\n');
        }
    }

    /** NBT 感知的键名(toString 只给注册名,同注册名多 NBT 变体无法区分). */
    private static String fmtKey(IAEItemStack key) {
        if (key.hasTagCompound()) {
            return key + " nbt=" + key.getDefinition().getTagCompound();
        }
        return String.valueOf(key);
    }

    /** 键 → 数量映射的 NBT 感知格式化. */
    private static String fmtMap(Map<IAEItemStack, Long> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<IAEItemStack, Long> e : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(fmtKey(e.getKey())).append('=').append(e.getValue());
        }
        return sb.append('}').toString();
    }

    /** 输出数组的 NBT 感知格式化. */
    private static String fmtArr(IAEItemStack[] arr) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (IAEItemStack s : arr) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(s == null ? "null" : (s.getStackSize() + "x" + fmtKey(
                    RecursiveCraftingHelper.canon(s))));
        }
        return sb.append(']').toString();
    }

    /** 截断轨迹落盘(unit-bootstrap-*.txt):键/库存/需求/终态可用 + 样板表 + 逐轮轨迹. */
    private static void dumpTrace(StringBuilder trace, StringBuilder patternTable, String reason,
            List<IAEItemStack> keys, Map<IAEItemStack, Long> stock,
            Map<IAEItemStack, Double> unitDemand, NetworkPatternIndex index, UnitResult result,
            @Nullable Map<IAEItemStack, Double> finalAvail) {
        StringBuilder sb = new StringBuilder();
        sb.append("[单元截断转储] ").append(reason).append('\n');
        sb.append("赤字: ").append(fmtDoubleKeyMap(result.deficits)).append('\n');
        for (IAEItemStack key : keys) {
            sb.append("键: ").append(fmtKey(key)).append(" 库存=").append(stock.getOrDefault(key, 0L))
                    .append(" 需求=").append(unitDemand.getOrDefault(key, 0.0))
                    .append(" 发射台=").append(index.canEmit(key))
                    .append(finalAvail == null ? ""
                            : " 终态可用=" + finalAvail.getOrDefault(key, 0.0))
                    .append('\n');
        }
        sb.append("== 样板表 ==\n").append(patternTable);
        sb.append("== 逐轮轨迹 ==\n").append(trace);
        java.io.File f = UnitTraceDump.dump("bootstrap", sb);
        if (f != null) {
            SpecialLog.info("[LP计划] 单元截断轨迹已转储: {}", f.getAbsolutePath());
        }
    }

    /** 键 → Double 映射的 NBT 感知格式化(赤字清单用). */
    private static String fmtDoubleKeyMap(Map<IAEItemStack, Double> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<IAEItemStack, Double> e : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(fmtKey(e.getKey())).append('=').append(e.getValue());
        }
        return sb.append('}').toString();
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

    /**
     * 根单元求解诊断（{@code /ae2e debug specialcrafting on} 时生效）:
     * 键集/库存/发射台/样板输入输出/LP 解全量 dump,用于定位"根单元 LP 判不可行"
     * (0 执行 + 根行赤字,截断与降级路径均不经过,常规日志不可见).
     */
    private static void dumpUnitDetail(NetworkPatternIndex index, List<IAEItemStack> keys,
            List<ICraftingPatternDetails> patterns, Map<IAEItemStack, Long> stock,
            Map<IAEItemStack, Double> unitDemand, UnitResult result) {
        if (!SpecialLog.isEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[LP计划] 根单元诊断: 键数=").append(keys.size())
                .append(" 样板数=").append(patterns.size()).append(" 需求=").append(unitDemand);
        int maxKeys = Math.min(keys.size(), 64);
        for (int i = 0; i < maxKeys; i++) {
            IAEItemStack key = keys.get(i);
            sb.append("\n  键: ").append(key)
                    .append(" 库存=").append(stock.getOrDefault(key, 0L))
                    .append(" 发射台=").append(index.canEmit(key));
        }
        if (keys.size() > maxKeys) {
            sb.append("\n  ...略 ").append(keys.size() - maxKeys).append(" 键");
        }
        int maxPatterns = Math.min(patterns.size(), 128);
        for (int i = 0; i < maxPatterns; i++) {
            ICraftingPatternDetails p = patterns.get(i);
            sb.append("\n  样板: in=").append(java.util.Arrays.toString(p.getCondensedInputs()))
                    .append(" out=").append(java.util.Arrays.toString(p.getCondensedOutputs()))
                    .append(" 可合成=").append(p.isCraftable());
        }
        if (patterns.size() > maxPatterns) {
            sb.append("\n  ...略 ").append(patterns.size() - maxPatterns).append(" 样板");
        }
        sb.append("\n  解: 执行=").append(result.executions.size())
                .append(" 赤字=").append(result.deficits)
                .append(" 环外折算=").append(result.externalDemands)
                .append(" 种子需求=").append(result.seedDemands);
        SpecialLog.info(sb.toString());
    }

    /**
     * 根单元快速路径诊断（无样板时）:根键索引无生产者会落此路径
     * （如流体根键与样板输出键不匹配）,键的完整 toString 可供比对 NBT 差异.
     */
    private static void dumpFastPathRoot(NetworkPatternIndex index, List<IAEItemStack> keys,
            Map<IAEItemStack, Long> stock, Map<IAEItemStack, Double> unitDemand) {
        if (!SpecialLog.isEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[LP计划] 根单元诊断(快速路径-无样板): 键数=").append(keys.size())
                .append(" 需求=").append(unitDemand);
        for (IAEItemStack key : keys) {
            sb.append("\n  键: ").append(key)
                    .append(" 库存=").append(stock.getOrDefault(key, 0L))
                    .append(" 发射台=").append(index.canEmit(key))
                    .append(" 索引生产者数=").append(index.patternsFor(key).size());
        }
        SpecialLog.info(sb.toString());
    }

    /** 截断接受:执行数取可行水位,环外折算按水位重算,交付缺口转赤字. */
    private static UnitResult truncateToFeasibleLevel(ICraftingGrid cc, NetworkPatternIndex index,
            List<IAEItemStack> keys, Map<IAEItemStack, Double> unitDemand, SccSolution sol,
            SeedBootstrapCheck.Verdict verdict, int iterations, String reason) {
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
        for (Map.Entry<IAEItemStack, Double> d : StableOrder.sortedEntries(unitDemand)) {
            if (index.canEmit(d.getKey())) {
                continue;
            }
            double shortfall = d.getValue() - verdict.finalAvail.getOrDefault(d.getKey(), 0.0);
            if (shortfall > 1e-6) {
                deficits.put(d.getKey(), shortfall);
            }
        }
        SpecialLog.info("[LP计划] 单元截断: {},赤字 {} 种", reason, deficits.size());
        Map<IAEItemStack, Double> seedDemands = SccLpSolve.seedDemandsOf(executions, keySet);
        return new UnitResult(executions, deficits, externalDemands, seedDemands, true, reason,
                iterations, false);
    }

    // ===== 求解单元划分(SCC × 输出共享并查集) =====

    /**
     * 构建求解单元划分:同样板的全部凝聚输出键合并为一个单元(跨 SCC 输出样板归并),
     * 不在键图中的键(纯原料)find 时按自身独立单元处理.
     * <p>包内可见:由 {@link NetworkPatternIndex#unitPartition()} 惰性构建并挂索引 memo
     * (P3),同一索引的快照/重算不重复建划分。</p>
     */
    static UnitPartition buildUnitPartition(NetworkPatternIndex index) {
        Map<IAEItemStack, IAEItemStack> parent = new HashMap<>();
        // 先按 SCC 合并:同分量键必须同属一个单元(环守恒不可拆)
        Map<Integer, IAEItemStack> sccRep = new HashMap<>();
        for (IAEItemStack key : StableOrder.sortedKeys(index.keyGraphKeys())) {
            Integer id = index.sccIdOf(key);
            if (id == null) {
                continue;
            }
            IAEItemStack rep = sccRep.putIfAbsent(id, key);
            if (rep != null) {
                union(parent, rep, key);
            }
        }
        // 再按"同样板输出共享"合并:跨 SCC 输出的样板归并为一个单元
        Set<ICraftingPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IAEItemStack key : StableOrder.sortedKeys(index.keyGraphKeys())) {
            List<ICraftingPatternDetails> producers = new ArrayList<>(index.patternsFor(key));
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
                        union(parent, first, canon);
                    }
                }
            }
        }
        return new UnitPartition(parent);
    }

    /** 并查集合并(确定性:canon 键较小者为根,合并序不影响最终根). */
    private static void union(Map<IAEItemStack, IAEItemStack> parent, IAEItemStack a, IAEItemStack b) {
        IAEItemStack ra = rootOf(parent, a);
        IAEItemStack rb = rootOf(parent, b);
        if (ra.equals(rb)) {
            return;
        }
        IAEItemStack root;
        IAEItemStack child;
        if (StableOrder.KEY_COMPARATOR.compare(ra, rb) <= 0) {
            root = ra;
            child = rb;
        } else {
            root = rb;
            child = ra;
        }
        parent.put(child, root);
        parent.putIfAbsent(root, root);
    }

    /** 追根(构造期用;不做就地压缩,压缩在构造收尾一次性完成). */
    private static IAEItemStack rootOf(Map<IAEItemStack, IAEItemStack> parent, IAEItemStack key) {
        IAEItemStack cur = key;
        for (;;) {
            IAEItemStack up = parent.get(cur);
            if (up == null || up.equals(cur)) {
                return cur;
            }
            cur = up;
        }
    }

    /**
     * 求解单元划分(构造后完全不可变:{@link #find} 无副作用,成员表预建),
     * 故可挂在索引上跨求解/跨线程复用(P3)。
     * <p>构造期收尾把全部键一次性压到最终根:此后 {@code find} 退化为一次查表。</p>
     */
    static final class UnitPartition {
        private final Map<IAEItemStack, IAEItemStack> root;
        private final Map<IAEItemStack, List<IAEItemStack>> members;

        UnitPartition(Map<IAEItemStack, IAEItemStack> parent) {
            Map<IAEItemStack, IAEItemStack> flat = new HashMap<>(parent.size() * 2);
            for (IAEItemStack key : parent.keySet()) {
                flat.put(key, rootOf(parent, key));
            }
            Map<IAEItemStack, List<IAEItemStack>> grouped = new HashMap<>();
            for (Map.Entry<IAEItemStack, IAEItemStack> entry : flat.entrySet()) {
                grouped.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
            }
            for (Map.Entry<IAEItemStack, List<IAEItemStack>> entry : grouped.entrySet()) {
                entry.setValue(Collections.unmodifiableList(StableOrder.sortedKeys(entry.getValue())));
            }
            this.root = flat;
            this.members = grouped;
        }

        IAEItemStack find(IAEItemStack key) {
            IAEItemStack r = this.root.get(key);
            return r == null ? key : r; // 不在键图/未合并:自身即单元
        }

        /** 单元成员键表(canon 键升序);未出现在任何合并中的键(纯原料)返回单键表. */
        List<IAEItemStack> members(IAEItemStack unit) {
            List<IAEItemStack> list = this.members.get(unit);
            return list == null ? Collections.singletonList(unit) : list;
        }
    }

    /** 单元的全部样板(主输出 + 副产物,身份去重;按样板签名确定序). */
    private static List<ICraftingPatternDetails> collectPatterns(ICraftingGrid cc, NetworkPatternIndex index,
            List<IAEItemStack> keys) {
        Set<ICraftingPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ICraftingPatternDetails> patterns = new ArrayList<>();
        for (IAEItemStack key : keys) {
            for (ICraftingPatternDetails p : index.patternsFor(key)) {
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
        // 样板序确定化:单元发现序(单元拓扑序/执行记录序)不得随索引重建漂移
        return StableOrder.sortedPatterns(patterns);
    }

    /**
     * Kahn 拓扑（消费方先解）:单元的全部下游单元已解才可解（需求先齐备）.
     * 根单元无下游,最先入队;解完 U 后其上游 V 的未解下游数 −1,归 0 即入队.
     * 冷凝图按构造无环,故所有可达单元都会出队.
     * <p>{@code listed} 兜底:替代候选扩边(P4)在极端回环网络里可能给单元图引入
     * 环(alt 既是某单元的替代候选、其生产者又吃该单元产物),此时无环假设不成立;
     * 去重保证每个单元最多求解一次(重复求解会双计执行记录,比漏解更危险).</p>
     */
    private static List<IAEItemStack> topologicalOrder(IAEItemStack rootUnit,
            Map<IAEItemStack, Set<IAEItemStack>> upstream, Map<IAEItemStack, Set<IAEItemStack>> downstream) {
        Map<IAEItemStack, Integer> remainingDownstream = new HashMap<>();
        for (IAEItemStack unit : upstream.keySet()) {
            remainingDownstream.put(unit, downstream.getOrDefault(unit, Collections.emptySet()).size());
        }
        List<IAEItemStack> order = new ArrayList<>();
        Set<IAEItemStack> listed = new java.util.HashSet<>();
        ArrayDeque<IAEItemStack> ready = new ArrayDeque<>();
        ready.add(rootUnit);
        while (!ready.isEmpty()) {
            IAEItemStack unit = ready.poll();
            if (!listed.add(unit)) {
                continue;
            }
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

    /** P0 单元画像条目(开发期埋点:{@link PruneSettings#unitProfile}). */
    private static final class UnitStat {
        final int keys;
        final int patterns;
        final int iterations;
        final long nanos;
        /** fastpath | gated | closed | lp. */
        final String decision;
        final IAEItemStack repKey;

        UnitStat(List<IAEItemStack> keys, List<ICraftingPatternDetails> patterns, int iterations,
                long nanos, String decision) {
            this.keys = keys.size();
            this.patterns = patterns.size();
            this.iterations = iterations;
            this.nanos = nanos;
            this.decision = decision;
            this.repKey = keys.isEmpty() ? null : keys.get(0);
        }
    }

    /** 平凡单元判定(97–98% 活跃单元是单样板单键;P0 画像口径). */
    private static boolean isTrivialUnit(UnitStat stat) {
        return stat.keys <= 1 && stat.patterns <= 1;
    }

    /** 巨单元判定(P0 画像口径;实测巨单元 563 键). */
    private static final int GIANT_UNIT_KEYS = 64;

    /** P0 画像输出:按耗时降序 top-N + 巨单元/平凡单元占比 + 剪枝审计汇总. */
    private static void printUnitProfile(List<UnitStat> stats, PruneSettings settings, int units,
            int fastPathUnits, int gatedUnits, int closedFormUnits, int lpUnits, int iterations,
            int degradedUnits, long wallMs) {
        if (stats == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(2048);
        sb.append("[UNIT-PROFILE] ").append(settings).append(" 单元=").append(units)
                .append(" 快速路径=").append(fastPathUnits).append(" 门控跳过=").append(gatedUnits)
                .append(" 闭式=").append(closedFormUnits).append(" 走LP=").append(lpUnits - closedFormUnits)
                .append(" LP单元=").append(lpUnits).append(" 迭代=").append(iterations)
                .append(" 降级=").append(degradedUnits).append(" 墙钟=").append(wallMs).append("ms\n");
        // 分桶:LP/闭式单元按键数与样板数分巨/平凡/其余
        long giantNanos = 0;
        int giantCount = 0;
        int giantIters = 0;
        long trivialNanos = 0;
        int trivialCount = 0;
        int trivialIters = 0;
        long otherNanos = 0;
        int otherCount = 0;
        int otherIters = 0;
        long activeNanos = 0;
        for (UnitStat stat : stats) {
            if ("gated".equals(stat.decision) || "fastpath".equals(stat.decision)) {
                continue;
            }
            activeNanos += stat.nanos;
            if (stat.keys >= GIANT_UNIT_KEYS) {
                giantCount++;
                giantNanos += stat.nanos;
                giantIters += stat.iterations;
            } else if (isTrivialUnit(stat)) {
                trivialCount++;
                trivialNanos += stat.nanos;
                trivialIters += stat.iterations;
            } else {
                otherCount++;
                otherNanos += stat.nanos;
                otherIters += stat.iterations;
            }
        }
        sb.append("[UNIT-PROFILE] 活跃单元耗时占比: 巨单元(键≥").append(GIANT_UNIT_KEYS).append(")=")
                .append(giantCount).append("个/").append(giantNanos / 1_000_000).append("ms/")
                .append(giantIters).append("迭代 ")
                .append(pct(giantNanos, activeNanos)).append(" | 平凡(单样板单键)=")
                .append(trivialCount).append("个/").append(trivialNanos / 1_000_000).append("ms/")
                .append(trivialIters).append("迭代 ").append(pct(trivialNanos, activeNanos))
                .append(" | 其余=").append(otherCount).append("个/")
                .append(otherNanos / 1_000_000).append("ms/").append(otherIters).append("迭代 ")
                .append(pct(otherNanos, activeNanos)).append('\n');
        List<UnitStat> sorted = new ArrayList<>(stats);
        sorted.sort((a, b) -> {
            int cmp = Long.compare(b.nanos, a.nanos);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(b.keys, a.keys);
        });
        int topN = Math.min(10, sorted.size());
        sb.append("[UNIT-PROFILE] 耗时 top-").append(topN).append(":\n");
        for (int i = 0; i < topN; i++) {
            UnitStat stat = sorted.get(i);
            sb.append("[UNIT-PROFILE]   #").append(i + 1).append(' ').append(stat.decision)
                    .append(" 键=").append(stat.keys).append(" 样板=").append(stat.patterns)
                    .append(" 迭代=").append(stat.iterations)
                    .append(" 耗时=").append(stat.nanos / 1_000_000).append("ms 代表键=")
                    .append(stat.repKey).append('\n');
        }
        System.out.print(sb);
    }

    /** 占比格式化(基准分母为 0 时输出 0%). */
    private static String pct(long part, long total) {
        if (total <= 0) {
            return "0%";
        }
        return String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * part / total);
    }
}
