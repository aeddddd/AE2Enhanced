package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import appeng.api.storage.data.IAEItemStack;

/**
 * 特殊计划显示信息(服务端计算 → 客户端渲染).
 * <p>{@link #entries} 记录自增殖/循环链键的轮次与每轮产耗;{@link #callCounts}
 * 统计各主产出键的样板调用次数,客户端在合成确认界面显示"调用 N 次(约 R 轮发配)".</p>
 */
public final class SpecialPlanInfo {

    public static final int KIND_SELF_DUP = 1;
    public static final int KIND_CYCLE = 2;

    public static final SpecialPlanInfo EMPTY = new SpecialPlanInfo(new LinkedHashMap<>(), new LinkedHashMap<>());

    /**
     * @param kind 1=自增殖样板;2=循环链成员
     * @param rounds 循环链总轮次(自增殖恒 1)
     * @param perRoundProduce 每轮(自增殖:每次)产出量
     * @param perRoundConsume 每轮(自增殖:每次)消耗量
     * @param totalCrafts 自增殖总调用次数(循环链恒 0)
     * @param initialExtract 初始提取(种子)
     */
    public static final class Entry {
        public final int kind;
        public final long rounds;
        public final long perRoundProduce;
        public final long perRoundConsume;
        public final long totalCrafts;
        public final long initialExtract;

        public Entry(int kind, long rounds, long perRoundProduce, long perRoundConsume,
                long totalCrafts, long initialExtract) {
            this.kind = kind;
            this.rounds = rounds;
            this.perRoundProduce = perRoundProduce;
            this.perRoundConsume = perRoundConsume;
            this.totalCrafts = totalCrafts;
            this.initialExtract = initialExtract;
        }
    }

    public final Map<IAEItemStack, Entry> entries;
    public final Map<IAEItemStack, Long> callCounts;

    public SpecialPlanInfo(Map<IAEItemStack, Entry> entries, Map<IAEItemStack, Long> callCounts) {
        this.entries = entries;
        this.callCounts = callCounts;
    }

    public boolean isEmpty() {
        return entries.isEmpty() && callCounts.isEmpty();
    }

    @Nullable
    public Entry entryFor(IAEItemStack key) {
        for (Map.Entry<IAEItemStack, Entry> e : entries.entrySet()) {
            if (e.getKey().equals(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 该键作为主产出的样板调用总次数(无则 0). */
    public long callCountOf(IAEItemStack key) {
        for (Map.Entry<IAEItemStack, Long> e : callCounts.entrySet()) {
            if (e.getKey().equals(key)) {
                return e.getValue();
            }
        }
        return 0;
    }

    /**
     * 从合成树恢复显示信息(纯函数,服务端调用).
     * <p>1.12.2 无独立计划对象,树即计划:patternTimes 遍历树收集,usedItems 取
     * "有数量且非可请求"的条目.自增殖样板经 {@link RecursiveCraftingHelper#isNetPositiveSelfRef}
     * 识别;循环链(含催化环)复用 {@link RoundQuotaScheduler#deriveQuota} 的闭包 + GCD 轮次推导.</p>
     */
    public static SpecialPlanInfo compute(appeng.crafting.CraftingJob job) {
        Map<IAEItemStack, Entry> entries = new LinkedHashMap<>();
        IAEItemStack finalWhat = RecursiveCraftingHelper.canon(job.getOutput());
        Map<appeng.api.networking.crafting.ICraftingPatternDetails, Long> patternTimes = new LinkedHashMap<>();
        Map<IAEItemStack, Long> usedItems = new LinkedHashMap<>();
        collectFromTree(job.getTree(), patternTimes, usedItems);

        // 全计划通用:各主产出键的样板调用次数(普通计划也显示)
        Map<IAEItemStack, Long> callCounts = new LinkedHashMap<>();
        for (Map.Entry<appeng.api.networking.crafting.ICraftingPatternDetails, Long> e : patternTimes.entrySet()) {
            IAEItemStack primary = e.getKey().getPrimaryOutput();
            if (primary != null) {
                callCounts.merge(RecursiveCraftingHelper.canon(primary), e.getValue(), Long::sum);
            }
        }

        // 自增殖样板(与求解器阶段 1 一致,优先于循环链)
        for (Map.Entry<appeng.api.networking.crafting.ICraftingPatternDetails, Long> e : patternTimes.entrySet()) {
            appeng.api.networking.crafting.ICraftingPatternDetails pattern = e.getKey();
            if (RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, finalWhat)) {
                long inPer = RecursiveCraftingHelper.selfInputPerCraft(pattern, finalWhat);
                long outPer = RecursiveCraftingHelper.selfOutputPerCraft(pattern, finalWhat);
                entries.put(finalWhat.copy(), new Entry(KIND_SELF_DUP, 1, outPer, inPer, e.getValue(),
                        usedItems.getOrDefault(finalWhat, 0L)));
                return new SpecialPlanInfo(entries, callCounts);
            }
        }

        // 循环链(闭包 + GCD 轮次;催化环的环外副产物输出也会出现在 perRound 中)
        RoundQuotaScheduler.Quota quota = RoundQuotaScheduler.deriveQuota(patternTimes, finalWhat);
        if (quota == null) {
            return new SpecialPlanInfo(entries, callCounts);
        }
        long rounds = 0;
        for (Map.Entry<appeng.api.networking.crafting.ICraftingPatternDetails, Long> e : quota.perRound.entrySet()) {
            rounds = patternTimes.get(e.getKey()) / e.getValue();
            break;
        }
        Map<IAEItemStack, long[]> perRound = new LinkedHashMap<>(); // [consume, produce]
        for (Map.Entry<appeng.api.networking.crafting.ICraftingPatternDetails, Long> e : quota.perRound.entrySet()) {
            long t = e.getValue();
            for (IAEItemStack input : e.getKey().getCondensedInputs()) {
                if (input != null) {
                    perRound.computeIfAbsent(RecursiveCraftingHelper.canon(input), k -> new long[2])[0] +=
                            input.getStackSize() * t;
                }
            }
            for (IAEItemStack output : e.getKey().getCondensedOutputs()) {
                if (output != null) {
                    perRound.computeIfAbsent(RecursiveCraftingHelper.canon(output), k -> new long[2])[1] +=
                            output.getStackSize() * t;
                }
            }
        }
        for (Map.Entry<IAEItemStack, long[]> e : perRound.entrySet()) {
            entries.put(e.getKey().copy(), new Entry(KIND_CYCLE, rounds, e.getValue()[1], e.getValue()[0], 0,
                    usedItems.getOrDefault(e.getKey(), 0L)));
        }
        return new SpecialPlanInfo(entries, callCounts);
    }

    /**
     * 遍历合成树:收集各样板的总执行次数(crafts > 0 的 process)与各键的 used 提取量.
     * <p>used 必须直读节点的 used 列表——populatePlan 的输出会把 used 与
     * requestable 条目按类型合并成同一条记录,无法还原.</p>
     */
    private static void collectFromTree(appeng.crafting.CraftingTreeNode node,
            Map<appeng.api.networking.crafting.ICraftingPatternDetails, Long> patternTimes,
            Map<IAEItemStack, Long> usedItems) {
        if (node == null) {
            return;
        }
        appeng.api.storage.data.IItemList<IAEItemStack> used = Ae2CraftingReflect.getNodeUsed(node);
        if (used != null) {
            for (IAEItemStack is : used) {
                if (is != null && is.getStackSize() > 0) {
                    usedItems.merge(RecursiveCraftingHelper.canon(is), is.getStackSize(), Long::sum);
                }
            }
        }
        for (appeng.crafting.CraftingTreeProcess pro : Ae2CraftingReflect.getNodeProcesses(node)) {
            long crafts = Ae2CraftingReflect.getProcessCrafts(pro);
            if (crafts > 0) {
                patternTimes.merge(Ae2CraftingReflect.getProcessDetails(pro), crafts, Long::sum);
            }
            for (appeng.crafting.CraftingTreeNode child : Ae2CraftingReflect.getProcessNodes(pro).keySet()) {
                collectFromTree(child, patternTimes, usedItems);
            }
        }
    }
}
