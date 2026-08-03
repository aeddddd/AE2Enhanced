package com.github.aeddddd.ae2enhanced.test.craftingplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.CrossCraftingGraphGenerator;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * 极端规模基准:大规模交叉合成图(主体普通合成 + 部分子环 + 部分特殊合成),
 * 订单字节数 ~100T(AE 标准口径:1 物品 = 1 字节),对比原生递归树与 DAG 引擎的
 * 计算速度,验证极端情况下是否存在实质提升.
 * <ul>
 * <li>纯主体图:原生与 DAG 均应成功,逐字段 parity(patternTimes/usedItems),
 * 字节口径核对,计时对比;</li>
 * <li>混合图:DAG 应产出可行计划(种子语义精确);原生作对照——
 * 环/特殊通道不可规划(缺料),仅提供耗时参照.</li>
 * </ul>
 * 计时含 JUnit 环境噪声,加速断言取保守阈值(4×),报告打印精确数值.
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class CrossCraftingScaleBenchmarkTest {

    private static final CalculationStrategy REPORT = CalculationStrategy.REPORT_MISSING_ITEMS;

    /** 目标字节规模:~100T = 1e14(AE 标准:1 物品 = 1 字节). */
    private static final long TARGET_BYTES = 100_000_000_000_000L;
    private static final long MIN_BYTES = TARGET_BYTES / 4;
    private static final long MAX_BYTES = TARGET_BYTES * 4;

    /**
     * 极端压力参数:深度 13 × 宽 256 × 扇入 3.
     * 原生递归树按路径展开 ≈ 3^14 ≈ 4.8e6 叶、≈7.2e6 节点(计算负担主体);
     * DAG 合并后 ≈3.6e3 唯一节点(物品×少量 NBT 变体突破原版物品数上限).
     */
    private static final int LAYERS = 13;
    private static final int WIDTH = 256;
    private static final int FAN_IN = 3;
    private static final int LOOPS = 8;
    private static final int DUPS = 8;

    /** 小图热身:预热双方代码路径,降低 JIT 噪声. */
    @BeforeAll
    static void warmup() {
        var g = CrossCraftingGraphGenerator.generate(4, 8, 2, 1, 1, 7);
        var what = new GenericStack(g.root(), 1000);
        g.env().runSimulation(what, REPORT, 60_000); // 原生热身(混合小图走缺料路径,无碍)
        g.env().runDagSimulation(what, REPORT);
    }

    /**
     * 纯主体交叉图(无环无特殊):~100T 字节订单下原生 vs DAG.
     * 双方计划必须逐字段一致,且 DAG 显著更快.
     */
    @Test
    public void pureBodyNativeVsDagAt100T() {
        var graph = CrossCraftingGraphGenerator.generate(LAYERS, WIDTH, FAN_IN, 0, 0, 42);
        long request = Math.max(1, TARGET_BYTES / graph.unitCost());
        var what = new GenericStack(graph.root(), request);

        long t0 = System.nanoTime();
        var nativePlan = graph.env().runSimulation(what, REPORT, 600_000);
        long nativeNanos = System.nanoTime() - t0;

        t0 = System.nanoTime();
        var dagPlan = graph.env().runDagSimulation(what, REPORT);
        long dagNanos = System.nanoTime() - t0;

        // 原料充足:双方一次性全量成功
        assertThat(nativePlan.simulation()).as("原生应可行").isFalse();
        assertThat(dagPlan.simulation()).as("DAG 应可行").isFalse();
        assertThat(dagPlan.finalOutput()).isEqualTo(nativePlan.finalOutput());

        // parity:DAG 与原生计划逐字段一致(bytes 为近似记账,不在比对范围)
        assertThat(dagPlan.patternTimes()).isEqualTo(nativePlan.patternTimes());
        assertThat(toMap(dagPlan.usedItems())).isEqualTo(toMap(nativePlan.usedItems()));
        assertThat(dagPlan.missingItems().isEmpty()).isTrue();
        assertThat(toMap(dagPlan.emittedItems())).isEqualTo(toMap(nativePlan.emittedItems()));

        // 字节规模核对:原生精确口径与统一估算口径均应落在 ~100T 区间
        long nativeStandard = aeStandardBytes(nativePlan);
        long dagStandard = aeStandardBytes(dagPlan);
        assertThat(nativePlan.bytes()).as("原生精确字节口径").isBetween(MIN_BYTES, MAX_BYTES);
        assertThat(nativeStandard).as("原生 AE 标准估算").isBetween(MIN_BYTES, MAX_BYTES);
        assertThat(dagStandard).as("DAG AE 标准估算").isBetween(MIN_BYTES, MAX_BYTES);

        report("纯主体交叉图", graph, request, nativeNanos, dagNanos, nativePlan, dagPlan, dagStandard, 0);

        assertThat(dagNanos).as("DAG 应显著快于原生(阈值 4×)").isLessThan(nativeNanos / 4);
    }

    /**
     * 混合图(主体普通 + 8 催化子环 + 8 自引用特殊合成):~100T 字节订单.
     * DAG 应产出可行计划且种子/环轮次记账精确;原生无法规划环/特殊通道(缺料),
     * 仅作耗时参照.
     */
    @Test
    public void mixedGraphDagAt100T() {
        var graph = CrossCraftingGraphGenerator.generate(LAYERS, WIDTH, FAN_IN, LOOPS, DUPS, 42);
        long request = Math.max(1, TARGET_BYTES / graph.unitCost());
        var what = new GenericStack(graph.root(), request);

        long t0 = System.nanoTime();
        var dagPlan = graph.env().runDagSimulation(what, REPORT);
        long dagNanos = System.nanoTime() - t0;

        assertThat(dagPlan.simulation()).as("DAG 应产出可行的混合计划").isFalse();
        assertThat(dagPlan.finalOutput().amount()).isEqualTo(request);
        // 种子语义:每个环/特殊通道恰好消耗 1 份种子
        for (var seed : graph.loopSeedKeys()) {
            assertThat(dagPlan.usedItems().get(seed)).as("催化环种子").isEqualTo(1);
        }
        for (var seed : graph.dupSeedKeys()) {
            assertThat(dagPlan.usedItems().get(seed)).as("自引用种子").isEqualTo(1);
        }
        // 环/特殊样板确实按请求规模参与计划
        var times = primaryMap(dagPlan);
        for (var x : graph.loopOutputKeys()) {
            assertThat(times.get(x)).as("催化环轮次 = 请求量").isEqualTo(request);
        }
        for (var s : graph.dupSeedKeys()) {
            assertThat(times.get(s)).as("自引用增殖次数 = 请求量").isEqualTo(request);
        }

        long dagStandard = aeStandardBytes(dagPlan);
        assertThat(dagStandard).as("DAG AE 标准估算").isBetween(MIN_BYTES, MAX_BYTES);

        // 原生对照:同一订单,环/特殊通道不可规划 → 缺料计划(耗时含失败尝试 + 模拟尝试两趟)
        t0 = System.nanoTime();
        var nativePlan = graph.env().runSimulation(what, REPORT, 900_000);
        long nativeNanos = System.nanoTime() - t0;
        long nativeMissing = 0;
        for (var entry : nativePlan.missingItems()) {
            nativeMissing += entry.getLongValue();
        }
        assertThat(nativePlan.simulation()).as("原生应无法规划环/特殊通道").isTrue();
        assertThat(nativeMissing).as("原生缺料(环/特殊通道)").isGreaterThan(0);

        report("混合图(主体+子环+特殊)", graph, request, nativeNanos, dagNanos, nativePlan, dagPlan,
                dagStandard, nativeMissing);

        assertThat(dagNanos).as("DAG 应显著快于原生(阈值 4×)").isLessThan(nativeNanos / 4);
    }

    /**
     * AE 标准口径字节估算(与 CraftingSimulationTest.bytesMatch 同公式的主项):
     * 消耗/发射/缺料物品量(1 物品 = 1 字节) + 样板执行总次数;
     * 省略节点×8 的微小项(在 1e14 规模下可忽略).
     */
    private static long aeStandardBytes(ICraftingPlan plan) {
        long bytes = 0;
        for (var entry : plan.usedItems()) {
            bytes += entry.getLongValue();
        }
        for (var entry : plan.emittedItems()) {
            bytes += entry.getLongValue();
        }
        for (var entry : plan.missingItems()) {
            bytes += entry.getLongValue();
        }
        for (var t : plan.patternTimes().values()) {
            bytes += t;
        }
        return bytes;
    }

    private static Map<AEKey, Long> primaryMap(ICraftingPlan plan) {
        Map<AEKey, Long> out = new HashMap<>();
        plan.patternTimes().forEach((pattern, times) -> out.merge(pattern.getPrimaryOutput().what(),
                times, Long::sum));
        return out;
    }

    private static Map<AEKey, Long> toMap(KeyCounter counter) {
        Map<AEKey, Long> out = new HashMap<>();
        for (var key : counter.keySet()) {
            out.put(key, counter.get(key));
        }
        return out;
    }

    private static void report(String label, CrossCraftingGraphGenerator.Generated graph, long request,
            long nativeNanos, long dagNanos, ICraftingPlan nativePlan, ICraftingPlan dagPlan,
            long standardBytes, long nativeMissing) {
        System.out.printf(
                "[Bench] %s: 普通样板=%,d, 子环=%d, 特殊=%d, 请求量=%,d, AE标准字节≈%.2fT%n",
                label, graph.normalPatterns(), graph.loopSeedKeys().size(), graph.dupSeedKeys().size(),
                request, standardBytes / 1e12);
        System.out.printf(
                "[Bench]   原生: %,d ms (plan.bytes=%,d, 缺料=%,d)%n",
                nativeNanos / 1_000_000, nativePlan.bytes(), nativeMissing);
        System.out.printf(
                "[Bench]   DAG : %,d ms (plan.bytes=%,d)%n",
                dagNanos / 1_000_000, dagPlan.bytes());
        System.out.printf(
                "[Bench]   加速比: %.1f×%n",
                (double) nativeNanos / Math.max(1, dagNanos));
    }
}
