package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraft;

/**
 * 天文数字需求回归测试(移植自 master 分支 ab7710 修复的对应场景):
 * 近 {@link Long#MAX_VALUE} 的需求下,环/自引用边界求解的轮数、贷款量、产出量
 * 均可超 long 表示——修复前此类订单会走"(a+b-1)/b 回绕成负数 → 误判求解失败 →
 * 整单回落原生 → 大网络计算卡死"的脏路径;修复后边界求解返回
 * {@code BoundaryResult.MISSING},执行器就地 O(1) 记缺料:
 * <ul>
 * <li>计划 O(1) 完成(&lt;2s,无回落卡死);</li>
 * <li>simulation=true,缺料 = 边界 key × 全额需求;</li>
 * <li>usedItems 不含边界 key 种子(MISSING 不做任何提取/合成)——
 * 这是与"回落原生"路径的判别特征(原生会提走种子).</li>
 * </ul>
 */
@BootstrapMinecraft
class ExtremeDemandTest {

    private static final CalculationStrategy REPORT = CalculationStrategy.REPORT_MISSING_ITEMS;
    private static final long MAX = Long.MAX_VALUE;

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }

    /** 边界自引用(1S→2S,深层 D←1S,R←1D),天文需求 → O(1) 缺料. */
    @Test
    void testSelfDupBoundaryAstronomicalDemandMissing() {
        var env = new SimulationEnv();
        var r = item(Items.STONE);
        var d = item(Items.DIRT);
        var s = item(Items.WHEAT);
        env.addPattern(new ProcessingPatternBuilder(r).addPreciseInput(1, d).build());
        env.addPattern(new ProcessingPatternBuilder(d).addPreciseInput(1, s).build());
        env.addPattern(new ProcessingPatternBuilder(mult(s, 2)).addPreciseInput(1, s).build());
        env.addStoredItem(s); // 种子 1

        long t0 = System.nanoTime();
        var plan = env.runDagSimulation(new GenericStack(r.what(), MAX), REPORT);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertThat(plan.simulation()).as("天文边界需求 → 缺料计划").isTrue();
        assertThat(plan.missingItems().get(AEItemKey.of(Items.WHEAT))).isEqualTo(MAX);
        assertThat(plan.usedItems().get(AEItemKey.of(Items.WHEAT)))
                .as("MISSING 路径不提走种子(与回落原生相区别)").isZero();
        assertThat(ms).as("应 O(1) 完成,不回落原生").isLessThan(2_000);
    }

    /** 催化环边界(X 为环外副产物,1A→2X+1B、1B→1A),天文需求 → O(1) 缺料. */
    @Test
    void testCatalyticBoundaryAstronomicalDemandMissing() {
        var env = new SimulationEnv();
        var r = item(Items.STONE);
        var x = item(Items.POTATO);
        var a = item(Items.WHEAT);
        var b = item(Items.CARROT);
        env.addPattern(new ProcessingPatternBuilder(r).addPreciseInput(1, x).build());
        env.addPattern(new ProcessingPatternBuilder(mult(x, 2), b).addPreciseInput(1, a).build());
        env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, b).build());
        env.addStoredItem(a); // 种子 1A

        long t0 = System.nanoTime();
        var plan = env.runDagSimulation(new GenericStack(r.what(), MAX), REPORT);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertThat(plan.simulation()).as("天文催化边界需求 → 缺料计划").isTrue();
        assertThat(plan.missingItems().get(AEItemKey.of(Items.POTATO))).isEqualTo(MAX);
        assertThat(plan.usedItems().get(AEItemKey.of(Items.WHEAT)))
                .as("MISSING 路径不提走种子").isZero();
        assertThat(ms).as("应 O(1) 完成,不回落原生").isLessThan(2_000);
    }

    /** 增殖两节点环边界(1A→1B、1B→2A),天文需求 → O(1) 缺料(IO 侧守卫触发 OVERFLOW). */
    @Test
    void testProductiveCycleBoundaryAstronomicalDemandMissing() {
        var env = new SimulationEnv();
        var r = item(Items.STONE);
        var a = item(Items.WHEAT);
        var b = item(Items.CARROT);
        env.addPattern(new ProcessingPatternBuilder(r).addPreciseInput(1, a).build());
        env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build());
        env.addPattern(new ProcessingPatternBuilder(mult(a, 2)).addPreciseInput(1, b).build());
        env.addStoredItem(a); // 种子 1A

        long t0 = System.nanoTime();
        var plan = env.runDagSimulation(new GenericStack(r.what(), MAX), REPORT);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertThat(plan.simulation()).as("天文增殖环边界需求 → 缺料计划").isTrue();
        assertThat(plan.missingItems().get(AEItemKey.of(Items.WHEAT))).isEqualTo(MAX);
        assertThat(plan.usedItems().get(AEItemKey.of(Items.WHEAT)))
                .as("MISSING 路径不提走种子").isZero();
        assertThat(ms).as("应 O(1) 完成,不回落原生").isLessThan(2_000);
    }

    /** 根路径自引用 1→2:旧 inPer 侧守卫漏掉的产出溢出形态(1×MAX 不超,2×MAX 超). */
    @Test
    void testRootSelfDupUnitInputAstronomicalDemandMissing() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        env.addStoredItem(stone); // 种子 1

        long t0 = System.nanoTime();
        var plan = env.runSpecialSimulation(new GenericStack(stone.what(), MAX), REPORT);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertThat(plan.simulation()).as("天文根需求 → 缺料计划").isTrue();
        assertThat(plan.missingItems().get(AEItemKey.of(Items.STONE))).isEqualTo(MAX);
        assertThat(ms).as("应 O(1) 完成").isLessThan(2_000);
    }
}
