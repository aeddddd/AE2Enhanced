package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

/**
 * 跨样板循环链分析器（阶段 2,泛化版,移植自 1.20.1）.
 * <p>枚举经过请求物品的<b>简单环</b>,对每个环键集建立"样板×物品"精确系数矩阵,
 * 以广义叉积（Bareiss 精确行列式）求平衡方程组的正整数零空间向量,得到各样板执行
 * 次数比与环净乘积率分类（增殖/中性/耗散）;再以超轮前缀分析求各环内物品的启动种子.</p>
 * <p>秩不足/无正整数解/数值超 long 时返回 null,由调用方回落原生行为.</p>
 */
public final class CycleAnalyzer {

    /**
     * 环净乘积率分类.
     */
    public enum RateClass {
        /** 每轮净产出为正,可增殖. */
        PRODUCTIVE,
        /** 进出相等（中性环）,不接管. */
        NEUTRAL,
        /** 净产出为负（耗散环）,不接管. */
        DISSIPATIVE
    }

    /**
     * 环的一步:{@code pattern} 将 {@code fromKey}（路径视角的主输入）转化为 {@code toKey}.
     */
    public static final class CycleStep {
        private final ICraftingPatternDetails pattern;
        private final IAEItemStack fromKey;
        private final IAEItemStack toKey;

        public CycleStep(ICraftingPatternDetails pattern, IAEItemStack fromKey, IAEItemStack toKey) {
            this.pattern = pattern;
            this.fromKey = fromKey;
            this.toKey = toKey;
        }

        public ICraftingPatternDetails pattern() {
            return pattern;
        }

        public IAEItemStack fromKey() {
            return fromKey;
        }

        public IAEItemStack toKey() {
            return toKey;
        }
    }

    /**
     * 环分析结果.
     */
    public static final class Analysis {
        private final List<IAEItemStack> keys;
        private final List<CycleStep> steps;
        private final RateClass rateClass;
        private final long[] timesPerRound;
        private final long netGain;
        private final long[] seedsPerKey;
        private final long[] batchSeedPerKey;

        Analysis(List<IAEItemStack> keys, List<CycleStep> steps, RateClass rateClass,
                long[] timesPerRound, long netGain, long[] seedsPerKey, long[] batchSeedPerKey) {
            this.keys = keys;
            this.steps = steps;
            this.rateClass = rateClass;
            this.timesPerRound = timesPerRound;
            this.netGain = netGain;
            this.seedsPerKey = seedsPerKey;
            this.batchSeedPerKey = batchSeedPerKey;
        }

        public List<IAEItemStack> keys() {
            return keys;
        }

        public List<CycleStep> steps() {
            return steps;
        }

        public RateClass rateClass() {
            return rateClass;
        }

        public long[] timesPerRound() {
            return timesPerRound;
        }

        public long netGain() {
            return netGain;
        }

        public long[] seedsPerKey() {
            return seedsPerKey;
        }

        public long[] batchSeedPerKey() {
            return batchSeedPerKey;
        }
    }

    /** detector/求解共用的遍历预算,避免超大网络下 DFS 失控. */
    private static final int MAX_VISITED = 512;
    /** 单次请求最多枚举的候选环数量. */
    private static final int MAX_CYCLES = 64;

    private CycleAnalyzer() {
    }

    /**
     * 一次编译/求解共享的生产者索引:主产出查询按键缓存,副产物生产者倒排
     * (全样板扫描<b>一次</b>建成,替代原先"每个缓存未命中键都全扫一遍"的
     * O(键数×样板数) 开销——极端规模基准中该开销占 DAG 编译 98% 耗时).
     * <p>副产物扫描依赖 {@code ICraftingGridCacheAccess} 暴露的全样板键集;
     * 不可用时(如单元测试的模拟网格)退化为仅主产出索引.</p>
     */
    public static final class ProducerIndex {
        private final ICraftingGrid cc;
        private final World world;
        private final Map<IAEItemStack, List<ICraftingPatternDetails>> cache = new LinkedHashMap<>();
        /** 网络级共享索引(SCC/副产物倒排);非本模组缓存实现(如测试模拟网格)为 null. */
        @Nullable
        private final NetworkPatternIndex shared;
        @Nullable
        private Map<IAEItemStack, List<ICraftingPatternDetails>> byproductIndex;

        public ProducerIndex(ICraftingGrid cc, World world) {
            this.cc = cc;
            this.world = world;
            this.shared = NetworkPatternIndex.of(cc);
        }

        /** 网络级共享索引(可为 null). */
        @Nullable
        NetworkPatternIndex shared() {
            return this.shared;
        }

        /** 生产 {@code current} 的全部样板:主产出索引快路径 + 副产物倒排(按键缓存). */
        List<ICraftingPatternDetails> producersOf(IAEItemStack current) {
            List<ICraftingPatternDetails> cached = this.cache.get(current);
            if (cached != null) {
                return cached;
            }
            List<ICraftingPatternDetails> out = new ArrayList<>();
            for (ICraftingPatternDetails pattern : this.cc.getCraftingFor(current, null, -1,
                    this.world)) {
                out.add(pattern);
            }
            List<ICraftingPatternDetails> extra = this.byproductIndex().getOrDefault(current,
                    Collections.emptyList());
            if (!extra.isEmpty()) {
                Set<ICraftingPatternDetails> seen = Collections
                        .newSetFromMap(new java.util.IdentityHashMap<>());
                seen.addAll(out);
                for (ICraftingPatternDetails pattern : extra) {
                    if (seen.add(pattern)) {
                        out.add(pattern);
                    }
                }
            }
            this.cache.put(current, out);
            return out;
        }

        /**
         * 副产物生产者倒排:输出键 → 以它为非主索引输出的样板列表.
         * 优先复用网络级共享索引(全样板扫描一次建成);不可用时按旧路径自建.
         */
        private Map<IAEItemStack, List<ICraftingPatternDetails>> byproductIndex() {
            if (this.byproductIndex == null) {
                if (this.shared != null) {
                    this.byproductIndex = this.shared.byproductMap();
                    return this.byproductIndex;
                }
                this.byproductIndex = new LinkedHashMap<>();
                if (this.cc instanceof com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess) {
                    for (IAEItemStack craftable : ((com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess) this.cc)
                            .ae2enhanced$craftableKeys()) {
                        IAEItemStack craftableKey = RecursiveCraftingHelper.canon(craftable);
                        for (ICraftingPatternDetails pattern : this.cc.getCraftingFor(craftableKey,
                                null, -1, this.world)) {
                            for (IAEItemStack output : pattern.getCondensedOutputs()) {
                                if (output == null) {
                                    continue;
                                }
                                IAEItemStack outKey = RecursiveCraftingHelper.canon(output);
                                if (outKey.equals(craftableKey)) {
                                    continue; // 主索引键,已由 getCraftingFor 覆盖
                                }
                                List<ICraftingPatternDetails> list = this.byproductIndex
                                        .computeIfAbsent(outKey, k -> new ArrayList<>());
                                if (!list.contains(pattern)) {
                                    list.add(pattern);
                                }
                            }
                        }
                    }
                }
            }
            return this.byproductIndex;
        }
    }

    /**
     * 枚举经过 {@code root} 的所有简单环（长度 ≥ 2;自引用环由阶段 1 处理,此处跳过）,
     * 按环长度降序返回（长环的键集更完整,优先尝试）.
     * <p>生产者发现含<b>副产物边</b>(1.1.0 起):root 作为样板的任意输出(不限主产出)
     * 都视为被该样板生产——否则经副产物闭合的催化环(如 1A→1X+1B、1B→1A)不可见.</p>
     */
    public static List<List<CycleStep>> findCyclesThrough(ICraftingGrid cc, IAEItemStack root, World world) {
        int[] budget = { MAX_VISITED };
        List<List<CycleStep>> cycles = new ArrayList<>();
        Set<IAEItemStack> onPath = new HashSet<>();
        IAEItemStack rootKey = RecursiveCraftingHelper.canon(root);
        onPath.add(rootKey);
        LinkedHashMap<IAEItemStack, CycleStep> chain = new LinkedHashMap<>();
        dfs(cc, world, rootKey, rootKey, onPath, chain, budget, cycles, new ProducerIndex(cc, world));
        cycles.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return cycles;
    }

    /**
     * 沿"被产生"边回溯 DFS:current 由某 pattern 产生,其输入 from 即反向边.
     * from == root 时闭合为环并记录（继续搜索其他环）.
     */
    private static void dfs(ICraftingGrid cc, World world, IAEItemStack root, IAEItemStack current,
            Set<IAEItemStack> onPath, LinkedHashMap<IAEItemStack, CycleStep> chain, int[] budget,
            List<List<CycleStep>> cycles, ProducerIndex producerIndex) {
        if (budget[0]-- <= 0 || cycles.size() >= MAX_CYCLES) {
            return;
        }
        for (ICraftingPatternDetails pattern : producerIndex.producersOf(current)) {
            boolean producesCurrent = false;
            for (IAEItemStack output : pattern.getCondensedOutputs()) {
                if (output != null && current.equals(output) && output.getStackSize() > 0) {
                    producesCurrent = true;
                    break;
                }
            }
            if (!producesCurrent) {
                continue;
            }
            for (IAEItemStack input : pattern.getCondensedInputs()) {
                if (input == null || input.getStackSize() <= 0) {
                    continue;
                }
                IAEItemStack from = RecursiveCraftingHelper.canon(input);
                if (from.equals(current)) {
                    continue; // 自引用交给阶段 1
                }
                CycleStep step = new CycleStep(pattern, from, current);
                if (from.equals(root)) {
                    List<CycleStep> steps = new ArrayList<>();
                    steps.add(step);
                    List<CycleStep> chainSteps = new ArrayList<>(chain.values());
                    Collections.reverse(chainSteps);
                    steps.addAll(chainSteps);
                    cycles.add(steps);
                    if (cycles.size() >= MAX_CYCLES) {
                        return;
                    }
                    continue;
                }
                if (onPath.contains(from)) {
                    continue; // 只接受经过 root 的简单环
                }
                onPath.add(from);
                chain.put(from, step);
                dfs(cc, world, root, from, onPath, chain, budget, cycles, producerIndex);
                chain.remove(from);
                onPath.remove(from);
            }
        }
    }

    /**
     * 样板是否"成环步骤":其某输入键可经"被产生"边回溯到该样板的某输出键
     * (含副产物输出)——即该样板参与一个环(自身可在路径中).
     * detector / DAG 编译器共用,预算受限.
     */
    public static boolean isCycleStep(ICraftingGrid cc, World world, ICraftingPatternDetails pattern) {
        return isCycleStep(cc, world, pattern, new ProducerIndex(cc, world));
    }

    /**
     * 同 {@link #isCycleStep(ICraftingGrid, World, ICraftingPatternDetails)},但共享调用方
     * 提供的 {@link ProducerIndex}——DAG 编译器等批量场景避免逐节点重复全样板扫描.
     */
    public static boolean isCycleStep(ICraftingGrid cc, World world, ICraftingPatternDetails pattern,
            ProducerIndex producerIndex) {
        // 快路径:网络级 SCC 索引 O(输入×输出) 查表,取代逐节点 budget DFS
        // (旧路径在数千节点计划中逐节点重复遍历,是编译主瓶颈;语义等价且不受 512 截断)
        NetworkPatternIndex shared = producerIndex.shared();
        if (shared != null) {
            return shared.isCycleStep(pattern);
        }
        Set<IAEItemStack> outputs = new HashSet<>();
        for (IAEItemStack output : pattern.getCondensedOutputs()) {
            if (output != null) {
                outputs.add(RecursiveCraftingHelper.canon(output));
            }
        }
        int[] budget = { MAX_VISITED };
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            if (reachesOutputs(cc, world, RecursiveCraftingHelper.canon(input), outputs, budget,
                    new HashSet<>(), producerIndex)) {
                return true;
            }
        }
        return false;
    }

    /** 沿"被产生"边回溯:current 的传递原料中是否出现 targets 中的键. */
    private static boolean reachesOutputs(ICraftingGrid cc, World world, IAEItemStack current,
            Set<IAEItemStack> targets, int[] budget, Set<IAEItemStack> visited,
            ProducerIndex producerIndex) {
        if (!visited.add(current) || budget[0]-- <= 0) {
            return false;
        }
        for (ICraftingPatternDetails producer : producerIndex.producersOf(current)) {
            boolean produces = false;
            for (IAEItemStack output : producer.getCondensedOutputs()) {
                if (output != null && current.equals(output)) {
                    produces = true;
                    break;
                }
            }
            if (!produces) {
                continue;
            }
            for (IAEItemStack input : producer.getCondensedInputs()) {
                if (input == null || input.getStackSize() <= 0) {
                    continue;
                }
                IAEItemStack from = RecursiveCraftingHelper.canon(input);
                if (targets.contains(from)) {
                    return true;
                }
                if (reachesOutputs(cc, world, from, targets, budget, visited, producerIndex)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 枚举"发射 what 作为环外副产物"的催化环:对生产 what 的每个样板
     * (what 不在其输入中——否则是自引用/环键,走既有路径),以其输入键为 root
     * 枚举环并筛出包含该样板的环,按环长降序去重返回.
     */
    public static List<List<CycleStep>> findCatalyticCycles(ICraftingGrid cc, IAEItemStack what,
            World world) {
        IAEItemStack whatKey = RecursiveCraftingHelper.canon(what);
        List<List<CycleStep>> out = new ArrayList<>();
        Set<List<ICraftingPatternDetails>> seen = new HashSet<>();
        ProducerIndex producerIndex = new ProducerIndex(cc, world);
        for (ICraftingPatternDetails pattern : producerIndex.producersOf(whatKey)) {
            boolean selfInput = false;
            for (IAEItemStack input : pattern.getCondensedInputs()) {
                if (input != null && whatKey.equals(input)) {
                    selfInput = true;
                    break;
                }
            }
            if (selfInput) {
                continue;
            }
            for (IAEItemStack input : pattern.getCondensedInputs()) {
                if (input == null || input.getStackSize() <= 0) {
                    continue;
                }
                for (List<CycleStep> cycle : findCyclesThrough(cc, input, world)) {
                    boolean contains = false;
                    List<ICraftingPatternDetails> signature = new ArrayList<>();
                    for (CycleStep step : cycle) {
                        signature.add(step.pattern());
                        if (step.pattern() == pattern) {
                            contains = true;
                        }
                    }
                    if (contains && seen.add(signature)) {
                        out.add(cycle);
                    }
                }
            }
        }
        out.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return out;
    }

    /**
     * 催化环每超轮发射的环外副产物 what 数量(what 必须不在环键上).
     */
    public static long byproductPerRound(Analysis analysis, IAEItemStack what) {
        for (IAEItemStack key : analysis.keys()) {
            if (key.equals(what)) {
                return 0;
            }
        }
        long perRound = 0;
        long[] times = analysis.timesPerRound();
        for (int i = 0; i < analysis.steps().size(); i++) {
            for (IAEItemStack output : analysis.steps().get(i).pattern().getCondensedOutputs()) {
                if (output != null && what.equals(output)) {
                    perRound += output.getStackSize() * times[i];
                }
            }
        }
        return perRound;
    }

    /**
     * 分析简单环:闭合性校验后委托求解核心.
     *
     * @return 分析结果;闭合性错误/秩不足/无正整数解/数值超 long 时返回 null.
     */
    @Nullable
    public static Analysis analyze(List<CycleStep> cycle) {
        if (cycle == null || cycle.size() < 2) {
            return null;
        }
        int n = cycle.size();
        List<IAEItemStack> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (!cycle.get(i).toKey().equals(cycle.get((i + 1) % n).fromKey())) {
                return null;
            }
            keys.add(cycle.get(i).fromKey());
        }
        return solveSystem(keys, cycle);
    }

    /**
     * 候选环并集分析（θ 形共享结构:多个环共享同一中间样板,逐环分析会互相把
     * 对方的中间物当环外输入而双双失败）.
     * <p>当 样板数 == 键数 时构成适定方程组,与单环同一套零空间求解;否则返回 null.</p>
     */
    @Nullable
    public static Analysis analyzeUnion(List<List<CycleStep>> cycles) {
        if (cycles == null || cycles.size() < 2) {
            return null;
        }
        IAEItemStack root = cycles.get(0).get(0).fromKey();
        List<IAEItemStack> keys = new ArrayList<>();
        keys.add(root);
        Map<ICraftingPatternDetails, CycleStep> stepByPattern = new LinkedHashMap<>();
        for (List<CycleStep> cycle : cycles) {
            for (CycleStep step : cycle) {
                if (!step.fromKey().equals(root) && !keys.contains(step.fromKey())) {
                    keys.add(step.fromKey());
                }
                if (!step.toKey().equals(root) && !keys.contains(step.toKey())) {
                    keys.add(step.toKey());
                }
                if (!stepByPattern.containsKey(step.pattern())) {
                    stepByPattern.put(step.pattern(), step);
                }
            }
        }
        List<CycleStep> steps = new ArrayList<>(stepByPattern.values());
        if (steps.size() != keys.size()) {
            return null; // m ≠ n:欠定或仅有平凡解,回落逐环迭代
        }
        return solveSystem(keys, steps);
    }

    /**
     * 求解核心:对给定的键集与样板集建立系数矩阵,求平衡方程正整数零空间解、
     * 净率分类、前缀种子与多消费者键全批次种子.
     */
    @Nullable
    private static Analysis solveSystem(List<IAEItemStack> keys, List<CycleStep> steps) {
        int n = steps.size();
        if (n < 2 || keys.size() != n) {
            return null;
        }

        // 系数矩阵 coeff[step][key] = 该样板每份对该 key 的净产出(产出-消耗)
        BigInteger[][] coeff = new BigInteger[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                coeff[i][j] = BigInteger.ZERO;
            }
            ICraftingPatternDetails pattern = steps.get(i).pattern();
            for (IAEItemStack output : pattern.getCondensedOutputs()) {
                if (output == null) {
                    continue;
                }
                int keyIdx = keys.indexOf(RecursiveCraftingHelper.canon(output));
                if (keyIdx >= 0) {
                    coeff[i][keyIdx] = coeff[i][keyIdx].add(BigInteger.valueOf(output.getStackSize()));
                }
            }
            for (IAEItemStack input : pattern.getCondensedInputs()) {
                if (input == null) {
                    continue;
                }
                int keyIdx = keys.indexOf(RecursiveCraftingHelper.canon(input));
                if (keyIdx >= 0) {
                    coeff[i][keyIdx] = coeff[i][keyIdx].subtract(BigInteger.valueOf(input.getStackSize()));
                }
            }
        }

        // 平衡方程:对每个非 root 键 Σ coeff[step][key]×t[step] = 0.
        BigInteger[][] balance = new BigInteger[n - 1][n];
        for (int row = 0; row < n - 1; row++) {
            for (int j = 0; j < n; j++) {
                balance[row][j] = coeff[j][row + 1];
            }
        }
        BigInteger[] times = nullSpaceVector(balance, n);
        if (times == null) {
            return null;
        }

        // 净率分类:root 键每超轮净产出 = Σ coeff[step][0]×t[step]
        BigInteger netGain = BigInteger.ZERO;
        for (int i = 0; i < n; i++) {
            netGain = netGain.add(coeff[i][0].multiply(times[i]));
        }
        int cmp = netGain.compareTo(BigInteger.ZERO);
        RateClass rateClass = cmp > 0 ? RateClass.PRODUCTIVE : cmp == 0 ? RateClass.NEUTRAL : RateClass.DISSIPATIVE;

        // 各键种子:按执行顺序做超轮前缀分析,取各键余额最低点
        BigInteger[] balancePrefix = new BigInteger[n];
        BigInteger[] minPrefix = new BigInteger[n];
        for (int j = 0; j < n; j++) {
            balancePrefix[j] = BigInteger.ZERO;
            minPrefix[j] = BigInteger.ZERO;
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                balancePrefix[j] = balancePrefix[j].add(coeff[i][j].multiply(times[i]));
                if (balancePrefix[j].compareTo(minPrefix[j]) < 0) {
                    minPrefix[j] = balancePrefix[j];
                }
            }
        }

        // 多消费者键检测:某环键被 ≥2 个步骤消耗时,需要按每超轮总消耗记账
        int[] consumers = new int[n];
        BigInteger[] consumption = new BigInteger[n];
        for (int j = 0; j < n; j++) {
            consumption[j] = BigInteger.ZERO;
            for (int i = 0; i < n; i++) {
                if (coeff[i][j].signum() < 0) {
                    consumers[j]++;
                    consumption[j] = consumption[j].add(coeff[i][j].negate().multiply(times[i]));
                }
            }
        }

        try {
            long[] timesLong = new long[n];
            long[] seeds = new long[n];
            long[] batchSeeds = new long[n];
            for (int i = 0; i < n; i++) {
                timesLong[i] = times[i].longValueExact();
                seeds[i] = minPrefix[i].negate().max(BigInteger.ZERO).longValueExact();
                batchSeeds[i] = consumers[i] >= 2 ? consumption[i].longValueExact() : 0;
            }
            return new Analysis(Collections.unmodifiableList(new ArrayList<>(keys)),
                    Collections.unmodifiableList(new ArrayList<>(steps)), rateClass, timesLong,
                    netGain.longValueExact(), seeds, batchSeeds);
        } catch (ArithmeticException e) {
            return null; // 超出 long → 不接管
        }
    }

    /**
     * 求 (n-1)×n 整数矩阵的正整数零空间向量（广义叉积,Bareiss 精确行列式）.
     *
     * @return 已约分的正整数向量;秩不足或不存在全正解时返回 null.
     */
    @Nullable
    private static BigInteger[] nullSpaceVector(BigInteger[][] balance, int n) {
        BigInteger[] v = new BigInteger[n];
        boolean allZero = true;
        for (int j = 0; j < n; j++) {
            BigInteger[][] sub = new BigInteger[n - 1][n - 1];
            for (int r = 0; r < n - 1; r++) {
                int c = 0;
                for (int k = 0; k < n; k++) {
                    if (k != j) {
                        sub[r][c++] = balance[r][k];
                    }
                }
            }
            BigInteger det = determinant(sub, n - 1);
            v[j] = (j % 2 == 0) ? det : det.negate();
            if (!v[j].equals(BigInteger.ZERO)) {
                allZero = false;
            }
        }
        if (allZero) {
            return null; // 秩 < n-1,欠定 → 不接管
        }
        boolean anyPos = false;
        boolean anyNeg = false;
        for (BigInteger x : v) {
            anyPos |= x.signum() > 0;
            anyNeg |= x.signum() < 0;
        }
        if (anyNeg) {
            for (int j = 0; j < n; j++) {
                v[j] = v[j].negate();
            }
        }
        for (BigInteger x : v) {
            if (x.signum() <= 0) {
                return null;
            }
        }
        BigInteger gcd = v[0].abs();
        for (BigInteger x : v) {
            gcd = gcd.gcd(x.abs());
        }
        for (int j = 0; j < n; j++) {
            v[j] = v[j].divide(gcd);
        }
        return v;
    }

    /**
     * Bareiss 无分数高斯消元求精确行列式.
     */
    private static BigInteger determinant(BigInteger[][] matrix, int n) {
        if (n == 0) {
            return BigInteger.ONE;
        }
        BigInteger[][] m = new BigInteger[n][n];
        for (int i = 0; i < n; i++) {
            m[i] = matrix[i].clone();
        }
        BigInteger prevPivot = BigInteger.ONE;
        int sign = 1;
        for (int k = 0; k < n - 1; k++) {
            if (m[k][k].equals(BigInteger.ZERO)) {
                int swap = -1;
                for (int r = k + 1; r < n; r++) {
                    if (!m[r][k].equals(BigInteger.ZERO)) {
                        swap = r;
                        break;
                    }
                }
                if (swap < 0) {
                    return BigInteger.ZERO;
                }
                BigInteger[] tmp = m[k];
                m[k] = m[swap];
                m[swap] = tmp;
                sign = -sign;
            }
            for (int i = k + 1; i < n; i++) {
                for (int j = k + 1; j < n; j++) {
                    m[i][j] = m[i][j].multiply(m[k][k])
                            .subtract(m[i][k].multiply(m[k][j]))
                            .divide(prevPivot);
                }
            }
            prevPivot = m[k][k];
        }
        return sign > 0 ? m[n - 1][n - 1] : m[n - 1][n - 1].negate();
    }
}
