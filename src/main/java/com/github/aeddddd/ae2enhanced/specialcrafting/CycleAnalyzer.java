package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;

/**
 * 跨样板循环链分析器（阶段 2,泛化版）.
 * <p>枚举经过请求物品的<b>简单环</b>（按键集升序尝试,长环优先）,对每个环键集
 * 建立"样板×物品"精确系数矩阵（样板可消耗/产出任意多种环内物品,如
 * {@code 16A+16B+W→64C} 同时消耗 A 与 B）,以广义叉积（Bareiss 精确行列式）
 * 求平衡方程组的正整数零空间向量,得到各样板执行次数比与环净乘积率分类
 * （增殖/中性/耗散）;再以超轮前缀分析求各环内物品的启动种子.</p>
 * <p>秩不足/无正整数解/数值超 long 时返回 null,由调用方回落原生行为
 * （原生对环剪枝,快速失败,无回归风险）.</p>
 */
public final class CycleAnalyzer {

    /**
     * 环净乘积率分类.
     */
    public enum RateClass {
        /** 每轮净产出为正,可增殖（如 1A 经一轮变 2A）. */
        PRODUCTIVE,
        /** 进出相等（中性环）,不接管. */
        NEUTRAL,
        /** 净产出为负（耗散环）,不接管. */
        DISSIPATIVE
    }

    /**
     * 环的一步:{@code pattern} 将 {@code fromKey}（路径视角的主输入）转化为 {@code toKey}.
     * 实际消耗/产出的环内物品集合以样板全量输入输出为准（系数矩阵在 analyze 中构建）.
     */
    public record CycleStep(IPatternDetails pattern, AEKey fromKey, AEKey toKey) {
    }

    /**
     * 环分析结果.
     *
     * @param keys 环内物品列表,keys[0] 为请求物（root）
     * @param steps 按执行顺序排列的环步骤（= 找到的回溯环的正向序）
     * @param rateClass 净乘积率分类
     * @param timesPerRound 每个超轮各样板的执行次数（正整数,已约分）
     * @param netGain 每个超轮净产出的请求物数量
     * @param seedsPerKey 各环内物品的启动种子（与 keys 对齐,启动一个超轮的最小前置需求）
     * @param batchSeedPerKey 多消费者键的全批次保守种子（每超轮总消耗量,与 keys 对齐;
     * 单消费者键为 0）——某环键被 ≥2 个步骤消耗时,CPU 贪婪推送可能让先行的消费者
     * 一次性耗尽该键、饿死其余消费者（运行时无贷款兜底,会永久死锁）,此类键的种子
     * 必须覆盖整批消耗才对任意推送顺序安全
     */
    public record Analysis(List<AEKey> keys, List<CycleStep> steps, RateClass rateClass,
            long[] timesPerRound, long netGain, long[] seedsPerKey, long[] batchSeedPerKey) {
    }

    /** detector/求解共用的遍历预算,避免超大网络下 DFS 失控. */
    private static final int MAX_VISITED = 512;
    /** 单次请求最多枚举的候选环数量,防止复杂网络下指数爆炸. */
    private static final int MAX_CYCLES = 64;

    private CycleAnalyzer() {
    }

    /**
     * 枚举经过 {@code root} 的所有简单环（长度 ≥ 2;自引用环由阶段 1 处理,此处跳过）,
     * 按环长度降序返回（长环的键集更完整,优先尝试）.
     * <p>生产者发现含<b>副产物边</b>(1.1.0 起):root 作为样板的任意输出(不限主产出)
     * 都视为被该样板生产——否则经副产物闭合的催化环(如 1A→1X+1B、1B→1A)不可见.</p>
     */
    public static List<List<CycleStep>> findCyclesThrough(ICraftingService craftingService, AEKey root) {
        int[] budget = { MAX_VISITED };
        List<List<CycleStep>> cycles = new ArrayList<>();
        Set<AEKey> onPath = new HashSet<>();
        onPath.add(root);
        LinkedHashMap<AEKey, CycleStep> chain = new LinkedHashMap<>();
        Map<AEKey, List<IPatternDetails>> producerCache = new LinkedHashMap<>();
        dfs(craftingService, root, root, onPath, chain, budget, cycles, producerCache);
        cycles.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return cycles;
    }

    /**
     * 便捷方法:返回找到的第一个（最长）环,无环返回 null.
     */
    @Nullable
    public static List<CycleStep> findCycle(ICraftingService craftingService, AEKey root) {
        var cycles = findCyclesThrough(craftingService, root);
        return cycles.isEmpty() ? null : cycles.get(0);
    }

    /**
     * 生产 {@code current} 的全部样板:主产出索引快路径 + 副产物全扫描(按请求缓存).
     */
    private static List<IPatternDetails> producersOf(ICraftingService craftingService, AEKey current,
            Map<AEKey, List<IPatternDetails>> cache) {
        return cache.computeIfAbsent(current, key -> {
            List<IPatternDetails> out = new ArrayList<>();
            for (var pattern : craftingService.getCraftingFor(key)) {
                out.add(pattern);
            }
            // 副产物生产者:getCraftingFor 只按主产出索引,需全样板扫描补漏
            for (var craftable : craftingService.getCraftables(k -> true)) {
                if (craftable.equals(key)) {
                    continue;
                }
                for (var pattern : craftingService.getCraftingFor(craftable)) {
                    for (var output : pattern.getOutputs()) {
                        if (key.equals(output.what())) {
                            out.add(pattern);
                            break;
                        }
                    }
                }
            }
            return out;
        });
    }

    /**
     * 沿"被产生"边回溯 DFS:current 由某 pattern 产生,其主输入 from 即反向边.
     * from == root 时闭合为环并记录（继续搜索其他环）.
     */
    private static void dfs(ICraftingService craftingService, AEKey root, AEKey current,
            Set<AEKey> onPath, LinkedHashMap<AEKey, CycleStep> chain, int[] budget,
            List<List<CycleStep>> cycles, Map<AEKey, List<IPatternDetails>> producerCache) {
        if (budget[0]-- <= 0 || cycles.size() >= MAX_CYCLES) {
            return;
        }
        for (var pattern : producersOf(craftingService, current, producerCache)) {
            boolean producesCurrent = false;
            for (var output : pattern.getOutputs()) {
                if (current.matches(output) && output.amount() > 0) {
                    producesCurrent = true;
                    break;
                }
            }
            if (!producesCurrent) {
                continue;
            }
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length == 0) {
                    continue; // 真实游戏中可能出现空候选输入(模糊匹配无成员等)
                }
                var primaryIn = possible[0];
                if (primaryIn.amount() <= 0) {
                    continue;
                }
                AEKey from = primaryIn.what();
                if (from.equals(current)) {
                    continue; // 自引用交给阶段 1
                }
                CycleStep step = new CycleStep(pattern, from, current);
                if (from.equals(root)) {
                    List<CycleStep> steps = new ArrayList<>();
                    steps.add(step);
                    // chain 按"从 root 回溯发现"的插入序排列,反向后才是正向环序
                    var chainSteps = new ArrayList<>(chain.values());
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
                dfs(craftingService, root, from, onPath, chain, budget, cycles, producerCache);
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
    public static boolean isCycleStep(ICraftingService craftingService, IPatternDetails pattern) {
        Set<AEKey> outputs = new HashSet<>();
        for (var output : pattern.getOutputs()) {
            outputs.add(output.what());
        }
        int[] budget = { MAX_VISITED };
        Map<AEKey, List<IPatternDetails>> producerCache = new LinkedHashMap<>();
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length == 0) {
                continue;
            }
            if (reachesOutputs(craftingService, possible[0].what(), outputs, budget, new HashSet<>(),
                    producerCache)) {
                return true;
            }
        }
        return false;
    }

    /** 沿"被产生"边回溯:current 的传递原料中是否出现 targets 中的键. */
    private static boolean reachesOutputs(ICraftingService craftingService, AEKey current,
            Set<AEKey> targets, int[] budget, Set<AEKey> visited,
            Map<AEKey, List<IPatternDetails>> producerCache) {
        if (!visited.add(current) || budget[0]-- <= 0) {
            return false;
        }
        for (var producer : producersOf(craftingService, current, producerCache)) {
            boolean produces = false;
            for (var output : producer.getOutputs()) {
                if (current.matches(output)) {
                    produces = true;
                    break;
                }
            }
            if (!produces) {
                continue;
            }
            for (var input : producer.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length == 0) {
                    continue;
                }
                var from = possible[0].what();
                if (targets.contains(from)) {
                    return true;
                }
                if (reachesOutputs(craftingService, from, targets, budget, visited, producerCache)) {
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
    public static List<List<CycleStep>> findCatalyticCycles(ICraftingService craftingService,
            AEKey what) {
        List<List<CycleStep>> out = new ArrayList<>();
        Set<List<IPatternDetails>> seen = new HashSet<>();
        for (var pattern : craftingService.getCraftingFor(what)) {
            boolean selfInput = false;
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length > 0 && what.equals(possible[0].what())) {
                    selfInput = true;
                    break;
                }
            }
            if (selfInput) {
                continue;
            }
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length == 0) {
                    continue;
                }
                for (var cycle : findCyclesThrough(craftingService, possible[0].what())) {
                    boolean contains = false;
                    List<IPatternDetails> signature = new ArrayList<>();
                    for (var step : cycle) {
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
    public static long byproductPerRound(Analysis analysis, AEKey what) {
        if (analysis.keys().contains(what)) {
            return 0;
        }
        long perRound = 0;
        var times = analysis.timesPerRound();
        for (int i = 0; i < analysis.steps().size(); i++) {
            for (var output : analysis.steps().get(i).pattern().getOutputs()) {
                if (what.equals(output.what())) {
                    perRound += output.amount() * times[i];
                }
            }
        }
        return perRound;
    }

    /**
     * 分析简单环:闭合性校验后委托 {@link #solveSystem}.
     *
     * @return 分析结果;闭合性错误/秩不足/无正整数解/数值超 long 时返回 null.
     */
    @Nullable
    public static Analysis analyze(List<CycleStep> cycle) {
        if (cycle == null || cycle.size() < 2) {
            return null;
        }
        int n = cycle.size();
        // 闭合性校验 + 键集（首步 fromKey 为 root）
        List<AEKey> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (!cycle.get(i).toKey().equals(cycle.get((i + 1) % n).fromKey())) {
                return null;
            }
            keys.add(cycle.get(i).fromKey());
        }
        return solveSystem(keys, cycle);
    }

    /**
     * 候选环并集分析（θ 形共享结构,如赛特斯石英循环:水晶→粉、水晶→充能、
     * 粉+充能→水晶——"粉+充能→水晶"被两个两键环共享,逐环分析会把另一中间物
     * 当环外输入而双双失败）.
     * <p>取所有候选环的键集与样板（按样板去重）的并集;当 样板数 == 键数 时构成
     * 适定方程组,与单环同一套零空间求解;否则（欠定/仅共享 root 的无关环并集）
     * 返回 null,由调用方回落逐环迭代.</p>
     */
    @Nullable
    public static Analysis analyzeUnion(List<List<CycleStep>> cycles) {
        if (cycles == null || cycles.size() < 2) {
            return null;
        }
        AEKey root = cycles.get(0).get(0).fromKey();
        List<AEKey> keys = new ArrayList<>();
        keys.add(root);
        Map<IPatternDetails, CycleStep> stepByPattern = new LinkedHashMap<>();
        for (var cycle : cycles) {
            for (var step : cycle) {
                if (!step.fromKey().equals(root) && !keys.contains(step.fromKey())) {
                    keys.add(step.fromKey());
                }
                if (!step.toKey().equals(root) && !keys.contains(step.toKey())) {
                    keys.add(step.toKey());
                }
                stepByPattern.putIfAbsent(step.pattern(), step);
            }
        }
        List<CycleStep> steps = new ArrayList<>(stepByPattern.values());
        if (steps.size() != keys.size()) {
            return null; // m ≠ n:欠定(LP 领域)或仅有平凡解,回落逐环迭代
        }
        return solveSystem(keys, steps);
    }

    /**
     * 求解核心:对给定的键集与样板集建立系数矩阵,求平衡方程正整数零空间解、
     * 净率分类、前缀种子与多消费者键全批次种子.
     */
    @Nullable
    private static Analysis solveSystem(List<AEKey> keys, List<CycleStep> steps) {
        int n = steps.size();
        if (n < 2 || keys.size() != n) {
            return null;
        }

        // 系数矩阵 coeff[step][key] = 该样板每份对该 key 的净产出(产出-消耗,精确 key 相等)
        BigInteger[][] coeff = new BigInteger[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                coeff[i][j] = BigInteger.ZERO;
            }
            var pattern = steps.get(i).pattern();
            for (var output : pattern.getOutputs()) {
                int keyIdx = keys.indexOf(output.what());
                if (keyIdx >= 0) {
                    coeff[i][keyIdx] = coeff[i][keyIdx].add(BigInteger.valueOf(output.amount()));
                }
            }
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length == 0) {
                    continue;
                }
                var primaryIn = possible[0];
                int keyIdx = keys.indexOf(primaryIn.what());
                if (keyIdx >= 0) {
                    coeff[i][keyIdx] = coeff[i][keyIdx]
                            .subtract(BigInteger.valueOf(primaryIn.amount() * input.getMultiplier()));
                }
            }
        }

        // 平衡方程:对每个非 root 键 Σ coeff[step][key]×t[step] = 0.
        // (n-1)×n 矩阵的零空间向量由广义叉积给出:t[j] = (-1)^j × det(删第 j 列的子矩阵).
        BigInteger[][] balance = new BigInteger[n - 1][n];
        for (int row = 0; row < n - 1; row++) {
            // 第 row 行对应 keys[row+1](非 root 键):取各样板对该键的系数
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

        // 多消费者键检测:某环键被 ≥2 个步骤消耗时,前缀种子+贷款只在计划期成立,
        // 运行时 CPU 贪婪推送可致先行的消费者耗尽该键、其余消费者永久饿死 →
        // 此类键必须以"每超轮总消耗 × 轮次"的全批次种子记账(对任意推送顺序安全).
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
            return new Analysis(List.copyOf(keys), List.copyOf(steps), rateClass, timesLong,
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
            // 删第 j 列的 (n-1)×(n-1) 子矩阵
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
        // 统一符号并要求全正(零分量意味着该样板不执行,环断裂)
        boolean anyPos = false;
        boolean anyNeg = false;
        for (var x : v) {
            anyPos |= x.signum() > 0;
            anyNeg |= x.signum() < 0;
        }
        if (anyNeg) {
            for (int j = 0; j < n; j++) {
                v[j] = v[j].negate();
            }
        }
        for (var x : v) {
            if (x.signum() <= 0) {
                return null;
            }
        }
        // gcd 约分
        BigInteger gcd = v[0].abs();
        for (var x : v) {
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
                // 行交换找非零主元
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
                var tmp = m[k];
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
