package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;

import com.github.aeddddd.ae2enhanced.util.RecursiveCraftingHelper;

/**
 * 特殊计划显示信息（服务端计算 → 客户端渲染）.
 * <p>从计划本身自恢复结构（无需标记传播）:自增殖样板经
 * {@link RecursiveCraftingHelper#isNetPositiveSelfRef} 识别;循环链复用
 * {@link RoundQuotaScheduler#deriveQuota} 的闭包 + GCD 轮次推导.</p>
 * <p>{@link #callCounts} 对**所有计划**(含普通)统计各主产出键的样板调用次数,
 * 客户端结合选中 CPU 的协处理器数显示"调用 N 次(约 R 轮发配)".</p>
 */
public record SpecialPlanInfo(Map<AEKey, Entry> entries, Map<AEKey, Long> callCounts) {

    public static final int KIND_SELF_DUP = 1;
    public static final int KIND_CYCLE = 2;

    public static final SpecialPlanInfo EMPTY = new SpecialPlanInfo(Map.of(), Map.of());

    /**
     * @param kind 1=自增殖样板;2=循环链成员
     * @param rounds 循环链总轮次(自增殖恒 1)
     * @param perRoundProduce 每轮(自增殖:每次)产出量
     * @param perRoundConsume 每轮(自增殖:每次)消耗量
     * @param totalCrafts 自增殖总调用次数(循环链恒 0)
     * @param initialExtract 初始提取(usedItems)
     */
    public record Entry(int kind, long rounds, long perRoundProduce, long perRoundConsume,
            long totalCrafts, long initialExtract) {
    }

    public boolean isEmpty() {
        return entries.isEmpty() && callCounts.isEmpty();
    }

    /**
     * 从完整计划计算显示信息（纯函数,服务端调用）.
     */
    public static SpecialPlanInfo compute(ICraftingPlan plan) {
        Map<AEKey, Entry> entries = new LinkedHashMap<>();
        var finalWhat = plan.finalOutput().what();
        var patternTimes = plan.patternTimes();

        // 全计划通用:各主产出键的样板调用次数(普通计划也显示)
        Map<AEKey, Long> callCounts = new LinkedHashMap<>();
        for (var entry : patternTimes.entrySet()) {
            callCounts.merge(entry.getKey().getPrimaryOutput().what(), entry.getValue(), Long::sum);
        }

        // 自增殖样板(与求解器阶段 1 一致,优先于循环链)
        for (var entry : patternTimes.entrySet()) {
            var pattern = entry.getKey();
            if (RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, finalWhat)) {
                long inPer = RecursiveCraftingHelper.selfInputPerCraft(pattern, finalWhat);
                long outPer = RecursiveCraftingHelper.selfOutputPerCraft(pattern, finalWhat);
                entries.put(finalWhat, new Entry(KIND_SELF_DUP, 1, outPer, inPer, entry.getValue(),
                        plan.usedItems().get(finalWhat)));
                return new SpecialPlanInfo(entries, callCounts);
            }
        }

        // 循环链(闭包 + GCD 轮次)
        var quota = RoundQuotaScheduler.deriveQuota(patternTimes, finalWhat);
        if (quota == null) {
            return new SpecialPlanInfo(entries, callCounts);
        }
        long rounds = 0;
        for (var entry : quota.perRound().entrySet()) {
            rounds = patternTimes.get(entry.getKey()) / entry.getValue();
            break;
        }
        Map<AEKey, long[]> perRound = new LinkedHashMap<>(); // [consume, produce]
        for (var entry : quota.perRound().entrySet()) {
            long t = entry.getValue();
            for (var input : entry.getKey().getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length == 0) {
                    continue;
                }
                perRound.computeIfAbsent(possible[0].what(), k -> new long[2])[0] += possible[0].amount()
                        * input.getMultiplier() * t;
            }
            for (var output : entry.getKey().getOutputs()) {
                perRound.computeIfAbsent(output.what(), k -> new long[2])[1] += output.amount() * t;
            }
        }
        for (var entry : perRound.entrySet()) {
            entries.put(entry.getKey(), new Entry(KIND_CYCLE, rounds, entry.getValue()[1], entry.getValue()[0],
                    0, plan.usedItems().get(entry.getKey())));
        }
        return new SpecialPlanInfo(entries, callCounts);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entries.size());
        for (var entry : entries.entrySet()) {
            AEKey.writeKey(buffer, entry.getKey());
            var e = entry.getValue();
            buffer.writeByte(e.kind());
            buffer.writeLong(e.rounds());
            buffer.writeLong(e.perRoundProduce());
            buffer.writeLong(e.perRoundConsume());
            buffer.writeLong(e.totalCrafts());
            buffer.writeLong(e.initialExtract());
        }
        buffer.writeVarInt(callCounts.size());
        for (var entry : callCounts.entrySet()) {
            AEKey.writeKey(buffer, entry.getKey());
            buffer.writeLong(entry.getValue());
        }
    }

    public static SpecialPlanInfo read(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<AEKey, Entry> entries = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            AEKey key = AEKey.readKey(buffer);
            entries.put(key, new Entry(buffer.readByte(), buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), buffer.readLong(), buffer.readLong()));
        }
        int callCountSize = buffer.readVarInt();
        Map<AEKey, Long> callCounts = new LinkedHashMap<>();
        for (int i = 0; i < callCountSize; i++) {
            callCounts.put(AEKey.readKey(buffer), buffer.readLong());
        }
        if (entries.isEmpty() && callCounts.isEmpty()) {
            return EMPTY;
        }
        return new SpecialPlanInfo(entries, callCounts);
    }

    @Nullable
    public Entry entryFor(AEKey key) {
        return entries.get(key);
    }

    /** 该键作为主产出的样板调用总次数(无则 0). */
    public long callCountOf(AEKey key) {
        return callCounts.getOrDefault(key, 0L);
    }
}
