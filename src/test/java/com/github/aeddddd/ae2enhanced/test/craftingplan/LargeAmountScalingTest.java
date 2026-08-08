package com.github.aeddddd.ae2enhanced.test.craftingplan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.Items;

import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * 极大数量下单的缩放检查:图固定、只放大请求量,定位随数量异常恶化的位置.
 * <p>结论(静态+实证):</p>
 * <ul>
 * <li>普通链/混合特殊(DAG 闭式解、环求解器、边界求解器):整批记账,按量 O(1),平直;</li>
 * <li><b>容器物样板(limitsQuantity)</b>:原生单分支强制 times=1 逐份迭代
 * ({@code CraftingTreeProcess.request} 循环 O(数量))——JUnit 无 mixin 为原版行为;
 * 游戏内 mixin 仅接管"同 key 催化剂",DAG 引擎(DEFAULT)批量记账为 O(1);</li>
 * <li><b>多样板 key(nodes.size()>1)</b>:原生多分支每次合成都新建子库存、
 * {@code request(child,1)} 逐次迭代 + applyDiff——O(数量) 且逐次分配;
 * DAG 对该类 key 整单回落原生,极大数量下是<b>真实的下单陷阱</b>.</li>
 * </ul>
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class LargeAmountScalingTest {

    private static final CalculationStrategy REPORT = CalculationStrategy.REPORT_MISSING_ITEMS;

    /** 巨量库存:足够 1e12 订单消耗. */
    private static final long STOCK = 4_000_000_000_000L;

    @BeforeAll
    static void warmup() {
        var env = chainEnv();
        var what = new GenericStack(key(Items.STONE), 1000);
        env.runSimulation(what, REPORT);
        env.runDagSimulation(what, REPORT);
        var mixed = mixedEnv();
        mixed.runDagSimulation(new GenericStack(key(Items.STONE), 1000), REPORT);
    }

    private static AEItemKey key(net.minecraft.world.item.Item item) {
        return AEItemKey.of(item);
    }

    /** 普通链:R(stone) ← 1×A(dirt) ← 1×B(cobble),B 巨量库存. */
    private static SimulationEnv chainEnv() {
        var env = new SimulationEnv();
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(key(Items.STONE), 1))
                .addPreciseInput(1, new GenericStack(key(Items.DIRT), 1)).build());
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(key(Items.DIRT), 1))
                .addPreciseInput(1, new GenericStack(key(Items.COBBLESTONE), 1)).build());
        env.addStoredItem(key(Items.COBBLESTONE), STOCK);
        return env;
    }

    /** 混合小图:R(stone) ← 1×X(催化环产物) + 1×D(自引用链). */
    private static SimulationEnv mixedEnv() {
        var env = new SimulationEnv();
        var a = key(Items.WHEAT);
        var b = key(Items.CARROT);
        var x = key(Items.POTATO);
        var s = key(Items.BEETROOT);
        var d = key(Items.APPLE);
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(x, 1), new GenericStack(b, 1))
                .addPreciseInput(1, new GenericStack(a, 1)).build());
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(a, 1))
                .addPreciseInput(1, new GenericStack(b, 1)).build());
        env.addStoredItem(a, 1);
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(d, 1))
                .addPreciseInput(1, new GenericStack(s, 1)).build());
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(s, 2))
                .addPreciseInput(1, new GenericStack(s, 1)).build());
        env.addStoredItem(s, 1);
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(key(Items.STONE), 1))
                .addPreciseInput(1, new GenericStack(x, 1))
                .addPreciseInput(1, new GenericStack(d, 1)).build());
        return env;
    }

    /** 容器物链:R(stone) ← 1×X(glass_bottle) ← 1×蜂蜜瓶(容器返还). */
    private static SimulationEnv containerEnv() {
        var env = new SimulationEnv();
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(key(Items.GLASS_BOTTLE), 1))
                .addPreciseInput(1, true, new GenericStack(key(Items.HONEY_BOTTLE), 1)).build());
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(key(Items.STONE), 1))
                .addPreciseInput(1, new GenericStack(key(Items.GLASS_BOTTLE), 1)).build());
        env.addStoredItem(key(Items.HONEY_BOTTLE), STOCK);
        return env;
    }

    /** 多样板 key 链:R(stone) ← 1×M(iron_ingot),M 有两个候选样板(原料各异). */
    private static SimulationEnv multiPatternEnv() {
        var env = new SimulationEnv();
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(key(Items.IRON_INGOT), 1))
                .addPreciseInput(1, new GenericStack(key(Items.IRON_ORE), 1)).build());
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(key(Items.IRON_INGOT), 1))
                .addPreciseInput(1, new GenericStack(key(Items.RAW_IRON), 1)).build());
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(key(Items.STONE), 1))
                .addPreciseInput(1, new GenericStack(key(Items.IRON_INGOT), 1)).build());
        env.addStoredItem(key(Items.IRON_ORE), STOCK);
        env.addStoredItem(key(Items.RAW_IRON), STOCK);
        return env;
    }

    /**
     * 基线(普通链):双方均整批记账,1e4 → 1e12 应基本平直.
     */
    @Test
    public void normalChainScalesFlat() {
        var env = chainEnv();
        long[] amounts = { 10_000, 100_000_000, 1_000_000_000_000L };
        for (long amount : amounts) {
            var what = new GenericStack(key(Items.STONE), amount);
            long t0 = System.nanoTime();
            var nativePlan = env.runSimulation(what, REPORT, 300_000);
            long nativeMs = (System.nanoTime() - t0) / 1_000_000;
            t0 = System.nanoTime();
            var dagPlan = env.runDagSimulation(what, REPORT);
            long dagMs = (System.nanoTime() - t0) / 1_000_000;
            assertThat(nativePlan.simulation()).isFalse();
            assertThat(dagPlan.simulation()).isFalse();
            System.out.printf("[Scale] 普通链: 数量=%,d, 原生=%,d ms, DAG=%,d ms%n", amount, nativeMs, dagMs);
            assertThat(nativeMs).as("原生普通链应按量 O(1)").isLessThan(5_000);
            assertThat(dagMs).as("DAG 普通链应按量 O(1)").isLessThan(2_000);
        }
    }

    /**
     * 混合特殊(催化环 + 自引用):闭式解/环求解器应按量 O(1),1e4 → 1e12 平直.
     */
    @Test
    public void mixedSpecialScalesFlat() {
        var env = mixedEnv();
        long[] amounts = { 10_000, 100_000_000, 1_000_000_000_000L };
        for (long amount : amounts) {
            var what = new GenericStack(key(Items.STONE), amount);
            long t0 = System.nanoTime();
            var dagPlan = env.runDagSimulation(what, REPORT);
            long dagMs = (System.nanoTime() - t0) / 1_000_000;
            assertThat(dagPlan.simulation()).as("DAG 应可行").isFalse();
            System.out.printf("[Scale] 混合特殊: 数量=%,d, DAG=%,d ms%n", amount, dagMs);
            assertThat(dagMs).as("DAG 混合特殊应按量 O(1)").isLessThan(2_000);
        }
    }

    /**
     * 容器物样板:原生(JUnit=原版行为)逐份迭代 O(数量);DAG 批量记账 O(1).
     */
    @Test
    public void containerPatternNativeLinearDagFlat() {
        var env = containerEnv();
        // 原生:1e4/1e5/1e6 应呈线性(逐份)
        long prevMs = -1;
        for (long amount : new long[] { 10_000, 100_000, 1_000_000 }) {
            var what = new GenericStack(key(Items.STONE), amount);
            long t0 = System.nanoTime();
            var plan = env.runSimulation(what, REPORT, 300_000);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertThat(plan.simulation()).isFalse();
            System.out.printf("[Scale] 容器物(原生): 数量=%,d, 耗时=%,d ms%n", amount, ms);
            if (prevMs >= 0) {
                assertThat(ms).as("原生容器物应随数量线性恶化").isGreaterThan(prevMs * 3);
            }
            prevMs = ms;
        }
        // DAG:1e4 → 1e12 平直
        for (long amount : new long[] { 10_000, 1_000_000_000_000L }) {
            var what = new GenericStack(key(Items.STONE), amount);
            long t0 = System.nanoTime();
            var plan = env.runDagSimulation(what, REPORT);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertThat(plan.simulation()).as("DAG 容器物应可行").isFalse();
            System.out.printf("[Scale] 容器物(DAG): 数量=%,d, 耗时=%,d ms%n", amount, ms);
            assertThat(ms).as("DAG 容器物应按量 O(1)").isLessThan(2_000);
        }
    }

    /**
     * 多样板 key:原生多分支逐次迭代(每次新建子库存 + applyDiff),O(数量);
     * DAG 对该类 key 整单回落原生——极大数量下的真实下单陷阱.
     */
    @Test
    public void multiPatternKeyNativeLinear() {
        var env = multiPatternEnv();
        long prevMs = -1;
        long firstMs = -1;
        long msAt1e6 = -1;
        for (long amount : new long[] { 10_000, 100_000, 1_000_000 }) {
            var what = new GenericStack(key(Items.STONE), amount);
            long t0 = System.nanoTime();
            var plan = env.runSimulation(what, REPORT, 300_000);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertThat(plan.simulation()).isFalse();
            System.out.printf("[Scale] 多样板(原生/DAG回落): 数量=%,d, 耗时=%,d ms%n", amount, ms);
            if (prevMs >= 0) {
                assertThat(ms).as("多样板应随数量单调恶化").isGreaterThan(prevMs);
            } else {
                firstMs = ms;
            }
            prevMs = ms;
            if (amount == 1_000_000) {
                msAt1e6 = ms;
            }
        }
        // 端点比值:1e4 → 1e6 数量放大 100×,耗时应显著放大(线性证据)
        assertThat(msAt1e6).as("多样板 1e4→1e6 耗时比").isGreaterThan(firstMs * 3);
        // 外推:1e10 量级订单的预计耗时(按 1e6 实测线性外推)
        double hoursAt1e10 = msAt1e6 / 1e6 * 1e10 / 1000.0 / 3600.0;
        System.out.printf("[Scale] 多样板外推: 1e6 实测 %,d ms(%.2f μs/次), 数量=1e10 预计 ≈%.1f 小时(不可下单)%n",
                msAt1e6, msAt1e6 * 1000.0 / 1e6, hoursAt1e10);
    }
}
