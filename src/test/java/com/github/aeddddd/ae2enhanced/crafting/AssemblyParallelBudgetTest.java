package com.github.aeddddd.ae2enhanced.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AssemblyParallelBudget} 测试。
 *
 * <p>覆盖：单样板吃满额度、同周期多样板分摊（跨配方并行）、并发槽位限制（跨配方并行模块）、
 * 小样板未用完额度回流、在途占用到期释放、满级并行卡（无限）语义、待服务样板优先级与轮转覆盖。</p>
 */
public class AssemblyParallelBudgetTest {

    /** 不限并发配方数（跨配方并行模块满级）. */
    private static final int UNLIMITED = Integer.MAX_VALUE;

    /** 模拟枢纽侧结算：按额度取用，实际结算量受需求约束。 */
    private static long settle(AssemblyParallelBudget budget, Object pattern, long demand) {
        long allow = budget.allowance(pattern);
        long ops = Math.min(demand, allow);
        if (ops > 0) {
            budget.claim(pattern, ops);
        }
        return ops;
    }

    // ------------------------------------------------------------------
    // 基线：单样板吃满额度 + 在途到期释放
    // ------------------------------------------------------------------

    /** 只有一个个样板时拿到全部并行额度；在途未到期前不再分配，到期后恢复。 */
    @Test
    public void testSinglePatternUsesFullCapAndReleasesAfterCycle() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object a = new Object();

        budget.beginPass(0L, 64L, 20L, UNLIMITED, Collections.singletonList(a));
        assertThat(budget.allowance(a)).isEqualTo(64L);
        budget.claim(a, 64L);
        assertThat(budget.inFlightOps()).isEqualTo(64L);

        // 周期内：额度已被自身在途占用
        for (long now = 1L; now < 20L; now++) {
            budget.beginPass(now, 64L, 20L, UNLIMITED, Collections.singletonList(a));
            assertThat(budget.allowance(a)).isZero();
        }

        // 到期后额度恢复
        budget.beginPass(20L, 64L, 20L, UNLIMITED, Collections.singletonList(a));
        assertThat(budget.allowance(a)).isEqualTo(64L);
        assertThat(budget.inFlightOps()).isZero();
    }

    /** 同一 tick 内已获得份额的样板不会重复吃首轮额度（避免一趟内反复结算）。 */
    @Test
    public void testNoSecondShareWithinSameTick() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object a = new Object();
        Object b = new Object();

        budget.beginPass(0L, 64L, 20L, UNLIMITED, Arrays.asList(a, b));
        assertThat(budget.allowance(a)).isEqualTo(32L);
        budget.claim(a, 32L);
        assertThat(budget.allowance(b)).isEqualTo(32L);
        budget.claim(b, 32L);

        // 同 tick 内再来一趟：额度已耗尽
        budget.beginPass(0L, 64L, 20L, UNLIMITED, Arrays.asList(a, b));
        assertThat(budget.allowance(a)).isZero();
        assertThat(budget.allowance(b)).isZero();
    }

    // ------------------------------------------------------------------
    // 跨配方并行：同一周期内多个样板同时推进
    // ------------------------------------------------------------------

    /** 4 个待服务样板平分 64 额度：同一 tick 内每个样板都能结算（不再一次只处理一个配方）。 */
    @Test
    public void testMultiplePatternsRunInSameCycle() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        List<Object> patterns = Arrays.asList(new Object(), new Object(), new Object(), new Object());

        budget.beginPass(0L, 64L, 20L, UNLIMITED, patterns);
        long total = 0L;
        for (Object pattern : patterns) {
            long ops = settle(budget, pattern, 1000L);
            assertThat(ops).isEqualTo(16L); // 64 / 4
            total += ops;
        }
        assertThat(total).isEqualTo(64L);
        assertThat(budget.inFlightOps()).isEqualTo(64L);
    }

    /** 某个样板需求不足时，未用完的额度回流给后续样板（总额度仍被吃满）。 */
    @Test
    public void testUnusedShareFlowsToOtherPatterns() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object small = new Object();
        Object mid = new Object();
        Object big = new Object();
        List<Object> pending = Arrays.asList(small, mid, big);

        budget.beginPass(0L, 64L, 20L, UNLIMITED, pending);
        long smallOps = settle(budget, small, 1L);
        long midOps = settle(budget, mid, 1000L);
        long bigOps = settle(budget, big, 1000L);

        assertThat(smallOps).isEqualTo(1L);          // 只做了 1 份
        assertThat(midOps + bigOps).isEqualTo(63L);  // 剩余额度全部回流
        assertThat(budget.inFlightOps()).isEqualTo(64L);
    }

    /**
     * 有额度但材料不足的样板结算 0 份时，未被消耗的额度在同 tick 的后续轮次回流给
     * 仍有需求的样板（避免「有份额但做不了」的样板拖住整个枢纽）。
     */
    @Test
    public void testLeftoverReallocatedInLaterPass() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object starved = new Object();  // 有额度但材料不足 → 结算 0 份
        Object rich = new Object();
        Object other = new Object();
        List<Object> pending = Arrays.asList(starved, rich, other);

        // 第一趟：三个样板各拿首轮份额（64/3=21、64/2=32、63/1=63）
        budget.beginPass(0L, 64L, 20L, UNLIMITED, pending);
        assertThat(budget.allowance(starved)).isEqualTo(21L);
        assertThat(budget.allowance(rich)).isEqualTo(32L);
        budget.claim(rich, 1L); // 实际只结算 1 份
        assertThat(budget.allowance(other)).isEqualTo(63L);
        budget.claim(other, 1L);
        assertThat(budget.inFlightOps()).isEqualTo(2L);

        // 第二趟（同 tick）：已无样板在等首轮份额 → rich 吃下剩余额度
        budget.beginPass(0L, 64L, 20L, UNLIMITED, pending);
        assertThat(budget.allowance(rich)).isEqualTo(62L); // 64 - 1 - 1
        budget.claim(rich, 62L);
        assertThat(budget.inFlightOps()).isEqualTo(64L);
    }

    // ------------------------------------------------------------------
    // 并发槽位（跨配方并行模块）
    // ------------------------------------------------------------------

    /** 并发槽位 = 1：与原始「一次只处理一种配方」等价，其余样板本 tick 让位。 */
    @Test
    public void testSingleSlotSerializesRecipes() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object a = new Object();
        Object b = new Object();
        List<Object> pending = Arrays.asList(a, b);

        budget.beginPass(0L, 64L, 20L, 1, pending);
        assertThat(budget.allowance(a)).isEqualTo(64L); // 持槽者吃满额度
        budget.claim(a, 64L);
        assertThat(budget.allowance(b)).isZero();       // 槽位已满：本 tick 让位
        assertThat(budget.activePatterns()).isEqualTo(1);

        // 下个周期（在途到期）才轮到 b
        budget.beginPass(20L, 64L, 20L, 1, pending);
        assertThat(budget.allowance(b)).isEqualTo(64L);
    }

    /** 并发槽位 = 2：最多两个样板同时持有在途占用，额度在两者间分摊。 */
    @Test
    public void testTwoSlotsSplitBudgetBetweenTwoRecipes() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();
        List<Object> pending = Arrays.asList(a, b, c);

        budget.beginPass(0L, 64L, 20L, 2, pending);
        assertThat(budget.allowance(a)).isEqualTo(32L);
        budget.claim(a, 32L);
        assertThat(budget.allowance(b)).isEqualTo(32L);
        budget.claim(b, 32L);
        assertThat(budget.allowance(c)).isZero(); // 槽位已满
        assertThat(budget.activePatterns()).isEqualTo(2);
    }

    /**
     * 槽位被占满时，被拒的样板不得挡住持槽者的额度回流：
     * 持槽者只结算了少量份数时，剩余额度仍归它（小需求/欠料不浪费并行）。
     */
    @Test
    public void testSlotBlockedPatternsDoNotBlockReflow() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object active = new Object();
        Object blocked = new Object();
        List<Object> pending = Arrays.asList(active, blocked);

        budget.beginPass(0L, 64L, 20L, 1, pending);
        budget.claim(active, 1L);               // 持槽者只结算 1 份
        budget.allowance(blocked);              // 被拒（槽位已满）

        // 第二趟（同 tick）：仍有 63 额度,持槽者继续消化
        budget.beginPass(0L, 64L, 20L, 1, pending);
        assertThat(budget.allowance(active)).isEqualTo(63L);
        budget.claim(active, 63L);
        assertThat(budget.inFlightOps()).isEqualTo(64L);
    }

    /** 并发槽位满级（不限）时，全部待服务样板都能获得份额。 */
    @Test
    public void testUnlimitedSlotsServeAllPatterns() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        List<Object> patterns = Arrays.asList(new Object(), new Object(), new Object());

        budget.beginPass(0L, 64L, 20L, UNLIMITED, patterns);
        long total = 0L;
        for (Object pattern : patterns) {
            total += settle(budget, pattern, 1000L);
        }
        assertThat(total).isEqualTo(64L);
        assertThat(budget.activePatterns()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // 公平性：上限/槽位小于待服务样板数时靠轮转覆盖全部样板
    // ------------------------------------------------------------------

    /**
     * 上限 2、待服务 4 个样板：每一轮只有 2 个能结算，但按调用方逐 tick 轮转迭代顺序，
     * 所有样板都会在有限轮次内被覆盖（不会出现永久饥饿）。
     */
    @Test
    public void testAllPatternsServedAcrossRotatedTicks() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        List<Object> all = Arrays.asList(new Object(), new Object(), new Object(), new Object());
        boolean[] served = new boolean[all.size()];

        for (long now = 0L; now < all.size(); now++) {
            // 调用方（Mixin）按世界时间轮转迭代顺序
            List<Object> ordered = new ArrayList<>(all);
            Collections.rotate(ordered, -(int) (now % ordered.size()));

            budget.beginPass(now, 2L, 1L, UNLIMITED, ordered);
            for (Object pattern : ordered) {
                if (settle(budget, pattern, 1000L) > 0) {
                    served[all.indexOf(pattern)] = true;
                }
            }
            assertThat(budget.inFlightOps()).isLessThanOrEqualTo(2L); // 上限始终成立
        }

        assertThat(served).containsOnly(true);
    }

    /** 并发槽位为 1 时同样靠轮转覆盖全部样板（无线程饥饿）。 */
    @Test
    public void testSingleSlotRotatesAcrossPatterns() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        List<Object> all = Arrays.asList(new Object(), new Object(), new Object());
        boolean[] served = new boolean[all.size()];

        for (long now = 0L; now < all.size(); now++) {
            List<Object> ordered = new ArrayList<>(all);
            Collections.rotate(ordered, -(int) (now % ordered.size()));

            budget.beginPass(now, 64L, 1L, 1, ordered);
            for (Object pattern : ordered) {
                if (settle(budget, pattern, 1000L) > 0) {
                    served[all.indexOf(pattern)] = true;
                }
            }
            assertThat(budget.activePatterns()).isLessThanOrEqualTo(1);
        }

        assertThat(served).containsOnly(true);
    }

    /**
     * 未出现在待服务清单里的样板按「唯一竞争者」处理（防御性回退）：保证任何情况下
     * 都不会因为清单缺失而永久拿不到额度。正常调用不会走到该分支。
     */
    @Test
    public void testPatternOutsidePendingListFallsBackToFullBudget() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object pattern = new Object();

        budget.beginPass(0L, 64L, 20L, UNLIMITED, Collections.emptyList());
        assertThat(budget.allowance(pattern)).isEqualTo(64L);
    }

    /** 上限为 0 时任何样板都拿不到额度。 */
    @Test
    public void testZeroCapGrantsNothing() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object pattern = new Object();

        budget.beginPass(0L, 0L, 20L, UNLIMITED, Collections.singletonList(pattern));
        assertThat(budget.allowance(pattern)).isZero();
    }

    // ------------------------------------------------------------------
    // 满级并行卡（Long.MAX_VALUE）与边界
    // ------------------------------------------------------------------

    /** 满级并行卡：额度视为无限，在途不再限制后续结算。 */
    @Test
    public void testInfiniteCapIgnoresInFlight() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object a = new Object();

        budget.beginPass(0L, Long.MAX_VALUE, 20L, UNLIMITED, Collections.singletonList(a));
        budget.claim(a, Long.MAX_VALUE / 4);

        budget.beginPass(0L, Long.MAX_VALUE, 20L, UNLIMITED, Collections.singletonList(a));
        assertThat(budget.allowance(a)).isEqualTo(Long.MAX_VALUE);
    }

    /** 结算 0 份（或负数/null）不产生在途占用。 */
    @Test
    public void testNonPositiveClaimIsIgnored() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object a = new Object();

        budget.beginPass(0L, 64L, 20L, UNLIMITED, Collections.singletonList(a));
        budget.claim(a, 0L);
        budget.claim(a, -5L);
        budget.claim(null, 10L);

        assertThat(budget.inFlightOps()).isZero();
        assertThat(budget.allowance(a)).isEqualTo(64L);
    }

    /** clear 清空在途与趟状态。 */
    @Test
    public void testClearResetsState() {
        AssemblyParallelBudget budget = new AssemblyParallelBudget();
        Object a = new Object();

        budget.beginPass(0L, 64L, 20L, UNLIMITED, Collections.singletonList(a));
        budget.claim(a, 64L);
        budget.clear();

        assertThat(budget.inFlightOps()).isZero();
        assertThat(budget.activePatterns()).isZero();
        budget.beginPass(0L, 64L, 20L, UNLIMITED, Collections.singletonList(a));
        assertThat(budget.allowance(a)).isEqualTo(64L);
    }
}
