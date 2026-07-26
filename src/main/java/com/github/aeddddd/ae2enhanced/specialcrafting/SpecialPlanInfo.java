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
 * {@link RoundQuotaScheduler#deriveQuota} 的闭包 + GCD 轮次推导.
 * 普通计划得到 {@link #EMPTY}（客户端清空缓存,不显示任何附加信息）.</p>
 */
public record SpecialPlanInfo(Map<AEKey, Entry> entries) {

    public static final int KIND_SELF_DUP = 1;
    public static final int KIND_CYCLE = 2;

    public static final SpecialPlanInfo EMPTY = new SpecialPlanInfo(Map.of());

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
        return entries.isEmpty();
    }

    /**
     * 从完整计划计算显示信息（纯函数,服务端调用）.
     */
    public static SpecialPlanInfo compute(ICraftingPlan plan) {
        Map<AEKey, Entry> entries = new LinkedHashMap<>();
        var finalWhat = plan.finalOutput().what();
        var patternTimes = plan.patternTimes();

        // 自增殖样板(与求解器阶段 1 一致,优先于循环链)
        for (var entry : patternTimes.entrySet()) {
            var pattern = entry.getKey();
            if (RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, finalWhat)) {
                long inPer = RecursiveCraftingHelper.selfInputPerCraft(pattern, finalWhat);
                long outPer = RecursiveCraftingHelper.selfOutputPerCraft(pattern, finalWhat);
                entries.put(finalWhat, new Entry(KIND_SELF_DUP, 1, outPer, inPer, entry.getValue(),
                        plan.usedItems().get(finalWhat)));
                return new SpecialPlanInfo(entries);
            }
        }

        // 循环链(闭包 + GCD 轮次)
        var quota = RoundQuotaScheduler.deriveQuota(patternTimes, finalWhat);
        if (quota == null) {
            return EMPTY;
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
        return new SpecialPlanInfo(entries);
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
    }

    public static SpecialPlanInfo read(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size == 0) {
            return EMPTY;
        }
        Map<AEKey, Entry> entries = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            AEKey key = AEKey.readKey(buffer);
            entries.put(key, new Entry(buffer.readByte(), buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), buffer.readLong(), buffer.readLong()));
        }
        return new SpecialPlanInfo(entries);
    }

    @Nullable
    public Entry entryFor(AEKey key) {
        return entries.get(key);
    }
}
