package com.github.aeddddd.ae2enhanced.test.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.RoundQuotaScheduler;

/**
 * 执行层模拟工装（1.12.2 移植版）：把计划层单测无法覆盖的执行语义变成自动化断言.
 * <p>模型（与游戏语义对齐）:</p>
 * <ul>
 * <li>初始提取 = 计划 usedItems,从网络移入 CPU 库存；</li>
 * <li>推送：仅从 CPU 库存消耗输入（任务中途不可访问网络）,产出次拍返回（模拟机器延迟）;</li>
 * <li>门控：自消耗 job 的最终产出在任务收官前滞留 CPU 库存，收官时一次性交付；</li>
 * <li>配额：可选启用 {@link RoundQuotaScheduler}（与游戏内虚拟 CPU 行为一致）;</li>
 * <li>收官：交付 finalOutput,CPU 剩余全部返还网络——CPU 必须清空（无残留断言）.</li>
 * </ul>
 * 守恒不变量：网络期末 - 网络期初 = 净产出 - 交付（种子/催化剂净变化为 0,副产物净增）.
 * <p>1.12.2 差异:计划读取面为 {@link PlanView}（合成树遍历）,键为规范化
 * {@link IAEItemStack},样板无 multiplier 概念（每槽数量即单次消耗）.</p>
 */
public final class ExecutionHarness {

    public static final class Options {
        public final boolean quota;
        public final boolean gate;
        public final long pushBudgetPerTick;
        public final int maxTicks;

        public Options(boolean quota, boolean gate, long pushBudgetPerTick, int maxTicks) {
            this.quota = quota;
            this.gate = gate;
            this.pushBudgetPerTick = pushBudgetPerTick;
            this.maxTicks = maxTicks;
        }

        /** 与游戏内一致:配额 + 门控开启,充足协处理器预算. */
        public static Options gameDefaults() {
            return new Options(true, true, 1_000_000, 100_000);
        }

        /** 串行推送(每拍 1 次,模拟无协处理器). */
        public Options serial() {
            return new Options(this.quota, this.gate, 1, this.maxTicks);
        }

        public Options withoutQuota() {
            return new Options(false, this.gate, this.pushBudgetPerTick, this.maxTicks);
        }
    }

    public static final class Result {
        public final boolean completed;
        public final boolean deadlock;
        public final int ticks;
        public final Map<IAEItemStack, Long> network;
        public final Map<IAEItemStack, Long> cpuInventory;
        public final long delivered;

        Result(boolean completed, boolean deadlock, int ticks, Map<IAEItemStack, Long> network,
                Map<IAEItemStack, Long> cpuInventory, long delivered) {
            this.completed = completed;
            this.deadlock = deadlock;
            this.ticks = ticks;
            this.network = network;
            this.cpuInventory = cpuInventory;
            this.delivered = delivered;
        }
    }

    private ExecutionHarness() {
    }

    /** 按计划 usedItems 初始提取. */
    public static Result execute(PlanView plan, Map<IAEItemStack, Long> networkInitial, Options options,
            List<ICraftingPatternDetails> pushOrder) {
        Map<IAEItemStack, Long> initial = new LinkedHashMap<>();
        for (Map.Entry<IAEItemStack, Long> entry : plan.usedItems().entrySet()) {
            initial.merge(RecursiveCraftingHelper.canon(entry.getKey()), entry.getValue(), Long::sum);
        }
        return execute(plan, networkInitial, initial, options, pushOrder);
    }

    /**
     * 显式指定初始提取（用于构造"计划违约"的反面用例）.
     *
     * @param initialExtraction 初始从网络提取到 CPU 的物品
     */
    public static Result execute(PlanView plan, Map<IAEItemStack, Long> networkInitial,
            Map<IAEItemStack, Long> initialExtraction, Options options, List<ICraftingPatternDetails> pushOrder) {
        Map<IAEItemStack, Long> network = new LinkedHashMap<>();
        for (Map.Entry<IAEItemStack, Long> entry : networkInitial.entrySet()) {
            network.merge(RecursiveCraftingHelper.canon(entry.getKey()), entry.getValue(), Long::sum);
        }
        Map<IAEItemStack, Long> cpu = new LinkedHashMap<>();
        Map<IAEItemStack, Long> inFlight = new LinkedHashMap<>();
        Map<ICraftingPatternDetails, Long> remaining = new LinkedHashMap<>(plan.patternTimes());
        Map<ICraftingPatternDetails, Long> totals = new LinkedHashMap<>(plan.patternTimes());
        IAEItemStack finalWhat = RecursiveCraftingHelper.canon(plan.finalOutput());
        long finalAmount = plan.finalOutput().getStackSize();

        // 初始提取
        for (Map.Entry<IAEItemStack, Long> entry : initialExtraction.entrySet()) {
            IAEItemStack key = RecursiveCraftingHelper.canon(entry.getKey());
            long got = take(network, key, entry.getValue());
            cpu.merge(key, got, Long::sum);
        }

        RoundQuotaScheduler.Quota quota = options.quota
                ? RoundQuotaScheduler.deriveQuota(totals, finalWhat)
                : null;
        boolean selfConsuming = quota != null;
        boolean gate = options.gate && selfConsuming;

        long delivered = 0;
        int ticks = 0;
        while (true) {
            if (allZero(remaining) && inFlight.isEmpty()) {
                break;
            }
            if (++ticks > options.maxTicks) {
                return new Result(false, true, ticks, network, cpu, delivered);
            }
            boolean progress = false;

            // 推送阶段(贪婪,受配额与预算约束;配额逐次推送重新评估,与游戏注入点语义一致)
            long budget = options.pushBudgetPerTick;
            for (ICraftingPatternDetails pattern : pushOrder) {
                while (remaining.getOrDefault(pattern, 0L) > 0 && budget > 0 && canConsume(cpu, pattern)
                        && (quota == null
                                || RoundQuotaScheduler.isPushAllowed(quota, totals, remaining, pattern))) {
                    consumeInputs(cpu, pattern);
                    addOutputs(inFlight, pattern);
                    remaining.merge(pattern, -1L, Long::sum);
                    budget--;
                    progress = true;
                }
            }

            // 返回阶段(次拍回流)
            for (Map.Entry<IAEItemStack, Long> entry : new ArrayList<>(inFlight.entrySet())) {
                progress = true;
                if (gate && entry.getKey().equals(finalWhat)) {
                    // 门控:最终产出滞留 CPU 库存,收官时统一交付
                    cpu.merge(entry.getKey(), entry.getValue(), Long::sum);
                } else if (entry.getKey().equals(finalWhat)) {
                    // 未门控(原生行为):立即交付——自消耗场景下这正是饿死根源
                    delivered += entry.getValue();
                } else {
                    cpu.merge(entry.getKey(), entry.getValue(), Long::sum);
                }
            }
            inFlight.clear();

            if (!progress) {
                return new Result(false, true, ticks, network, cpu, delivered);
            }
        }

        // 收官:交付 finalOutput(必须足额在 CPU 库存中),剩余全部返还网络
        delivered += take(cpu, finalWhat, finalAmount);
        for (Map.Entry<IAEItemStack, Long> entry : new ArrayList<>(cpu.entrySet())) {
            network.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        cpu.clear();
        return new Result(true, false, ticks, network, cpu, delivered);
    }

    /** 生成指定 pattern 列表的全部排列(≤4 个时穷举,否则按种子洗牌取样). */
    public static List<List<ICraftingPatternDetails>> pushOrders(List<ICraftingPatternDetails> patterns) {
        List<List<ICraftingPatternDetails>> result = new ArrayList<>();
        permute(new ArrayList<>(patterns), 0, result, 120);
        return result;
    }

    private static void permute(List<ICraftingPatternDetails> items, int index,
            List<List<ICraftingPatternDetails>> out, int cap) {
        if (out.size() >= cap) {
            return;
        }
        if (index == items.size()) {
            out.add(new ArrayList<>(items));
            return;
        }
        for (int i = index; i < items.size(); i++) {
            ICraftingPatternDetails tmp = items.get(index);
            items.set(index, items.get(i));
            items.set(i, tmp);
            permute(items, index + 1, out, cap);
            tmp = items.get(index);
            items.set(index, items.get(i));
            items.set(i, tmp);
        }
    }

    private static boolean allZero(Map<ICraftingPatternDetails, Long> remaining) {
        for (long value : remaining.values()) {
            if (value > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean canConsume(Map<IAEItemStack, Long> cpu, ICraftingPatternDetails pattern) {
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input == null) {
                continue;
            }
            if (cpu.getOrDefault(RecursiveCraftingHelper.canon(input), 0L) < input.getStackSize()) {
                return false;
            }
        }
        return true;
    }

    private static void consumeInputs(Map<IAEItemStack, Long> cpu, ICraftingPatternDetails pattern) {
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input == null) {
                continue;
            }
            cpu.merge(RecursiveCraftingHelper.canon(input), -input.getStackSize(), Long::sum);
        }
    }

    private static void addOutputs(Map<IAEItemStack, Long> inFlight, ICraftingPatternDetails pattern) {
        for (IAEItemStack output : pattern.getCondensedOutputs()) {
            if (output == null) {
                continue;
            }
            inFlight.merge(RecursiveCraftingHelper.canon(output), output.getStackSize(), Long::sum);
        }
    }

    private static long take(Map<IAEItemStack, Long> stock, IAEItemStack key, long amount) {
        long have = stock.getOrDefault(key, 0L);
        long taken = Math.min(have, amount);
        stock.put(key, have - taken);
        return taken;
    }
}
