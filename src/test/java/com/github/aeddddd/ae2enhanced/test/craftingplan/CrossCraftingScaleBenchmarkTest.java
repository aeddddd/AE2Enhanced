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
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
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

    /** 原生墙钟超时(宽输入组):步进喂片,超时中断计算线程. */
    private static final long NATIVE_TIMEOUT_MS = 20_000;
    /**
     * 原生墙钟超时(深层组),单独调小:深层树节点小、生长快,
     * 20s 会长出 ~1.4e7 节点超出测试堆;10s ≈ 7e6 节点 ≈ 3GB,安全且相对
     * 估算的 ~28 分钟仍是充分 DNF.
     */
    private static final long DEEP_NATIVE_TIMEOUT_MS = 10_000;

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
     * 更深层数:深度 18 × 扇入 3,原生路径 ≈ 3^19 ≈ 1.2e9(按实测 ~7e5 节点/s
     * 估算 ≈ 28 分钟)→ 原生必须墙钟超时;DAG 合并后 ≈1.2e3 节点,照常完成.
     */
    @Test
    public void deepLayersNativeTimeoutDagCompletes() {
        var graph = CrossCraftingGraphGenerator.generate(18, 64, 3, 0, 0, 42);
        long request = Math.max(1, TARGET_BYTES / Math.min(graph.unitCost(), TARGET_BYTES));
        var what = new GenericStack(graph.root(), request);

        long t0 = System.nanoTime();
        var dagPlan = graph.env().runDagSimulation(what, REPORT);
        long dagNanos = System.nanoTime() - t0;

        assertThat(dagPlan.simulation()).as("DAG 应可行").isFalse();
        assertThat(dagPlan.finalOutput().amount()).isEqualTo(request);
        long dagStandard = aeStandardBytes(dagPlan);
        assertThat(dagStandard).as("字节规模下限").isGreaterThanOrEqualTo(TARGET_BYTES / 10);

        long t1 = System.nanoTime();
        try {
            var nativePlan = graph.env().runSimulation(what, REPORT, DEEP_NATIVE_TIMEOUT_MS);
            report("深层图(18 层,原生意外完成)", graph, request, System.nanoTime() - t1, dagNanos,
                    nativePlan, dagPlan, dagStandard, 0);
        } catch (SimulationEnv.SimulationTimeoutException e) {
            reportDnf("深层图(18 层)", graph, request, dagNanos, dagPlan, dagStandard,
                    DEEP_NATIVE_TIMEOUT_MS);
        } finally {
            System.gc(); // 释放中断时残留的百万级节点树,避免挤压后续用例堆
        }
    }

    /**
     * 解放样板生成(输入种类 1..81 随机 + 大数量).宽扇入爆炸(原生超时)与
     * int 级大数量在单一可行图内不可共存(成本预算必然截断其一),故拆两子图:
     * <ul>
     * <li>(a) 宽输入组:扇入 1..81(受层宽 48 钳制),数量 1..64,扇入期望 ~24 →
     * 原生路径 ≈ 24^6 ≈ 2e8(估算数分钟)→ 原生必须墙钟超时;DAG ≈3e2 节点照常完成;</li>
     * <li>(b) 大数量组:浅层(3)+ 扇入 1..81 + 单种数量可达 {@link Integer#MAX_VALUE}
     * (需求预算内),原生浅树可完成 → 双方逐字段 parity,验证大数量记账正确性.</li>
     * </ul>
     */
    @Test
    public void wideInputsBigAmountsNativeTimeout() {
        // (a) 宽输入组
        var wide = CrossCraftingGraphGenerator.generate(
                new CrossCraftingGraphGenerator.Params(6, 48, 1, 81, 64, 0, 0, 0, 42));
        long wideRequest = Math.max(1, TARGET_BYTES / Math.min(wide.unitCost(), TARGET_BYTES));
        var wideWhat = new GenericStack(wide.root(), wideRequest);

        long t0 = System.nanoTime();
        var widePlan = wide.env().runDagSimulation(wideWhat, REPORT);
        long wideDagNanos = System.nanoTime() - t0;

        assertThat(widePlan.simulation()).as("宽输入图 DAG 应可行, 诊断=" + diagnose(widePlan)).isFalse();
        assertThat(widePlan.finalOutput().amount()).isEqualTo(wideRequest);
        long wideStandard = aeStandardBytes(widePlan);
        assertThat(wideStandard).as("字节规模下限").isGreaterThanOrEqualTo(TARGET_BYTES / 10);

        long t1 = System.nanoTime();
        try {
            var nativePlan = wide.env().runSimulation(wideWhat, REPORT, NATIVE_TIMEOUT_MS);
            report("宽输入图(1..81 输入,原生意外完成)", wide, wideRequest, System.nanoTime() - t1,
                    wideDagNanos, nativePlan, widePlan, wideStandard, 0);
        } catch (SimulationEnv.SimulationTimeoutException e) {
            reportDnf("宽输入图(1..81 输入)", wide, wideRequest, wideDagNanos, widePlan, wideStandard,
                    NATIVE_TIMEOUT_MS);
        } finally {
            System.gc(); // 释放中断时残留的大树,避免挤压后续用例堆
        }

        // (b) 大数量组
        var big = CrossCraftingGraphGenerator.generate(
                new CrossCraftingGraphGenerator.Params(3, 48, 1, 81, Integer.MAX_VALUE, 0, 0, 0, 7));
        assertThat(big.maxInputAmount()).as("应真实出现超大数量输入(>1e6)").isGreaterThan(1_000_000L);
        long bigRequest = Math.max(1, TARGET_BYTES / Math.min(big.unitCost(), TARGET_BYTES));
        var bigWhat = new GenericStack(big.root(), bigRequest);

        t0 = System.nanoTime();
        var bigNative = big.env().runSimulation(bigWhat, REPORT, 600_000);
        long bigNativeNanos = System.nanoTime() - t0;

        t0 = System.nanoTime();
        var bigDag = big.env().runDagSimulation(bigWhat, REPORT);
        long bigDagNanos = System.nanoTime() - t0;

        assertThat(bigNative.simulation()).as("大数量图原生应可行").isFalse();
        assertThat(bigDag.simulation()).as("大数量图 DAG 应可行, 诊断=" + diagnose(bigDag)).isFalse();
        assertThat(bigDag.finalOutput()).isEqualTo(bigNative.finalOutput());
        // parity:大数量记账下 DAG 与原生计划逐字段一致(bytes 为近似记账,不在比对范围)
        assertThat(bigDag.patternTimes()).isEqualTo(bigNative.patternTimes());
        assertThat(toMap(bigDag.usedItems())).isEqualTo(toMap(bigNative.usedItems()));
        assertThat(bigDag.missingItems().isEmpty()).isTrue();

        long bigStandard = aeStandardBytes(bigDag);
        assertThat(bigStandard).as("字节规模下限").isGreaterThanOrEqualTo(TARGET_BYTES / 10);
        report("大数量图(数量至 int)", big, bigRequest, bigNativeNanos, bigDagNanos, bigNative, bigDag,
                bigStandard, 0);
    }

    /**
     * 副产物组:主体 ~50% 样板附带 1~2 个专属垃圾副产物,规模回到原生可完成档
     * (深度 11 ≈ 8e5 树节点)→ 双方逐字段 parity + 计时对比.
     */
    @Test
    public void byproductPatternsParityAtScale() {
        var graph = CrossCraftingGraphGenerator.generate(
                new CrossCraftingGraphGenerator.Params(11, 48, 3, 3, 3, 0.5, 0, 0, 42));
        long request = Math.max(1, TARGET_BYTES / Math.min(graph.unitCost(), TARGET_BYTES));
        var what = new GenericStack(graph.root(), request);

        long t0 = System.nanoTime();
        var nativePlan = graph.env().runSimulation(what, REPORT, 600_000);
        long nativeNanos = System.nanoTime() - t0;

        t0 = System.nanoTime();
        var dagPlan = graph.env().runDagSimulation(what, REPORT);
        long dagNanos = System.nanoTime() - t0;

        assertThat(nativePlan.simulation()).as("原生应可行").isFalse();
        assertThat(dagPlan.simulation()).as("DAG 应可行").isFalse();
        assertThat(dagPlan.finalOutput()).isEqualTo(nativePlan.finalOutput());
        // parity:含副产物样板下 DAG 与原生计划逐字段一致(bytes 为近似记账,不在比对范围)
        assertThat(dagPlan.patternTimes()).isEqualTo(nativePlan.patternTimes());
        assertThat(toMap(dagPlan.usedItems())).isEqualTo(toMap(nativePlan.usedItems()));
        assertThat(dagPlan.missingItems().isEmpty()).isTrue();
        assertThat(toMap(dagPlan.emittedItems())).isEqualTo(toMap(nativePlan.emittedItems()));

        long dagStandard = aeStandardBytes(dagPlan);
        assertThat(dagStandard).isBetween(MIN_BYTES, MAX_BYTES);
        report("副产物图(50% 样板带垃圾副产物)", graph, request, nativeNanos, dagNanos, nativePlan,
                dagPlan, dagStandard, 0);

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

    /** 计划诊断摘要:缺料总量与前几个缺料键、样板数、最终产出. */
    private static String diagnose(ICraftingPlan plan) {
        long missing = 0;
        var top = new StringBuilder();
        for (var entry : plan.missingItems()) {
            missing += entry.getLongValue();
            if (top.length() < 300) {
                top.append(entry.getKey()).append('x').append(entry.getLongValue()).append("; ");
            }
        }
        return "missing=" + missing + " [" + top + "], patterns=" + plan.patternTimes().size()
                + ", final=" + plan.finalOutput();
    }

    /** 原生墙钟超时(DNF)组的报告:原生耗时以超时预算为下限,加速比给下界. */
    private static void reportDnf(String label, CrossCraftingGraphGenerator.Generated graph, long request,
            long dagNanos, ICraftingPlan dagPlan, long standardBytes, long timeoutMs) {
        System.out.printf(
                "[Bench] %s: 普通样板=%,d, 子环=%d, 特殊=%d, 请求量=%,d, AE标准字节≈%.2fT%n",
                label, graph.normalPatterns(), graph.loopSeedKeys().size(), graph.dupSeedKeys().size(),
                request, standardBytes / 1e12);
        System.out.printf("[Bench]   原生: DNF(>%,d ms,已中断)%n", timeoutMs);
        System.out.printf(
                "[Bench]   DAG : %,d ms (plan.bytes=%,d)%n",
                dagNanos / 1_000_000, dagPlan.bytes());
        System.out.printf(
                "[Bench]   加速比: >%.1f×(按原生超时下限)%n",
                (double) (timeoutMs * 1_000_000L) / Math.max(1, dagNanos));
    }
}
