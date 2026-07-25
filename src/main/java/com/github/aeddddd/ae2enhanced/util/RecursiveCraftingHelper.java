package com.github.aeddddd.ae2enhanced.util;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;

import org.jetbrains.annotations.Nullable;

/**
 * 递归类合成（产物与原料含相同物品且净产出为正,如 A+2B=2A）的判定工具.
 * <p>AE2 1.20 原生计算层通过 {@code CraftingTreeNode#notRecursive} 排除一切自引用样板,
 * 并在 {@code CraftingCalculation#runCraftAttempt} 中对请求物执行 {@code ignore(output)}
 * 清零库存,导致顶层下单此类配方必然报缺料.本工具为计算层 Mixin 提供统一的判定逻辑.</p>
 */
public final class RecursiveCraftingHelper {

    private RecursiveCraftingHelper() {
    }

    /**
     * 查找样板的精确自引用 key:主输入 key 与输出 key 完全相等(非模糊匹配),
     * 且每份产出总量 ≥ 投入总量(净增殖或催化剂型).
     * <p>NBT 变化型(耐久损耗等模糊自引用)不会被命中——其借贷记账需要逐物品追踪,
     * 不在闭式解范围内.</p>
     *
     * @return 自引用 key;不存在或净耗型返回 null.
     */
    @Nullable
    public static AEKey findSelfRefKey(IPatternDetails details) {
        for (var input : details.getInputs()) {
            var possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length == 0 || possibleInputs[0].amount() <= 0) {
                continue;
            }
            AEKey inKey = possibleInputs[0].what();
            long inPer = possibleInputs[0].amount() * input.getMultiplier();
            if (inPer <= 0) {
                continue;
            }
            long outPer = 0;
            for (var output : details.getOutputs()) {
                if (inKey.equals(output.what())) {
                    outPer += output.amount();
                }
            }
            if (outPer >= inPer) {
                return inKey;
            }
        }
        return null;
    }

    /**
     * 每次合成消耗的 {@code what} 数量（所有以 what 为主候选的输入槽合计）.
     */
    public static long selfInputPerCraft(IPatternDetails details, AEKey what) {
        long in = 0;
        for (var input : details.getInputs()) {
            var primary = input.getPossibleInputs()[0];
            if (what.matches(primary)) {
                in += primary.amount() * input.getMultiplier();
            }
        }
        return in;
    }

    /**
     * 每次合成产出的 {@code what} 数量.
     */
    public static long selfOutputPerCraft(IPatternDetails details, AEKey what) {
        long out = 0;
        for (var output : details.getOutputs()) {
            if (what.matches(output)) {
                out += output.amount();
            }
        }
        return out;
    }

    /**
     * 是否为 {@code what} 的净产出自引用样板：原料与产物均含 what,且每次合成净产出为正.
     */
    public static boolean isNetPositiveSelfRef(IPatternDetails details, AEKey what) {
        long in = selfInputPerCraft(details, what);
        if (in <= 0) {
            return false;
        }
        return selfOutputPerCraft(details, what) > in;
    }

    /**
     * {@code what} 的候选样板是否唯一且为净产出自引用样板.
     * 仅在唯一候选时接管计算,避免改变多分支场景的原生择优行为.
     */
    public static boolean isOnlyCandidateSelfRef(ICraftingService craftingService, AEKey what) {
        var patterns = craftingService.getCraftingFor(what);
        var it = patterns.iterator();
        if (!it.hasNext()) {
            return false;
        }
        IPatternDetails only = it.next();
        if (it.hasNext()) {
            return false; // 多候选样板：保持原生择优,不接管
        }
        return isNetPositiveSelfRef(only, what);
    }
}
