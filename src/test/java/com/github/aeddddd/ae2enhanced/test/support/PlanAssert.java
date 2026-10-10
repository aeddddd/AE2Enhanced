package com.github.aeddddd.ae2enhanced.test.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

/**
 * 计划断言辅助（对应 1.20.1 各测试类的 CraftingPlanAssert,1.12.2 统一版）.
 * <p>守恒不变量:交付量 = 请求量,网络消耗 = 种子 + 非自输入.</p>
 */
public final class PlanAssert {

    private final PlanView plan;

    private PlanAssert(PlanView plan) {
        this.plan = Objects.requireNonNull(plan);
    }

    public static PlanAssert assertThatPlan(PlanView plan) {
        return new PlanAssert(plan);
    }

    public PlanAssert succeeded() {
        assertThat(plan.simulation()).as("计划应成功(非模拟)").isFalse();
        assertThat(plan.missingItems()).as("成功计划不应有缺失").isEmpty();
        return this;
    }

    public PlanAssert failed() {
        assertThat(plan.simulation()).as("计划应失败(模拟/缺料)").isTrue();
        return this;
    }

    public PlanAssert patternsMatch(ICraftingPatternDetails p1, long t1) {
        Map<ICraftingPatternDetails, Long> expected = new LinkedHashMap<>();
        expected.put(p1, t1);
        return patternsMatch(expected);
    }

    public PlanAssert patternsMatch(Map<ICraftingPatternDetails, Long> expected) {
        assertThat(plan.patternTimes()).isEqualTo(expected);
        return this;
    }

    public PlanAssert listMatches(Map<IAEItemStack, Long> actual, IAEItemStack... expectedStacks) {
        Map<IAEItemStack, Long> expected = new LinkedHashMap<>();
        for (IAEItemStack stack : expectedStacks) {
            IAEItemStack key = com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper.canon(stack);
            expected.merge(key, stack.getStackSize(), Long::sum);
        }
        assertThat(actual).isEqualTo(expected);
        return this;
    }

    public PlanAssert missingMatch(IAEItemStack... stacks) {
        return listMatches(plan.missingItems(), stacks);
    }

    public PlanAssert usedMatch(IAEItemStack... stacks) {
        return listMatches(plan.usedItems(), stacks);
    }

    /** used 列表应包含这些键及数量(不要求精确相等——populatePlan 平铺列表
     * 会把 missing 条目混入 used 视图,显示层由容器按网络实存再分离). */
    public PlanAssert usedContains(IAEItemStack... stacks) {
        Map<IAEItemStack, Long> actual = plan.usedItems();
        for (IAEItemStack stack : stacks) {
            IAEItemStack key = com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper.canon(stack);
            assertThat(actual.getOrDefault(key, 0L)).as("used 应含 %s×%s", key, stack.getStackSize())
                    .isGreaterThanOrEqualTo(stack.getStackSize());
        }
        return this;
    }
}
