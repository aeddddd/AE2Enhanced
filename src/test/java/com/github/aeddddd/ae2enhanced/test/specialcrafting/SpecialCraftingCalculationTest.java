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
 * B/C 组:特殊配方求解器(SpecialCraftingCalculation)测试.
 * <p>B 组验证路由透明性(detector 未命中时与原生逐字段一致);
 * C 组验证净产出自引用的闭式解、种子语义、多分支与溢出回落.
 * 每个特殊用例的断言满足守恒不变量:交付量 = 请求量,网络消耗 = 种子 + 非自输入.</p>
 */
@BootstrapMinecraft
public class SpecialCraftingCalculationTest {

    /** B1:detector 未命中 → 特殊计算器走 super.run(),与原生计划逐字段一致. */
    @Test
    public void testPassthroughMatchesNative() {
        var env = new SimulationEnv();
        var cobble = item(Items.COBBLESTONE);
        var stone = item(Items.STONE);
        var pattern = env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addStoredItem(mult(cobble, 100));

        var nativePlan = env.runSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        var specialPlan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);

        assertThat(specialPlan.simulation()).isEqualTo(nativePlan.simulation());
        assertThat(specialPlan.finalOutput()).isEqualTo(nativePlan.finalOutput());
        assertThat(specialPlan.bytes()).isEqualTo(nativePlan.bytes());
        assertThat(specialPlan.patternTimes()).isEqualTo(nativePlan.patternTimes());
        assertThat(specialPlan.multiplePaths()).isEqualTo(nativePlan.multiplePaths());
        assertCounterEquals(nativePlan.usedItems(), specialPlan.usedItems());
        assertCounterEquals(nativePlan.emittedItems(), specialPlan.emittedItems());
        assertCounterEquals(nativePlan.missingItems(), specialPlan.missingItems());
        // 原生计划不得被标记为特殊计划
        assertThat(SpecialPlanMarker.isSpecial(specialPlan)).isFalse();
    }

    /** C1:1→2 净增殖,唯一候选,有种子 → 闭式解成功,种子计入 usedItems. */
    @Test
    public void testSelfRefClosedFormWithSeed() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var dup = env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        env.addStoredItem(stone); // 种子 1

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(dup, 10)
                .usedMatch(stone)
                .missingMatch();
        // 守恒:交付 10 = 净产出 10×(2-1),网络仅消耗 1 种子
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** C2:1→2 净增殖,无种子 → 回落原生,报缺料(不凭空增殖). */
    @Test
    public void testSelfRefWithoutSeedFallsBackToMissing() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan).failed();
        // 原生兜底:自引用样板 limitQty 逐份展开,缺失物品必须为 stone(具体数量随原生展开细节)
        assertThat(plan.missingItems().get(stone.what())).isGreaterThanOrEqualTo(1);
        assertThat(SpecialPlanMarker.isSpecial(plan)).isFalse();
    }

    /** C3:A+2B→2A(锻造模板型),种子 + 充足 B → 成功,B 按份数消耗. */
    @Test
    public void testSmithingTemplateStyleDuplication() {
        var env = new SimulationEnv();
        var diamond = item(Items.DIAMOND);
        var stick = item(Items.STICK);
        var dup = env.addPattern(new ProcessingPatternBuilder(mult(diamond, 2))
                .addPreciseInput(1, diamond)
                .addPreciseInput(2, stick)
                .build());
        env.addStoredItem(diamond); // 种子 1
        env.addStoredItem(mult(stick, 100));

        var plan = env.runSpecialSimulation(mult(diamond, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(dup, 10)
                .usedMatch(diamond, mult(stick, 20))
                .missingMatch();
    }

    /** C4:库存超出种子部分先交付,边界无 off-by-one. */
    @Test
    public void testStockBeyondSeedDeliveredFirst() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var dup = env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        env.addStoredItem(mult(stone, 5)); // 种子 1 + 可交付 4

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(dup, 6) // 10 - 4 库存 = 6 份净产出
                .usedMatch(mult(stone, 5)) // 4 交付 + 1 种子
                .missingMatch();
    }

    /** C5:多分支(自引用 + 普通),无种子 → 回落原生走普通分支. */
    @Test
    public void testMultiBranchWithoutSeedUsesNormalBranch() {
        var env = new SimulationEnv();
        var cobble = item(Items.COBBLESTONE);
        var stone = item(Items.STONE);
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        var normal = env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addStoredItem(mult(cobble, 5));

        var plan = env.runSpecialSimulation(mult(stone, 5), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(normal, 5)
                .usedMatch(mult(cobble, 5));
        assertThat(SpecialPlanMarker.isSpecial(plan)).isFalse();
    }

    /** C6:多分支,有种子 → 自引用增殖优先,标记 multiplePaths. */
    @Test
    public void testMultiBranchWithSeedPrefersSelfRef() {
        var env = new SimulationEnv();
        var cobble = item(Items.COBBLESTONE);
        var stone = item(Items.STONE);
        var dup = env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addStoredItem(stone); // 种子 1
        env.addStoredItem(mult(cobble, 5));

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(dup, 10)
                .usedMatch(stone);
        assertThat(plan.multiplePaths()).isTrue();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** C7:催化剂型(A→A+B,无净产出)不在阶段 1 范围 → 原生行为(缺料失败). */
    @Test
    public void testCatalystPatternFallsBackToNative() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var stick = item(Items.STICK);
        env.addPattern(new ProcessingPatternBuilder(stone, stick).addPreciseInput(1, stone).build());
        env.addStoredItem(stone); // 即使有库存,原生 ignore(output) 也不交付

        var plan = env.runSpecialSimulation(stone, CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .failed()
                .missingMatch(stone);
        assertThat(SpecialPlanMarker.isSpecial(plan)).isFalse();
    }

    /** C9:天文数字订单(贷款量溢出 long)→ 回落原生,不静默截断. */
    @Test
    public void testAstronomicalOrderFallsBack() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        // 2→3:inPer=2,request Long.MAX_VALUE 时 crafts > Long.MAX_VALUE/2 → 溢出回落
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 3)).addPreciseInput(2, stone).build());
        env.addStoredItem(mult(stone, 2));

        var plan = env.runSpecialSimulation(new GenericStack(stone.what(), Long.MAX_VALUE),
                CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan).failed();
        assertThat(SpecialPlanMarker.isSpecial(plan)).isFalse();
    }

    // ===== 断言辅助 =====

    private static void assertCounterEquals(KeyCounter expected, KeyCounter actual) {
        assertThat(actual.size()).isEqualTo(expected.size());
        for (var entry : expected) {
            assertThat(actual.get(entry.getKey())).isEqualTo(entry.getLongValue());
        }
    }

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

        CraftingPlanAssert patternsMatch(IPatternDetails p1, long t1) {
            assertThat(plan.patternTimes()).isEqualTo(Map.of(p1, t1));
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
