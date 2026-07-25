package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * H 组:复杂组合场景——自引用与循环链并存、环外输入子合成、分数速率、
 * 多环竞争、ceil 边界、天文数字、流体循环、候选迭代等.
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class ComplexScenarioTest {

    /** H1:自引用样板与循环链并存时,自引用(阶段 1)优先接管. */
    @Test
    public void testSelfRefTakesPriorityOverCycle() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var dup = env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        var p0 = env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addStoredItem(stone);

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(dup, 10L)) // 只用自引用样板,不走循环链
                .usedMatch(stone)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** H2:循环链的环外输入本身需要子合成(不含环成员)→ 原生子合成正常展开. */
    @Test
    public void testCycleWithCraftableExternalInput() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var dirt = item(Items.DIRT);
        var sand = item(Items.SAND);
        var p0 = env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2))
                .addPreciseInput(1, stone)
                .addPreciseInput(1, dirt)
                .build());
        var p1 = env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        var pDirt = env.addPattern(new ProcessingPatternBuilder(mult(dirt, 2)).addPreciseInput(1, sand).build());
        env.addStoredItem(stone); // 种子
        env.addStoredItem(mult(sand, 4)); // 无 dirt 库存,需从 sand 子合成

        var plan = env.runSpecialSimulation(mult(stone, 2), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(p0, 2L, p1, 4L, pDirt, 1L))
                .usedMatch(stone, sand)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** H3:三键环 + 分数速率 + 每轮消耗辅材:A+W→2B,B→C,2C→3A → t=[1,2,1],净产 2A/轮. */
    @Test
    public void testThreeKeyCycleWithRationalRatesAndAuxInput() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var dirt = item(Items.DIRT);
        var p0 = env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2))
                .addPreciseInput(1, stone)
                .addPreciseInput(1, dirt)
                .build());
        var p1 = env.addPattern(new ProcessingPatternBuilder(sand).addPreciseInput(1, cobble).build());
        var p2 = env.addPattern(new ProcessingPatternBuilder(mult(stone, 3)).addPreciseInput(2, sand).build());
        env.addStoredItem(stone); // 种子
        env.addStoredItem(mult(dirt, 10)); // 辅材

        var plan = env.runSpecialSimulation(mult(stone, 4), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(p0, 2L, p1, 4L, p2, 2L)) // 2 轮 × [1,2,1]
                .usedMatch(stone, mult(dirt, 2))
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** H4:中性环与增殖环并存(同一请求物)→ 中性环跳过,增殖环接管. */
    @Test
    public void testNeutralCycleSkippedForProductiveOne() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build()); // 中性环 A↔B
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        var p2 = env.addPattern(new ProcessingPatternBuilder(mult(sand, 2)).addPreciseInput(1, stone).build());
        var p3 = env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, sand).build());
        env.addStoredItem(stone);

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(p2, 10L, p3, 20L)) // 只走增殖环
                .usedMatch(stone)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** H5:两个仅共享 root 的独立增殖环 → 并集 m≠n 返回 null,逐环迭代成功. */
    @Test
    public void testTwoDisjointCyclesUnionRejectedButIterationSolves() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var p0 = env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, stone).build());
        var p1 = env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addPattern(new ProcessingPatternBuilder(mult(sand, 3)).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, sand).build());
        env.addStoredItem(stone);

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(p0, 10L, p1, 20L)) // 第一个候选环(发现序)求解成功
                .usedMatch(stone)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** H6:请求量非净增益整数倍 → ceil 多转一轮,余量执行结束返回网络. */
    @Test
    public void testTargetNotMultipleOfNetGain() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var dirt = item(Items.DIRT);
        var p1 = env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        var p2 = env.addPattern(new ProcessingPatternBuilder(mult(sand, 64))
                .addPreciseInput(16, stone)
                .addPreciseInput(16, cobble)
                .addPreciseInput(1, dirt)
                .build());
        var p3 = env.addPattern(new ProcessingPatternBuilder(mult(stone, 64))
                .addPreciseInput(64, sand)
                .addPreciseInput(1, dirt)
                .build());
        env.addStoredItem(mult(stone, 64)); // 全批次种子 32×2
        env.addStoredItem(mult(dirt, 100));

        var plan = env.runSpecialSimulation(mult(stone, 33), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(p1, 32L, p2, 2L, p3, 2L)) // ceil(33/32)=2 轮,与请求 64 相同
                .usedMatch(mult(stone, 64), mult(dirt, 4))
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** H7:环路径天文数字订单 → O(1) 缺料计划(不逐份展开,不溢出). */
    @Test
    public void testAstronomicalCycleOrderFallsBackToMissing() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addStoredItem(stone);

        var plan = env.runSpecialSimulation(mult(stone, Long.MAX_VALUE - 1),
                CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan).failed();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isFalse();
    }

    /** H8:流体循环链:1000mb 水→2000mb 岩浆,1000mb 岩浆→1000mb 水(增殖环). */
    @Test
    public void testFluidCycle() {
        var env = new SimulationEnv();
        var water = fluid(Fluids.WATER, 1000);
        var lava = fluid(Fluids.LAVA, 1000);
        var p0 = env.addPattern(new ProcessingPatternBuilder(mult(lava, 2)).addPreciseInput(1, water).build());
        var p1 = env.addPattern(new ProcessingPatternBuilder(water).addPreciseInput(1, lava).build());
        env.addStoredItem(water); // 种子 1000mb

        var plan = env.runSpecialSimulation(mult(water, 2), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(p0, 2L, p1, 4L))
                .usedMatch(water)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** H9:多产物自引用样板(1A→2A+1B)请求 A → 阶段 1 闭式解,副产物 B 不计消耗. */
    @Test
    public void testMultiOutputSelfRef() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var stick = item(Items.STICK);
        var dup = env.addPattern(new ProcessingPatternBuilder(mult(stone, 2), stick)
                .addPreciseInput(1, stone)
                .build());
        env.addStoredItem(stone);

        var plan = env.runSpecialSimulation(mult(stone, 4), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(dup, 4L))
                .usedMatch(stone)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** H12:广义自引用候选迭代——第一个候选种子不足,第二个可解(主产出均为请求物,符合 AE2 索引语义). */
    @Test
    public void testGeneralSelfRefCandidateIteration() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var dirt = item(Items.DIRT);
        var stick = item(Items.STICK);
        // B+A ← A 形式(主产出 B,催化剂 A 返还):p1 需 stone 种子,p2 需 dirt 种子
        env.addPattern(new ProcessingPatternBuilder(stick, stone).addPreciseInput(1, stone).build());
        var p2 = env.addPattern(new ProcessingPatternBuilder(stick, dirt).addPreciseInput(1, dirt).build());
        env.addStoredItem(dirt); // 只有 dirt 种子

        var plan = env.runSpecialSimulation(mult(stick, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(p2, 10L))
                .usedMatch(dirt)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    // ===== 断言辅助 =====

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack fluid(Fluid fluid, int amountMb) {
        return new GenericStack(AEFluidKey.of(fluid), amountMb);
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }

    private static CraftingPlanAssert assertThatPlan(ICraftingPlan plan) {
        return new CraftingPlanAssert(plan);
    }

    private static class CraftingPlanAssert {
        private final ICraftingPlan plan;

        private CraftingPlanAssert(ICraftingPlan plan) {
            this.plan = Objects.requireNonNull(plan);
        }

        CraftingPlanAssert succeeded() {
            assertThat(plan.simulation()).isFalse();
            assertThat(plan.missingItems()).isEmpty();
            return this;
        }

        CraftingPlanAssert failed() {
            assertThat(plan.simulation()).isTrue();
            assertThat(plan.missingItems()).isNotEmpty();
            return this;
        }

        CraftingPlanAssert patternsMatch(Map<IPatternDetails, Long> patternTimes) {
            assertThat(plan.patternTimes()).isEqualTo(patternTimes);
            return this;
        }

        CraftingPlanAssert listMatches(KeyCounter actualList, GenericStack... expectedStacks) {
            var expectedList = new KeyCounter();
            for (var stack : expectedStacks) {
                expectedList.add(stack.what(), stack.amount());
            }
            assertThat(actualList.size()).isEqualTo(expectedList.size());
            for (var expected : expectedList) {
                assertThat(actualList.get(expected.getKey())).isEqualTo(expected.getLongValue());
            }
            return this;
        }

        CraftingPlanAssert missingMatch(GenericStack... stacks) {
            return listMatches(plan.missingItems(), stacks);
        }

        CraftingPlanAssert usedMatch(GenericStack... stacks) {
            return listMatches(plan.usedItems(), stacks);
        }
    }
}
