package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;

import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCpuLogicAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ExecutingCraftingJobAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.TaskProgressAccessor;

/**
 * 超轮配额调度器（执行层,解决多消费者键的全批次种子依赖）.
 * <p><b>问题</b>:环计划中某键被 ≥2 个 pattern 消耗时,CPU 贪婪推送可让先行的
 * 消费者一次性耗尽库存、其余消费者饿死.此前用"全批次种子"（库存 ≈ 下单量）
 * 兜底,巨型订单不可用.</p>
 * <p><b>方案</b>:对我们的虚拟 CPU 上的自消耗 job,限制每个闭包 pattern 的推送
 * 不超过"最慢闭包 pattern 进度 + 1 个超轮"的配额——先行消费者最多领先一轮,
 * 多消费者键的并发消耗被闸在每轮总消耗以内,库存要求降回每轮种子.</p>
 * <p><b>配额自恢复</b>:计划 patternTimes 总次数 = 轮次 × 超轮比（求解器构造上
 * 已约分）,对闭包内总次数求 GCD 即恢复轮次,无需标记传播;闭包 = 任务集中
 * "既消耗又产出"的键所触及的 pattern（外部子合成 pattern 自动豁免）.</p>
 * <p><b>只过滤不复制</b>:在原生 executeCrafting 的任务迭代入口过滤超配额
 * pattern,能量/waitingFor/容器返还等原生推送机制零改动.</p>
 * <p><b>已知限制</b>:NBT 恢复的 job 无配额快照,退化为原生推送（计算核心
 * 持久化阶段解决）.</p>
 */
public final class RoundQuotaScheduler {

    /**
     * 配额:闭包内各 pattern 每个超轮（相对 GCD）的执行次数.
     */
    public record Quota(Map<IPatternDetails, Long> perRound) {
    }

    /** job → 提交时的 patternTimes 快照（弱键,job 结束自动回收）. */
    private static final Map<ExecutingCraftingJob, Map<IPatternDetails, Long>> TOTALS = new WeakHashMap<>();

    /** job → 推导出的配额（随快照失效自动回收）. */
    private static final Map<ExecutingCraftingJob, Quota> QUOTAS = new WeakHashMap<>();

    private RoundQuotaScheduler() {
    }

    /**
     * 任务提交成功时快照 patternTimes（供后续配额推导）.
     */
    public static synchronized void snapshot(ExecutingCraftingJob job, ICraftingPlan plan) {
        if (plan != null && !plan.patternTimes().isEmpty()) {
            TOTALS.put(job, Map.copyOf(plan.patternTimes()));
            QUOTAS.remove(job);
        }
    }

    /**
     * 逐次推送否决（游戏适配层,每次推送前由 extractPatternInputs 的注入点调用）.
     * <p>超配额时返回 true,注入点令输入提取返回 null——原生视同"输入不足"自然
     * 跳过该 pattern（空容器 reinject 安全）,下一拍配额前进后自动恢复.</p>
     * 非我们的虚拟 CPU / 非自消耗 job / 闭包外 pattern 一律 false（零影响）;
     * NBT 恢复的任务以剩余量惰性重建配额（⑤）,限推不中断.
     */
    public static boolean shouldVetoPush(CraftingCpuLogic logic, IPatternDetails details) {
        var logicAcc = (CraftingCpuLogicAccessor) logic;
        var job = logicAcc.getJob();
        if (job == null || !VirtualCraftingCPURegistry.isOurVirtualCpu(logicAcc.getCluster())) {
            return false;
        }
        Map<IPatternDetails, Long> totals;
        synchronized (RoundQuotaScheduler.class) {
            totals = TOTALS.computeIfAbsent(job, j -> {
                // NBT 恢复(⑤):提交时快照丢失,以任务剩余量惰性重建——配额语义只依赖
                // 闭包内比例(GCD 约分),剩余量比例与原计划一致,恢复后限推不间断;
                // 某 pattern 剩余为 0 时 GCD 为 0 → 配额空,退化原生(安全)
                Map<IPatternDetails, Long> rebuilt = new LinkedHashMap<>();
                for (var entry : ((ExecutingCraftingJobAccessor) j).getTasks().entrySet()) {
                    rebuilt.put(entry.getKey(), ((TaskProgressAccessor) entry.getValue()).getValue());
                }
                return rebuilt;
            });
        }
        if (totals.isEmpty() || !totals.containsKey(details)) {
            return false;
        }
        Quota quota;
        synchronized (RoundQuotaScheduler.class) {
            quota = QUOTAS.computeIfAbsent(job,
                    j -> deriveQuota(totals, ((ExecutingCraftingJobAccessor) j).getFinalOutput().what()));
        }
        if (quota == null) {
            return false;
        }
        var tasks = ((ExecutingCraftingJobAccessor) job).getTasks();
        Map<IPatternDetails, Long> remaining = new LinkedHashMap<>();
        for (var entry : tasks.entrySet()) {
            remaining.put(entry.getKey(), ((TaskProgressAccessor) entry.getValue()).getValue());
        }
        return !isPushAllowed(quota, totals, remaining, details);
    }

    /**
     * 推导配额（纯函数）:任务集中"既消耗又产出"的键为候选闭包键;
     * 候选键必须**真的成环**(沿闭包内样板能从自身回到自身)才纳入——
     * 线性副产物复用(产出也被消耗但不成环)不调度,避免误伤死锁.
     * 自 1.1.0 起不再要求最终产出在闭包内:深层循环(DAG 边界)计划的
     * 最终产出是根物品,环在中间层,同样需要限推.
     *
     * @return 配额;无真环/无法推导时返回 null（调用方退化原生推送）.
     */
    @Nullable
    public static Quota deriveQuota(Map<IPatternDetails, Long> totals, AEKey finalOutputWhat) {
        Set<AEKey> produced = new HashSet<>();
        Set<AEKey> consumed = new HashSet<>();
        for (var pattern : totals.keySet()) {
            for (var output : pattern.getOutputs()) {
                produced.add(output.what());
            }
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length > 0) {
                    consumed.add(possible[0].what());
                }
            }
        }
        produced.retainAll(consumed);
        if (produced.isEmpty()) {
            return null; // 非自消耗 job
        }
        // 真环判定:候选键 K 成环 ⟺ 从消费 K 的样板出发,沿"样板→产出候选键→
        // 消费该键的样板"能回到产出 K 的样板(自增殖 = 单样板自环)
        Map<AEKey, List<IPatternDetails>> consumersOf = new LinkedHashMap<>();
        Map<AEKey, List<IPatternDetails>> producersOf = new LinkedHashMap<>();
        for (var pattern : totals.keySet()) {
            for (var output : pattern.getOutputs()) {
                if (produced.contains(output.what())) {
                    producersOf.computeIfAbsent(output.what(), k -> new ArrayList<>()).add(pattern);
                }
            }
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length > 0 && produced.contains(possible[0].what())) {
                    consumersOf.computeIfAbsent(possible[0].what(), k -> new ArrayList<>()).add(pattern);
                }
            }
        }
        Set<AEKey> cyclicKeys = new HashSet<>();
        for (var key : produced) {
            if (isCyclicKey(key, produced, consumersOf, producersOf)) {
                cyclicKeys.add(key);
            }
        }
        if (cyclicKeys.isEmpty()) {
            return null; // 线性副产物复用,不成环,不调度
        }
        Map<IPatternDetails, Long> closureTotals = new LinkedHashMap<>();
        long gcd = 0;
        for (var entry : totals.entrySet()) {
            if (touchesAny(entry.getKey(), cyclicKeys)) {
                closureTotals.put(entry.getKey(), entry.getValue());
                gcd = gcd(gcd, entry.getValue());
            }
        }
        if (closureTotals.isEmpty() || gcd <= 0) {
            return null;
        }
        Map<IPatternDetails, Long> perRound = new LinkedHashMap<>();
        for (var entry : closureTotals.entrySet()) {
            perRound.put(entry.getKey(), entry.getValue() / gcd);
        }
        return new Quota(perRound);
    }

    /**
     * 单次推送配额判定（纯函数）:闭包 pattern 的已推送量不得超过
     * （最慢闭包进度 + 1 超轮）;闭包外 pattern 不受限.
     */
    public static boolean isPushAllowed(Quota quota, Map<IPatternDetails, Long> totals,
            Map<IPatternDetails, Long> remaining, IPatternDetails pattern) {
        Long t = quota.perRound().get(pattern);
        if (t == null) {
            return true; // 闭包外:不限推
        }
        long round = Long.MAX_VALUE;
        for (var entry : quota.perRound().entrySet()) {
            long pushed = totals.getOrDefault(entry.getKey(), 0L) - remaining.getOrDefault(entry.getKey(), 0L);
            round = Math.min(round, pushed / entry.getValue());
        }
        if (round == Long.MAX_VALUE) {
            round = 0; // 闭包已全部完成,剩余任务自由推送
        }
        long pushed = totals.getOrDefault(pattern, 0L) - remaining.getOrDefault(pattern, 0L);
        long cap = t > Long.MAX_VALUE / (round + 1) ? Long.MAX_VALUE : t * (round + 1);
        return pushed < cap;
    }

    /**
     * 配额过滤（纯函数,测试辅助）:返回当前允许推送的 pattern 集合.
     */
    public static Set<IPatternDetails> filterPushable(Quota quota, Map<IPatternDetails, Long> totals,
            Map<IPatternDetails, Long> remaining) {
        var allowed = new LinkedHashSet<IPatternDetails>();
        for (var pattern : remaining.keySet()) {
            if (isPushAllowed(quota, totals, remaining, pattern)) {
                allowed.add(pattern);
            }
        }
        return allowed;
    }

    private static boolean touchesAny(IPatternDetails pattern, Set<AEKey> loopKeys) {
        for (var output : pattern.getOutputs()) {
            if (loopKeys.contains(output.what())) {
                return true;
            }
        }
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length > 0 && loopKeys.contains(possible[0].what())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 键 K 是否成环:从消费 K 的样板出发,沿"样板产出候选键 → 消费该键的样板"
     * 可达产出 K 的样板(自增殖样板一步即成环).
     */
    private static boolean isCyclicKey(AEKey key, Set<AEKey> candidates,
            Map<AEKey, List<IPatternDetails>> consumersOf, Map<AEKey, List<IPatternDetails>> producersOf) {
        var producers = producersOf.getOrDefault(key, List.of());
        Set<IPatternDetails> visited = new HashSet<>();
        var stack = new java.util.ArrayDeque<>(consumersOf.getOrDefault(key, List.of()));
        while (!stack.isEmpty()) {
            var pattern = stack.pop();
            if (!visited.add(pattern)) {
                continue;
            }
            if (producers.contains(pattern)) {
                return true;
            }
            for (var output : pattern.getOutputs()) {
                if (candidates.contains(output.what())) {
                    stack.addAll(consumersOf.getOrDefault(output.what(), List.of()));
                }
            }
        }
        return false;
    }

    private static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
