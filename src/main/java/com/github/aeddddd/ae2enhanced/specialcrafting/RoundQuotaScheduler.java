package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * 超轮配额调度器（执行层,解决多消费者键的全批次种子依赖,移植自 1.20.1）.
 * <p><b>问题</b>:环计划中某键被 ≥2 个 pattern 消耗时,CPU 贪婪推送可让先行的
 * 消费者一次性耗尽库存、其余消费者饿死.</p>
 * <p><b>方案</b>:对被标记为特殊 job 的 CPU 集群,限制每个闭包 pattern 的推送
 * 不超过"最慢闭包 pattern 进度 + 1 个超轮"的配额——先行消费者最多领先一轮,
 * 多消费者键的并发消耗被闸在每轮总消耗以内,库存要求降回每轮种子.</p>
 * <p><b>配额自恢复</b>:计划 tasks 总次数 = 轮次 × 超轮比,对闭包内总次数求 GCD
 * 即恢复轮次;GCD=1(LP 最小执行计数互质)时退化为闭包最小总次数作轮数
 * (超轮比 ≈1,锁步推进);闭包 = 任务集中"既消耗又产出"的键所触及的
 * pattern（外部子合成 pattern 自动豁免）.</p>
 * <p><b>NBT 恢复</b>:配额快照随集群 NBT 持久化(见 MixinCraftingCPUClusterSpecial),
 * 重启/集群重组后按 pattern ItemStack 匹配重建;快照缺失时退化为原生推送.</p>
 */
public final class RoundQuotaScheduler {

    /**
     * 配额:闭包内各 pattern 每个超轮（相对 GCD）的执行次数.
     */
    public static final class Quota {
        public final Map<ICraftingPatternDetails, Long> perRound;

        Quota(Map<ICraftingPatternDetails, Long> perRound) {
            this.perRound = perRound;
        }
    }

    /** 无配额标记（非自消耗 job:所有 pattern 自由推送）. */
    private static final Quota NO_QUOTA = new Quota(Collections.emptyMap());

    /** cluster → 提交时的 tasks 次数快照（弱键,集群回收自动清理）. */
    private static final Map<CraftingCPUCluster, Map<ICraftingPatternDetails, Long>> TOTALS = Collections
            .synchronizedMap(new WeakHashMap<>());

    /** cluster → 推导出的配额（随快照失效自动回收）. */
    private static final Map<CraftingCPUCluster, Quota> QUOTAS = Collections
            .synchronizedMap(new WeakHashMap<>());

    private RoundQuotaScheduler() {
    }

    /**
     * 任务提交成功时快照 tasks 次数（供后续配额推导）.
     */
    public static void snapshot(CraftingCPUCluster cluster, Map<ICraftingPatternDetails, Long> totals) {
        if (totals == null || totals.isEmpty()) {
            return;
        }
        // 剔除 value <= 0 的幽灵条目（多备选 pattern 分支中未被使用的样板，
        // AE2 原生 isBusy() 同样剔除），否则推导配额时会出现 0 配额导致除零崩溃
        Map<ICraftingPatternDetails, Long> cleaned = new LinkedHashMap<>();
        for (Map.Entry<ICraftingPatternDetails, Long> entry : totals.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                cleaned.put(entry.getKey(), entry.getValue());
            }
        }
        if (cleaned.isEmpty()) {
            return;
        }
        TOTALS.put(cluster, Collections.unmodifiableMap(cleaned));
        QUOTAS.remove(cluster);
    }

    /**
     * 提交时的 tasks 总次数快照（NBT 持久化用;无快照返回 null）.
     */
    @Nullable
    public static Map<ICraftingPatternDetails, Long> totalsOf(CraftingCPUCluster cluster) {
        return TOTALS.get(cluster);
    }

    /**
     * job 完成/取消时清理快照与配额.
     */
    public static void clear(CraftingCPUCluster cluster) {
        TOTALS.remove(cluster);
        QUOTAS.remove(cluster);
    }

    /**
     * 逐次推送否决（每次推送前由 executeCrafting 内 canCraft 调用的包装点调用）.
     * <p>超配额时返回 true,包装点令 canCraft 返回 false——原生视同"输入不足"自然
     * 跳过该 pattern,下一拍配额前进后自动恢复.</p>
     * 非特殊集群 / 无快照 / 非自消耗 job / 闭包外 pattern 一律 false（零影响）.
     */
    public static boolean shouldVetoPush(CraftingCPUCluster cluster, ICraftingPatternDetails details,
            Map<ICraftingPatternDetails, Long> remaining, IAEItemStack finalOutput) {
        if (!SpecialCraftingRuntime.isSpecialCluster(cluster) || finalOutput == null) {
            return false;
        }
        Map<ICraftingPatternDetails, Long> totals = TOTALS.get(cluster);
        if (totals == null || !totals.containsKey(details)) {
            return false; // NBT 恢复任务:退化原生推送
        }
        Quota quota = QUOTAS.get(cluster);
        if (quota == null) {
            quota = deriveQuota(totals, finalOutput);
            QUOTAS.put(cluster, quota == null ? NO_QUOTA : quota);
            quota = quota == null ? NO_QUOTA : quota;
        }
        if (quota == NO_QUOTA) {
            return false;
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
    public static Quota deriveQuota(Map<ICraftingPatternDetails, Long> totals, IAEItemStack finalOutputWhat) {
        Set<IAEItemStack> produced = new HashSet<>();
        Set<IAEItemStack> consumed = new HashSet<>();
        for (ICraftingPatternDetails pattern : totals.keySet()) {
            for (IAEItemStack output : pattern.getCondensedOutputs()) {
                if (output != null) {
                    produced.add(RecursiveCraftingHelper.canon(output));
                }
            }
            for (IAEItemStack input : pattern.getCondensedInputs()) {
                if (input != null) {
                    consumed.add(RecursiveCraftingHelper.canon(input));
                }
            }
        }
        produced.retainAll(consumed);
        if (produced.isEmpty()) {
            return null; // 非自消耗 job
        }
        // 真环判定:候选键 K 成环 ⟺ 从消费 K 的样板出发,沿"样板→产出候选键→
        // 消费该键的样板"能回到产出 K 的样板(自增殖 = 单样板自环)
        Map<IAEItemStack, java.util.List<ICraftingPatternDetails>> consumersOf = new LinkedHashMap<>();
        Map<IAEItemStack, java.util.List<ICraftingPatternDetails>> producersOf = new LinkedHashMap<>();
        for (ICraftingPatternDetails pattern : totals.keySet()) {
            for (IAEItemStack output : pattern.getCondensedOutputs()) {
                if (output != null && produced.contains(RecursiveCraftingHelper.canon(output))) {
                    producersOf.computeIfAbsent(RecursiveCraftingHelper.canon(output),
                            k -> new java.util.ArrayList<>()).add(pattern);
                }
            }
            for (IAEItemStack input : pattern.getCondensedInputs()) {
                if (input != null && produced.contains(RecursiveCraftingHelper.canon(input))) {
                    consumersOf.computeIfAbsent(RecursiveCraftingHelper.canon(input),
                            k -> new java.util.ArrayList<>()).add(pattern);
                }
            }
        }
        Set<IAEItemStack> cyclicKeys = new HashSet<>();
        for (IAEItemStack key : produced) {
            if (isCyclicKey(key, produced, consumersOf, producersOf)) {
                cyclicKeys.add(key);
            }
        }
        if (cyclicKeys.isEmpty()) {
            return null; // 线性副产物复用,不成环,不调度
        }
        Map<ICraftingPatternDetails, Long> closureTotals = new LinkedHashMap<>();
        long gcd = 0;
        for (Map.Entry<ICraftingPatternDetails, Long> entry : totals.entrySet()) {
            if (touchesAny(entry.getKey(), cyclicKeys)) {
                closureTotals.put(entry.getKey(), entry.getValue());
                gcd = gcd(gcd, entry.getValue());
            }
        }
        if (closureTotals.isEmpty() || gcd <= 0) {
            return null;
        }
        // 轮数恢复:GCD>1 时按 GCD 约分;GCD=1(LP 最小执行计数常互质,如 49/49/48)
        // 退化为闭包最小总次数——超轮比 ≈1,锁步推进(±1 次)防先行消费者吃光
        // 共享种子;若沿用 GCD=1 则超轮 = 整个任务,闸门形同虚设
        long rounds = gcd;
        if (rounds == 1) {
            rounds = Long.MAX_VALUE;
            for (long total : closureTotals.values()) {
                rounds = Math.min(rounds, total);
            }
        }
        Map<ICraftingPatternDetails, Long> perRound = new LinkedHashMap<>();
        for (Map.Entry<ICraftingPatternDetails, Long> entry : closureTotals.entrySet()) {
            perRound.put(entry.getKey(), Math.max(1L, entry.getValue() / rounds));
        }
        return new Quota(perRound);
    }

    /**
     * 键 K 是否成环:从消费 K 的样板出发,沿"样板产出候选键 → 消费该键的样板"
     * 可达产出 K 的样板(自增殖样板一步即成环).
     */
    private static boolean isCyclicKey(IAEItemStack key, Set<IAEItemStack> candidates,
            Map<IAEItemStack, java.util.List<ICraftingPatternDetails>> consumersOf,
            Map<IAEItemStack, java.util.List<ICraftingPatternDetails>> producersOf) {
        java.util.List<ICraftingPatternDetails> producers = producersOf.getOrDefault(key,
                java.util.Collections.emptyList());
        Set<ICraftingPatternDetails> visited = new HashSet<>();
        java.util.ArrayDeque<ICraftingPatternDetails> stack = new java.util.ArrayDeque<>(
                consumersOf.getOrDefault(key, java.util.Collections.emptyList()));
        while (!stack.isEmpty()) {
            ICraftingPatternDetails pattern = stack.pop();
            if (!visited.add(pattern)) {
                continue;
            }
            if (producers.contains(pattern)) {
                return true;
            }
            for (IAEItemStack output : pattern.getCondensedOutputs()) {
                if (output != null && candidates.contains(RecursiveCraftingHelper.canon(output))) {
                    stack.addAll(consumersOf.getOrDefault(RecursiveCraftingHelper.canon(output),
                            java.util.Collections.emptyList()));
                }
            }
        }
        return false;
    }

    /**
     * 单次推送配额判定（纯函数）:闭包 pattern 的已推送量不得超过
     * （最慢闭包进度 + 1 超轮）;闭包外 pattern 不受限.
     */
    public static boolean isPushAllowed(Quota quota, Map<ICraftingPatternDetails, Long> totals,
            Map<ICraftingPatternDetails, Long> remaining, ICraftingPatternDetails pattern) {
        Long t = quota.perRound.get(pattern);
        if (t == null) {
            return true; // 闭包外:不限推
        }
        long round = Long.MAX_VALUE;
        for (Map.Entry<ICraftingPatternDetails, Long> entry : quota.perRound.entrySet()) {
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
     * 配额过滤(测试与诊断用):从 totals 键集中筛出当前允许推送的 pattern.
     */
    public static java.util.List<ICraftingPatternDetails> filterPushable(Quota quota,
            Map<ICraftingPatternDetails, Long> totals, Map<ICraftingPatternDetails, Long> remaining) {
        java.util.List<ICraftingPatternDetails> allowed = new java.util.ArrayList<>();
        for (ICraftingPatternDetails pattern : totals.keySet()) {
            if (isPushAllowed(quota, totals, remaining, pattern)) {
                allowed.add(pattern);
            }
        }
        return allowed;
    }

    private static boolean touchesAny(ICraftingPatternDetails pattern, Set<IAEItemStack> loopKeys) {
        for (IAEItemStack output : pattern.getCondensedOutputs()) {
            if (output != null && loopKeys.contains(RecursiveCraftingHelper.canon(output))) {
                return true;
            }
        }
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input != null && loopKeys.contains(RecursiveCraftingHelper.canon(input))) {
                return true;
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
