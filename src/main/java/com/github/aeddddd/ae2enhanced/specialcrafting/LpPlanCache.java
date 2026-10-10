package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;

/**
 * 计划结果缓存(任务 4):C3 求解闭包缓存 + C1 计划结果缓存.
 *
 * <p>每 {@link NetworkPatternIndex} 持有一份,生命周期与索引绑定
 * (recalculateCraftingPatterns 置空索引 ⇒ 缓存随之丢弃,天然正确).</p>
 *
 * <ul>
 * <li><b>C3 闭包</b>({@code rootKey} → {@link Closure}):单元发现 + 单元间边 + 拓扑序
 * 只依赖索引(单元成员键、样板输入键),与库存/需求无关(见方案地基事实 1),故可按根键
 * 整体复用.同一闭包同时给出<b>覆盖键表</b>(闭包键 ∪ 可达样板全部输入键 ∪ 根键)——
 * 稀疏库存(C2)与指纹都按此表;</li>
 * <li><b>C1 计划</b>({@code rootKey, target, pruneMode, solverMode, 指纹} → {@link CondensationPlanner.LpPlanOutcome}):
 * 指纹 = 覆盖键逐位 (键哈希, 库存 long, 发射位) 的 64 位混淆值,只作快速否决;命中时
 * 仍<b>逐元素精确比较</b>库存/发射位数组,零误返.命中返回同一次计算的产物
 * (产物不可变;读取方不得修改——见 {@code FlowReconciler} R1),故冷/热逐位一致.</li>
 * </ul>
 *
 * <p><b>确定性纪律</b>:覆盖键表按 canon 键升序({@link StableOrder#sortedKeys}),
 * 逐键哈希取 {@link StableOrder#keyOf} 的规范串(FNV-1a,与 JVM 启动无关);
 * pruneMode/solverMode 进 C1 键——同 JVM 切换属性不得跨模式命中.</p>
 *
 * <p><b>线程安全</b>:两个 LRU 为 accessOrder {@code LinkedHashMap},全部读写在同一把
 * 实例锁内(容量小,临界区为 O(条目数) 的 O(1) 操作);闭包未命中时的构建也在锁内完成,
 * 保证同一根键的闭包只构建一次.缓存产物不可变,可跨线程共享读取.</p>
 *
 * <p><b>指标</b>(仅 {@code ae2e.planCacheStats=true} 时上报 MetricsRegistry):
 * {@code plan.cache.closure.hit|miss}、{@code plan.cache.plan.hit|miss}、
 * {@code plan.cache.plan.hit.shortfall}(命中产物含赤字/降级);稀疏库存覆盖集外读穿
 * ({@code plan.cache.sparse.fallback})恒定维护——它是覆盖集完备性的自检信号,正常为 0,
 * 因为读穿本身已是异常路径。指纹耗时与字节估算不注册 Gauge(按索引实例注册会随索引
 * 重建泄漏注册表项),改由 {@link Stats} 暴露.</p>
 *
 * <p><b>已知偏差</b>:命中路径直接返回产物,不再重跑求解期诊断(计划快照落盘/单元
 * 画像/剪枝轨迹)——这些诊断只在首次计算时产生({@code /ae2e debug specialcrafting} 门控).</p>
 */
public final class LpPlanCache {

    /** 缓存档位. */
    public enum Mode {
        /** 全关(对拍基线):不缓存闭包,库存走旧全量遍历. */
        OFF,
        /** 仅 C3 闭包 + C2 稀疏库存. */
        CLOSURE,
        /** C3 闭包 + C2 稀疏库存 + C1 计划结果(默认). */
        FULL
    }

    /** 入口一次性解析的开关(此后不得再读系统属性). */
    public static final class Settings {
        public final Mode mode;
        /** C1 计划条目上限(LRU). */
        public final int planMax;
        /** C3 闭包条目上限(LRU). */
        public final int closureMax;
        /** 命中/未命中指标是否上报. */
        public final boolean stats;
        /** C1 计划条目估算字节软上限(超限按 LRU 逐出). */
        public final long maxBytes;

        private Settings(Mode mode, int planMax, int closureMax, boolean stats, long maxBytes) {
            this.mode = mode;
            this.planMax = planMax;
            this.closureMax = closureMax;
            this.stats = stats;
            this.maxBytes = maxBytes;
        }

        /** 求解入口一次性解析系统属性(此后不得再读). */
        public static Settings fromSystemProperties() {
            String raw = System.getProperty("ae2e.planCache", "full");
            Mode mode;
            switch (raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)) {
                case "off":
                    mode = Mode.OFF;
                    break;
                case "closure":
                    mode = Mode.CLOSURE;
                    break;
                default:
                    mode = Mode.FULL;
                    break;
            }
            int planMax = Math.max(1, Integer.getInteger("ae2e.planCacheMax", 8));
            int closureMax = Math.max(1, Integer.getInteger("ae2e.planCacheClosureMax", 32));
            long maxBytes = Math.max(0L, Long.getLong("ae2e.planCacheBytesMB", 8L)) * 1024L * 1024L;
            return new Settings(mode, planMax, closureMax, Boolean.getBoolean("ae2e.planCacheStats"),
                    maxBytes);
        }

        /** 显式档位(测试/对拍用;字节上限 8MB). */
        public static Settings of(Mode mode, int planMax, int closureMax, boolean stats) {
            return new Settings(mode, planMax, closureMax, stats, 8L * 1024L * 1024L);
        }

        /** 闭包缓存是否启用(同时决定库存是否走稀疏口径). */
        public boolean closureEnabled() {
            return this.mode != Mode.OFF;
        }

        /** C1 计划缓存是否启用. */
        public boolean planEnabled() {
            return this.mode == Mode.FULL;
        }

        @Override
        public String toString() {
            return "planCache=" + this.mode + " planMax=" + this.planMax + " closureMax="
                    + this.closureMax + (this.stats ? " stats" : "");
        }
    }

    /**
     * 不可变求解闭包:C3 缓存值.
     * <p>{@code order} 为冷凝拓扑序(消费方先),{@code unitKeys}/{@code unitPatterns}
     * 由单元代表键索引;{@code allKeys}/{@code keyHashes} 为覆盖键表与逐键哈希(下标对齐).</p>
     */
    public static final class Closure {
        public final IAEItemStack rootKey;
        /** 根键所属求解单元代表键(拓扑序首元素;需求累加宿主). */
        public final IAEItemStack rootUnit;
        public final List<IAEItemStack> order;
        public final Map<IAEItemStack, List<IAEItemStack>> unitKeys;
        public final Map<IAEItemStack, List<ICraftingPatternDetails>> unitPatterns;
        /** 覆盖键(canon 键升序):闭包键 ∪ 可达样板全部输入键 ∪ {根键}. */
        public final List<IAEItemStack> allKeys;
        /** 与 {@link #allKeys} 下标对齐的 64 位键哈希(启动无关,仅作指纹混淆). */
        public final long[] keyHashes;

        Closure(IAEItemStack rootKey, IAEItemStack rootUnit, List<IAEItemStack> order,
                Map<IAEItemStack, List<IAEItemStack>> unitKeys,
                Map<IAEItemStack, List<ICraftingPatternDetails>> unitPatterns,
                List<IAEItemStack> allKeys, long[] keyHashes) {
            this.rootKey = rootKey;
            this.rootUnit = rootUnit;
            this.order = order;
            this.unitKeys = unitKeys;
            this.unitPatterns = unitPatterns;
            this.allKeys = allKeys;
            this.keyHashes = keyHashes;
        }

        /** 覆盖键数(指纹与稀疏库存规模). */
        public int coverageSize() {
            return this.allKeys.size();
        }
    }

    /** 闭包构建回调(由 {@link CondensationPlanner} 提供,避免反向依赖). */
    public interface ClosureBuilder {
        Closure build();
    }

    /** C1 计划缓存条目(不可变;值 = 同一次计算的产物,读取方不得修改). */
    private static final class PlanEntry {
        final Closure closure;
        final long target;
        final PruneSettings.Mode prune;
        final String solverMode;
        final long fingerprint;
        /** 与 {@link Closure#allKeys} 对齐的库存精确值(指纹命中后逐元素复核). */
        final long[] values;
        /** 与 {@link Closure#allKeys} 对齐的发射位(0/1). */
        final long[] emits;
        final CondensationPlanner.LpPlanOutcome outcome;

        PlanEntry(Closure closure, long target, PruneSettings.Mode prune, String solverMode,
                long fingerprint, long[] values, long[] emits,
                CondensationPlanner.LpPlanOutcome outcome) {
            this.closure = closure;
            this.target = target;
            this.prune = prune;
            this.solverMode = solverMode;
            this.fingerprint = fingerprint;
            this.values = values;
            this.emits = emits;
            this.outcome = outcome;
        }
    }

    /** C1 键:闭包身份 + 请求量 + 两个模式档 + 指纹(等价性由指纹 + 逐元素复核共同保证). */
    private static final class PlanKey {
        final Closure closure;
        final long target;
        final PruneSettings.Mode prune;
        final String solverMode;
        final long fingerprint;

        PlanKey(Closure closure, long target, PruneSettings.Mode prune, String solverMode,
                long fingerprint) {
            this.closure = closure;
            this.target = target;
            this.prune = prune;
            this.solverMode = solverMode;
            this.fingerprint = fingerprint;
        }

        @Override
        public int hashCode() {
            int h = System.identityHashCode(this.closure);
            h = h * 31 + (int) (this.target ^ (this.target >>> 32));
            h = h * 31 + this.prune.ordinal();
            h = h * 31 + this.solverMode.hashCode();
            h = h * 31 + (int) (this.fingerprint ^ (this.fingerprint >>> 32));
            return h;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PlanKey)) {
                return false;
            }
            PlanKey o = (PlanKey) other;
            return this.closure == o.closure && this.target == o.target && this.prune == o.prune
                    && this.fingerprint == o.fingerprint && this.solverMode.equals(o.solverMode);
        }
    }

    /** 指纹混淆常数(FNV-1a 64 位). */
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** 闭包 LRU(accessOrder;容量由调用方 Settings 约束). */
    private final LinkedHashMap<IAEItemStack, Closure> closures =
            new LinkedHashMap<>(16, 0.75f, true);
    /** 计划 LRU(accessOrder;容量 + 字节软上限由调用方 Settings 约束). */
    private final LinkedHashMap<PlanKey, PlanEntry> plans = new LinkedHashMap<>(16, 0.75f, true);
    private long planBytes;

    // ===== 命中/未命中计数(测试与指标共用;恒定维护)=====
    private final AtomicLong closureHits = new AtomicLong();
    private final AtomicLong closureMisses = new AtomicLong();
    private final AtomicLong planHits = new AtomicLong();
    private final AtomicLong planMisses = new AtomicLong();
    private final AtomicLong fingerprintNanos = new AtomicLong();
    /** 稀疏库存覆盖集外读穿次数(正常恒 0:覆盖集 = 求解器实际读取的键集). */
    private final AtomicLong coverageFallbacks = new AtomicLong();

    // ===== C3 闭包 =====

    /**
     * 取根键的求解闭包:命中直接返回(同一索引内根键的闭包是纯函数),未命中则调用
     * {@code builder} 构建并登记(构建在锁内,同一根键只构建一次).
     */
    public Closure closure(IAEItemStack rootKey, ClosureBuilder builder, Settings settings) {
        synchronized (this) {
            Closure cached = this.closures.get(rootKey);
            if (cached == null) {
                cached = builder.build();
                this.closures.put(rootKey, cached);
                while (this.closures.size() > settings.closureMax) {
                    java.util.Iterator<IAEItemStack> it = this.closures.keySet().iterator();
                    it.next();
                    it.remove();
                }
                this.closureMisses.incrementAndGet();
                metrics("plan.cache.closure.miss", settings);
            } else {
                this.closureHits.incrementAndGet();
                metrics("plan.cache.closure.hit", settings);
            }
            return cached;
        }
    }

    // ===== C1 计划 =====

    /**
     * 计划缓存查询:指纹 + (target, prune, solverMode, 闭包身份) 全等后,再逐元素
     * 精确比较库存/发射位数组——两者都相同才返回缓存产物(零误返).
     *
     * @param values 与 {@code closure.allKeys} 对齐的库存精确值
     * @param emits 与 {@code closure.allKeys} 对齐的发射位
     */
    @Nullable
    public CondensationPlanner.LpPlanOutcome planLookup(Closure closure, long target,
            PruneSettings prune, String solverMode, long fingerprint, long[] values, long[] emits,
            Settings settings) {
        PlanKey key = new PlanKey(closure, target, prune.mode, solverMode, fingerprint);
        synchronized (this) {
            PlanEntry entry = this.plans.get(key);
            if (entry == null || !exactMatch(entry, closure, values, emits)) {
                this.planMisses.incrementAndGet();
                metrics("plan.cache.plan.miss", settings);
                return null;
            }
            this.planHits.incrementAndGet();
            metrics("plan.cache.plan.hit", settings);
            if (!entry.outcome.allOptimal || !entry.outcome.deficits.isEmpty()) {
                // 缺料/降级产物同样缓存:补货会改覆盖键库存 ⇒ 指纹失配 ⇒ 自动失效(V4)
                metrics("plan.cache.plan.hit.shortfall", settings);
            }
            return entry.outcome;
        }
    }

    /** 计划缓存登记(值必须与指纹/精确值数组同源). */
    public void planPut(Closure closure, long target, PruneSettings prune, String solverMode,
            long fingerprint, long[] values, long[] emits, CondensationPlanner.LpPlanOutcome outcome,
            Settings settings) {
        PlanKey key = new PlanKey(closure, target, prune.mode, solverMode, fingerprint);
        PlanEntry entry = new PlanEntry(closure, target, prune.mode, solverMode, fingerprint, values,
                emits, outcome);
        long bytes = estimateBytes(entry);
        synchronized (this) {
            PlanEntry previous = this.plans.put(key, entry);
            if (previous != null) {
                this.planBytes -= estimateBytes(previous);
            }
            this.planBytes += bytes;
            boolean overCount = this.plans.size() > settings.planMax;
            boolean overBytes = settings.maxBytes > 0 && this.planBytes > settings.maxBytes;
            while (overCount || overBytes) {
                // 注意:accessOrder LinkedHashMap 上 get() 会重排访问序(并改动 modCount),
                // 逐出必须走 entrySet 迭代器本身(它给出的首元素即 LRU 头)
                java.util.Iterator<Map.Entry<PlanKey, PlanEntry>> it = this.plans.entrySet().iterator();
                if (!it.hasNext()) {
                    break;
                }
                Map.Entry<PlanKey, PlanEntry> eldest = it.next();
                if (eldest.getKey().equals(key)) {
                    break; // 仅剩本次产物:保留最新(下次查询仍可命中)
                }
                this.planBytes -= estimateBytes(eldest.getValue());
                it.remove();
                overCount = this.plans.size() > settings.planMax;
                overBytes = settings.maxBytes > 0 && this.planBytes > settings.maxBytes;
            }
        }
    }

    /** 指纹命中后的逐元素精确复核(闭包身份 + 长度 + 库存值 + 发射位). */
    private static boolean exactMatch(PlanEntry entry, Closure closure, long[] values, long[] emits) {
        if (entry.closure != closure || entry.values.length != values.length
                || entry.emits.length != emits.length) {
            return false;
        }
        for (int i = 0; i < values.length; i++) {
            if (entry.values[i] != values[i] || entry.emits[i] != emits[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 覆盖键指纹:逐键 (键哈希, 库存 long, 发射位) 混合(纯 long 运算,零分配).
     * 只作快速否决——命中仍需 {@link #exactMatch} 逐元素复核.
     */
    public static long fingerprint(Closure closure, long[] values, long[] emits) {
        long h = FNV_OFFSET;
        long[] hashes = closure.keyHashes;
        for (int i = 0; i < hashes.length; i++) {
            h = (h ^ hashes[i]) * FNV_PRIME;
            h = (h ^ values[i]) * FNV_PRIME;
            h = (h ^ emits[i]) * FNV_PRIME;
        }
        return h;
    }

    /** 键规范串 → 64 位哈希(FNV-1a;串来自 StableOrder,故与 JVM 启动无关). */
    public static long hashKey(String keyString) {
        long h = FNV_OFFSET;
        for (int i = 0; i < keyString.length(); i++) {
            h = (h ^ keyString.charAt(i)) * FNV_PRIME;
        }
        return h;
    }

    /** 条目字节估算(数组 + 解产物粗估;软上限逐出用). */
    private static long estimateBytes(PlanEntry entry) {
        long bytes = 64L + 16L * entry.values.length + 16L * entry.emits.length;
        bytes += 200L; // 产物外壳
        bytes += 64L * entry.outcome.executions.size();
        bytes += 64L * entry.outcome.unitSolutions.size();
        for (CondensationPlanner.UnitSolution unit : entry.outcome.unitSolutions) {
            bytes += 32L * unit.keys.size() + 64L * unit.executions.size() + 32L * unit.deficits.size();
        }
        return bytes;
    }

    // ===== 诊断/测试 =====

    /** 覆盖键表构建助手:去重后按 canon 键升序 + 逐键哈希(构建期一次);结构深拷贝为只读. */
    static Closure makeClosure(IAEItemStack rootKey, IAEItemStack rootUnit, List<IAEItemStack> order,
            Map<IAEItemStack, List<IAEItemStack>> unitKeys,
            Map<IAEItemStack, List<ICraftingPatternDetails>> unitPatterns,
            java.util.Collection<IAEItemStack> coverageKeys) {
        List<IAEItemStack> allKeys = StableOrder.sortedKeys(coverageKeys);
        long[] hashes = new long[allKeys.size()];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = hashKey(StableOrder.keyOf(allKeys.get(i)));
        }
        Map<IAEItemStack, List<IAEItemStack>> frozenKeys = new LinkedHashMap<>(unitKeys.size() * 2);
        for (Map.Entry<IAEItemStack, List<IAEItemStack>> e : unitKeys.entrySet()) {
            frozenKeys.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        Map<IAEItemStack, List<ICraftingPatternDetails>> frozenPatterns =
                new LinkedHashMap<>(unitPatterns.size() * 2);
        for (Map.Entry<IAEItemStack, List<ICraftingPatternDetails>> e : unitPatterns.entrySet()) {
            frozenPatterns.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return new Closure(rootKey, rootUnit, Collections.unmodifiableList(new ArrayList<>(order)),
                Collections.unmodifiableMap(frozenKeys), Collections.unmodifiableMap(frozenPatterns),
                Collections.unmodifiableList(allKeys), hashes);
    }

    /** 命中率快照(诊断与测试用;不可变值对象). */
    public Stats stats() {
        Stats s = new Stats();
        s.closureHits = this.closureHits.get();
        s.closureMisses = this.closureMisses.get();
        s.planHits = this.planHits.get();
        s.planMisses = this.planMisses.get();
        s.fingerprintNanos = this.fingerprintNanos.get();
        s.coverageFallbacks = this.coverageFallbacks.get();
        synchronized (this) {
            s.closureEntries = this.closures.size();
            s.planEntries = this.plans.size();
            s.planBytes = this.planBytes;
        }
        return s;
    }

    /** 计数清零(测试用;不清缓存内容). */
    public void resetStats() {
        this.closureHits.set(0);
        this.closureMisses.set(0);
        this.planHits.set(0);
        this.planMisses.set(0);
        this.fingerprintNanos.set(0);
        this.coverageFallbacks.set(0);
    }

    /** 清空缓存内容(测试用). */
    public void clear() {
        synchronized (this) {
            this.closures.clear();
            this.plans.clear();
            this.planBytes = 0;
        }
    }

    /** 记录一次指纹耗时(ns;诊断). */
    void noteFingerprintNanos(long nanos) {
        if (nanos > 0) {
            this.fingerprintNanos.addAndGet(nanos);
        }
    }

    /**
     * 记录一次稀疏库存覆盖集外读穿(诊断):正常为 0,非 0 说明覆盖集分析有漏——
     * 该次读取仍返回真值(读穿兜底),但应在覆盖集构建处补齐(测试以此验证完备性).
     */
    void noteCoverageFallback() {
        this.coverageFallbacks.incrementAndGet();
        MetricsRegistry.counter("plan.cache.sparse.fallback").increment();
    }

    private static void metrics(String name, Settings settings) {
        if (settings.stats) {
            MetricsRegistry.counter(name).increment();
        }
    }

    /** 命中率/占用快照. */
    public static final class Stats {
        public long closureHits;
        public long closureMisses;
        public long planHits;
        public long planMisses;
        public long fingerprintNanos;
        /** 稀疏库存覆盖集外读穿次数(正常 0). */
        public long coverageFallbacks;
        public int closureEntries;
        public int planEntries;
        public long planBytes;

        @Override
        public String toString() {
            return "closure " + closureHits + "/" + (closureHits + closureMisses)
                    + " plan " + planHits + "/" + (planHits + planMisses)
                    + " entries=" + closureEntries + "/" + planEntries + " bytes=" + planBytes;
        }
    }
}
