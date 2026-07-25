package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;

/**
 * 跨样板循环链分析器（阶段 2）.
 * <p>在"物品↔样板"依赖图中检测经过请求物品的<b>简单环</b>（每个环内样板只消耗
 * 一种环内物品、只产出一种环内物品,如 A→2B,B→A）,并用精确有理数（BigInteger）
 * 计算环的净乘积率,分类为增殖环（>1)/中性环（=1)/耗散环（<1).
 * 非简单环（样板引用多个环内物品）与超出 long 的速率一律返回 null,
 * 由调用方回落原生行为（原生对环剪枝,快速失败,无回归风险）.</p>
 */
public final class CycleAnalyzer {

    /**
     * 环净乘积率分类.
     */
    public enum RateClass {
        /** 每轮净产出为正,可增殖（如 A→2B,B→A:1A 经一轮变 2A）. */
        PRODUCTIVE,
        /** 进出相等（催化剂环）,阶段 2 不接管. */
        NEUTRAL,
        /** 净产出为负（耗散环）,阶段 2 不接管. */
        DISSIPATIVE
    }

    /**
     * 环的一步:{@code pattern} 消耗 {@code inPer} 个 {@code fromKey},产出 {@code outPer} 个 {@code toKey}.
     */
    public record CycleStep(IPatternDetails pattern, AEKey fromKey, long inPer, AEKey toKey, long outPer) {
    }

    /**
     * 环分析结果.
     *
     * @param steps 按正向（消耗→产出）排列的环步骤,末步 toKey = 首步 fromKey
     * @param rateClass 净乘积率分类
     * @param timesPerRound 每个"超轮"各样板的执行次数（速率分数通分为整数）
     * @param netGain 每个超轮净产出的请求物数量
     * @param seed 启动一个超轮所需的请求物种子数量
     */
    public record Analysis(List<CycleStep> steps, RateClass rateClass, long[] timesPerRound,
            long netGain, long seed) {
    }

    /** detector/求解共用的遍历预算,避免超大网络下 DFS 失控. */
    private static final int MAX_VISITED = 512;

    private CycleAnalyzer() {
    }

    /**
     * 寻找经过 {@code root} 的简单环（长度 ≥ 2;自引用环由阶段 1 处理,此处跳过）.
     *
     * @return 正向步骤列表,未找到返回 null.
     */
    @Nullable
    public static List<CycleStep> findCycle(ICraftingService craftingService, AEKey root) {
        int[] budget = { MAX_VISITED };
        Set<AEKey> onPath = new HashSet<>();
        onPath.add(root);
        // 从 root 到当前节点的"产生链",按插入序保持正向顺序
        LinkedHashMap<AEKey, CycleStep> chain = new LinkedHashMap<>();
        return dfs(craftingService, root, root, onPath, chain, budget);
    }

    /**
     * 沿"被产生"边回溯 DFS:current 由某 pattern 产生,其主输入 from 即反向边.
     * 当 from == root 时闭合为环.
     */
    @Nullable
    private static List<CycleStep> dfs(ICraftingService craftingService, AEKey root, AEKey current,
            Set<AEKey> onPath, LinkedHashMap<AEKey, CycleStep> chain, int[] budget) {
        if (budget[0]-- <= 0) {
            return null;
        }
        for (var pattern : craftingService.getCraftingFor(current)) {
            var primaryOut = pattern.getPrimaryOutput();
            if (primaryOut == null || !current.matches(primaryOut) || primaryOut.amount() <= 0) {
                continue;
            }
            long outPer = primaryOut.amount();
            for (var input : pattern.getInputs()) {
                var primaryIn = input.getPossibleInputs()[0];
                long inPer = primaryIn.amount() * input.getMultiplier();
                if (inPer <= 0) {
                    continue;
                }
                AEKey from = primaryIn.what();
                if (from.equals(current)) {
                    continue; // 自引用交给阶段 1
                }
                CycleStep step = new CycleStep(pattern, from, inPer, current, outPer);
                if (from.equals(root)) {
                    List<CycleStep> steps = new ArrayList<>();
                    steps.add(step);
                    // chain 按"从 root 回溯发现"的插入序排列,反向后才是正向环序
                    var chainSteps = new ArrayList<>(chain.values());
                    java.util.Collections.reverse(chainSteps);
                    steps.addAll(chainSteps);
                    return steps;
                }
                if (onPath.contains(from)) {
                    continue; // 只接受经过 root 的简单环
                }
                onPath.add(from);
                chain.put(from, step);
                var found = dfs(craftingService, root, from, onPath, chain, budget);
                if (found != null) {
                    return found;
                }
                chain.remove(from);
                onPath.remove(from);
            }
        }
        return null;
    }

    /**
     * 分析简单环:闭合性/非简单校验 + 净乘积率分类 + 超轮缩放.
     *
     * @return 分析结果;非简单环或数值超出 long 时返回 null.
     */
    @Nullable
    public static Analysis analyze(List<CycleStep> steps) {
        if (steps == null || steps.size() < 2) {
            return null;
        }
        int n = steps.size();
        // 闭合性校验
        for (int i = 0; i < n; i++) {
            if (!steps.get(i).toKey().equals(steps.get((i + 1) % n).fromKey())) {
                return null;
            }
        }
        // 非简单判定:任一样板的输入/输出引用环内其他 key → 不接管
        Set<AEKey> cycleKeys = new HashSet<>();
        for (var step : steps) {
            cycleKeys.add(step.fromKey());
        }
        for (var step : steps) {
            for (var input : step.pattern().getInputs()) {
                AEKey k = input.getPossibleInputs()[0].what();
                if (cycleKeys.contains(k) && !k.equals(step.fromKey())) {
                    return null;
                }
            }
            for (var output : step.pattern().getOutputs()) {
                if (cycleKeys.contains(output.what()) && !output.what().equals(step.toKey())) {
                    return null;
                }
            }
        }

        // 速率分数:rs[0] = 1;rs[i] = rs[i-1] × outPer[i-1] / inPer[i](每轮各样板执行次数)
        BigInteger[] num = new BigInteger[n];
        BigInteger[] den = new BigInteger[n];
        num[0] = BigInteger.ONE;
        den[0] = BigInteger.ONE;
        for (int i = 1; i < n; i++) {
            BigInteger rn = num[i - 1].multiply(BigInteger.valueOf(steps.get(i - 1).outPer()));
            BigInteger rd = den[i - 1].multiply(BigInteger.valueOf(steps.get(i).inPer()));
            BigInteger gcd = rn.gcd(rd);
            num[i] = rn.divide(gcd);
            den[i] = rd.divide(gcd);
        }
        // 每轮 X 净率 = rs[n-1] × outPer[n-1] / inPer[0]
        BigInteger produced = num[n - 1].multiply(BigInteger.valueOf(steps.get(n - 1).outPer()));
        BigInteger consumed = den[n - 1].multiply(BigInteger.valueOf(steps.get(0).inPer()));
        int cmp = produced.compareTo(consumed);
        RateClass rateClass = cmp > 0 ? RateClass.PRODUCTIVE : cmp == 0 ? RateClass.NEUTRAL : RateClass.DISSIPATIVE;

        // 超轮缩放:分母最小公倍数
        BigInteger m = BigInteger.ONE;
        for (int i = 0; i < n; i++) {
            m = m.multiply(den[i]).divide(m.gcd(den[i]));
        }
        long[] times = new long[n];
        try {
            for (int i = 0; i < n; i++) {
                times[i] = num[i].multiply(m).divide(den[i]).longValueExact();
            }
            long netGain = produced.multiply(m).divide(den[n - 1])
                    .subtract(BigInteger.valueOf(times[0]).multiply(BigInteger.valueOf(steps.get(0).inPer())))
                    .longValueExact();
            long seed = BigInteger.valueOf(times[0]).multiply(BigInteger.valueOf(steps.get(0).inPer()))
                    .longValueExact();
            if (netGain <= 0 && rateClass == RateClass.PRODUCTIVE) {
                return null; // 理论不可达,防御
            }
            return new Analysis(List.copyOf(steps), rateClass, times, netGain, seed);
        } catch (ArithmeticException e) {
            return null; // 超出 long → 不接管
        }
    }
}
