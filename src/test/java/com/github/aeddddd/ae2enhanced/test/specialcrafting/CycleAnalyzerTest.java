package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.stacks.GenericStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CycleAnalyzer;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraft;

/**
 * F 组:跨样板循环链分析器(CycleAnalyzer)纯单元测试.
 */
@BootstrapMinecraft
public class CycleAnalyzerTest {

    /** F1:无环 → null. */
    @Test
    public void testNoCycle() {
        var env = new SimulationEnv();
        var cobble = item(Items.COBBLESTONE);
        var stone = item(Items.STONE);
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());

        assertThat(CycleAnalyzer.findCycle(env.craftingService(), stone.what())).isNull();
    }

    /** F2:仅自引用(阶段 1 范围)→ 不识别为跨样板环. */
    @Test
    public void testSelfRefNotACycle() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());

        assertThat(CycleAnalyzer.findCycle(env.craftingService(), stone.what())).isNull();
    }

    /** F3:两节点增殖环 A→2B,B→A → PRODUCTIVE,数值正确. */
    @Test
    public void testTwoNodeProductiveCycle() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());

        var cycle = CycleAnalyzer.findCycle(env.craftingService(), stone.what());
        assertThat(cycle).isNotNull();
        assertThat(cycle).hasSize(2);

        var analysis = CycleAnalyzer.analyze(cycle);
        assertThat(analysis).isNotNull();
        assertThat(analysis.rateClass()).isEqualTo(CycleAnalyzer.RateClass.PRODUCTIVE);
        assertThat(analysis.timesPerRound()).containsExactly(1, 2);
        assertThat(analysis.netGain()).isEqualTo(1);
        assertThat(analysis.seed()).isEqualTo(1);
    }

    /** F4:三节点增殖环 A→B,B→C,C→2A. */
    @Test
    public void testThreeNodeProductiveCycle() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var dirt = item(Items.DIRT);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(dirt).addPreciseInput(1, cobble).build());
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, dirt).build());

        var cycle = CycleAnalyzer.findCycle(env.craftingService(), stone.what());
        assertThat(cycle).isNotNull();
        assertThat(cycle).hasSize(3);

        var analysis = CycleAnalyzer.analyze(cycle);
        assertThat(analysis).isNotNull();
        assertThat(analysis.rateClass()).isEqualTo(CycleAnalyzer.RateClass.PRODUCTIVE);
        assertThat(analysis.timesPerRound()).containsExactly(1, 1, 1);
        assertThat(analysis.netGain()).isEqualTo(1);
        assertThat(analysis.seed()).isEqualTo(1);
    }

    /** F5:存在无关普通样板时仍能发现环. */
    @Test
    public void testCycleFoundWithExtraPatterns() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var dirt = item(Items.DIRT);
        env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, dirt).build());

        assertThat(CycleAnalyzer.findCycle(env.craftingService(), stone.what())).isNotNull();
    }

    /** F6:中性环(净率 = 1)→ NEUTRAL. */
    @Test
    public void testNeutralCycle() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());

        var cycle = CycleAnalyzer.findCycle(env.craftingService(), stone.what());
        assertThat(cycle).isNotNull();
        var analysis = CycleAnalyzer.analyze(cycle);
        assertThat(analysis).isNotNull();
        assertThat(analysis.rateClass()).isEqualTo(CycleAnalyzer.RateClass.NEUTRAL);
    }

    /** F7:耗散环(净率 < 1)→ DISSIPATIVE. */
    @Test
    public void testDissipativeCycle() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(2, cobble).build());

        var cycle = CycleAnalyzer.findCycle(env.craftingService(), stone.what());
        assertThat(cycle).isNotNull();
        var analysis = CycleAnalyzer.analyze(cycle);
        assertThat(analysis).isNotNull();
        assertThat(analysis.rateClass()).isEqualTo(CycleAnalyzer.RateClass.DISSIPATIVE);
    }

    /** F8:非简单环(样板消耗两种环内物品)→ analyze 返回 null. */
    @Test
    public void testNonSimpleCycleRejected() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        // P0:1A + 1B → 2B(消耗两种环内物品);P1:1B → 1A
        env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2))
                .addPreciseInput(1, stone)
                .addPreciseInput(1, cobble)
                .build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());

        var cycle = CycleAnalyzer.findCycle(env.craftingService(), stone.what());
        // 环可能被找到,但分析必须拒绝(净率无法按简单环闭式求解)
        if (cycle != null) {
            assertThat(CycleAnalyzer.analyze(cycle)).isNull();
        }
    }

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }
}
