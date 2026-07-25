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
        assertThat(analysis.seedsPerKey()).containsExactly(1, 0);
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
        assertThat(analysis.seedsPerKey()).containsExactly(1, 0, 0);
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

    /**
     * F8:样板消耗多种环内物品但净率为 1(A+B→2B,B→A:每轮 A 净变化为 0)
     * → 泛化引擎可分析(非简单检查已移除),分类为 NEUTRAL,不接管.
     */
    @Test
    public void testMultiCycleKeyInputNeutralCycle() {
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
        assertThat(cycle).isNotNull();
        var analysis = CycleAnalyzer.analyze(cycle);
        assertThat(analysis).isNotNull();
        assertThat(analysis.rateClass()).isEqualTo(CycleAnalyzer.RateClass.NEUTRAL);
    }

    /**
     * F9(用户案例):A→B,16A+16B+1W→64C,64C+1W→64A.
     * 样板同时消耗两种环内物品 → 线性平衡解 t=[16,1,1],每轮净产 32A,种子 32A.
     */
    @Test
    public void testUserCaseMultiInputCycle() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var dirt = item(Items.DIRT);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(mult(sand, 64))
                .addPreciseInput(16, stone)
                .addPreciseInput(16, cobble)
                .addPreciseInput(1, dirt)
                .build());
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 64))
                .addPreciseInput(64, sand)
                .addPreciseInput(1, dirt)
                .build());

        // 应找到最长的三键环(而非 {A,C} 两键短环)
        var cycles = CycleAnalyzer.findCyclesThrough(env.craftingService(), stone.what());
        assertThat(cycles).isNotEmpty();
        assertThat(cycles.get(0)).hasSize(3);

        var analysis = CycleAnalyzer.analyze(cycles.get(0));
        assertThat(analysis).isNotNull();
        assertThat(analysis.rateClass()).isEqualTo(CycleAnalyzer.RateClass.PRODUCTIVE);
        assertThat(analysis.timesPerRound()).containsExactly(16, 1, 1);
        assertThat(analysis.netGain()).isEqualTo(32);
        assertThat(analysis.seedsPerKey()).containsExactly(32, 0, 0);
        // A 被 P1、P2 两个步骤消耗(多消费者键)→ 全批次保守种子 = 每轮总消耗 32;
        // B、C 单消费者 → 0(前缀种子+贷款在运行时对任意推送顺序安全)
        assertThat(analysis.batchSeedPerKey()).containsExactly(32, 0, 0);
    }

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }
}
