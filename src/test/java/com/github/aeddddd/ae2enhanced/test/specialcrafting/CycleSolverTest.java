package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraft;

/**
 * G 组:跨样板循环链求解(CycleSolver 经 SpecialCraftingCalculation)测试.
 * <p>守恒不变量:交付量 = 请求量,网络消耗 = 种子 + 环外输入.</p>
 */
@BootstrapMinecraft
public class CycleSolverTest {

    /** G1:两节点增殖环 A→2B,B→A,有种子 → 闭式解成功. */
    @Test
    public void testTwoNodeCycleWithSeed() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var p0 = env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, stone).build());
        var p1 = env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addStoredItem(stone); // 种子 1

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(p0, 10L, p1, 20L))
                .usedMatch(stone) // 仅消耗 1 种子
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** G2:增殖环无种子 → 回落原生,报缺料(不凭空增殖). */
    @Test
    public void testCycleWithoutSeedFallsBack() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan).failed();
        // 原生兜底:环被剪枝,中间产物 cobble 不可合成 → 缺失物品为 cobble
        assertThat(plan.missingItems().get(cobble.what())).isGreaterThanOrEqualTo(1);
        assertThat(SpecialPlanMarker.isSpecial(plan)).isFalse();
    }

    /** G3:中性环 → 不接管,原生行为(缺料失败). */
    @Test
    public void testNeutralCycleFallsBack() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addStoredItem(stone);

        var plan = env.runSpecialSimulation(mult(stone, 5), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan).failed();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isFalse();
    }

    /** G4:耗散环 → 不接管,原生行为(缺料失败). */
    @Test
    public void testDissipativeCycleFallsBack() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(2, cobble).build());
        env.addStoredItem(stone);

        var plan = env.runSpecialSimulation(mult(stone, 5), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan).failed();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isFalse();
    }

    /** G5:环带环外输入 A+C→2B,B→A → 环外输入按份数消耗. */
    @Test
    public void testCycleWithExternalInput() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var dirt = item(Items.DIRT);
        var p0 = env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2))
                .addPreciseInput(1, stone)
                .addPreciseInput(1, dirt)
                .build());
        var p1 = env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addStoredItem(stone); // 种子 1
        env.addStoredItem(mult(dirt, 100));

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(p0, 10L, p1, 20L))
                .usedMatch(stone, mult(dirt, 10))
                .missingMatch();
    }

    /** G6:分数速率超轮缩放 A→1B,2B→3A → times=[2,1],种子 2. */
    @Test
    public void testRationalRateSuperRound() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var p0 = env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        var p1 = env.addPattern(new ProcessingPatternBuilder(mult(stone, 3)).addPreciseInput(2, cobble).build());
        env.addStoredItem(mult(stone, 2)); // 种子 2

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(Map.of(p0, 20L, p1, 10L))
                .usedMatch(mult(stone, 2))
                .missingMatch();
    }

    // ===== 断言辅助 =====

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
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
