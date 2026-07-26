package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * X 组:执行层语义自动化测试(ExecutionHarness).
 * <p>核心断言:① 任务完成;② CPU 无残留(收官后清空);③ 网络守恒
 * (期末 = 期初 + 净产出 - 交付);④ 对全部推送排列成立.</p>
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class ExecutionHarnessTest {

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }

    private static Map<AEKey, Long> stock(GenericStack... stacks) {
        Map<AEKey, Long> map = new LinkedHashMap<>();
        for (var stack : stacks) {
            map.merge(stack.what(), stack.amount(), Long::sum);
        }
        return map;
    }

    /**
     * 对全部推送排列执行并断言:完成、无死锁、CPU 无残留、交付足额、网络期末精确匹配.
     */
    private static void assertExecutesCleanly(ICraftingPlan plan, Map<AEKey, Long> network,
            List<IPatternDetails> patterns, long expectedDelivered, Map<AEKey, Long> expectedNetworkEnd) {
        assertThat(plan.simulation()).as("计划应成功").isFalse();
        for (var order : ExecutionHarness.pushOrders(patterns)) {
            var result = ExecutionHarness.execute(plan, network, ExecutionHarness.Options.gameDefaults(), order);
            assertThat(result.completed())
                    .as("订单应完成 [order=%s, ticks=%d, deadlock=%s, cpu=%s, network=%s]",
                            patterns.stream().map(order::indexOf).toList(), result.ticks(), result.deadlock(),
                            result.cpuInventory(), result.network())
                    .isTrue();
            assertThat(result.deadlock()).isFalse();
            assertThat(result.cpuInventory()).as("CPU 收官后必须无残留")
                    .allSatisfy((k, v) -> assertThat(v).as("CPU 残留 %s", k).isZero());
            assertThat(result.delivered()).isEqualTo(expectedDelivered);
            for (var entry : expectedNetworkEnd.entrySet()) {
                assertThat(result.network().getOrDefault(entry.getKey(), 0L))
                        .as("网络期末 %s", entry.getKey())
                        .isEqualTo(entry.getValue());
            }
        }
    }

    /** X1:自引用复制(1A→2A)——种子 1,请求 10,执行后种子完整返还. */
    @Test
    public void testSelfRefDuplicationExecution() {
        var stone = item(Items.STONE);
        var env = new SimulationEnv();
        var dup = env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        env.addStoredItem(stone);

        assertExecutesCleanly(
                env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS),
                stock(stone), List.of(dup), 10, stock(stone));
    }

    /** X2:θ 循环 ×100——每轮种子(2A+1C)即可,全排列完成且守恒. */
    @Test
    public void testThetaX100AllOrders() {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var env = new SimulationEnv();
        var crush = env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        var charge = env.addPattern(new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        var back = env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 2)); // 每轮种子
        env.addStoredItem(sand);

        assertExecutesCleanly(
                env.runSpecialSimulation(mult(stone, 100), CalculationStrategy.REPORT_MISSING_ITEMS),
                stock(mult(stone, 2), sand), List.of(crush, charge, back), 100,
                stock(mult(stone, 2), sand)); // 种子原样返还,净产 200 - 交付 100 - 消耗 100 = 0
    }

    /** X3:θ 循环 ×1000(500 轮)——配额调度下串行预算也完成. */
    @Test
    public void testThetaX1000SerialBudget() {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var env = new SimulationEnv();
        var crush = env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        var charge = env.addPattern(new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        var back = env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 2));
        env.addStoredItem(sand);

        var plan = env.runSpecialSimulation(mult(stone, 1000), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThat(plan.simulation()).isFalse();
        var result = ExecutionHarness.execute(plan, stock(mult(stone, 2), sand),
                ExecutionHarness.Options.gameDefaults().serial(), List.of(crush, charge, back));
        assertThat(result.completed()).isTrue();
        assertThat(result.delivered()).isEqualTo(1000);
        assertThat(result.network().getOrDefault(stone.what(), 0L)).isEqualTo(2);
    }

    /** X4:用户 ABC 案例(16A+16B+W→64C,64C+W→64A)——种子 32A,守恒. */
    @Test
    public void testUserAbcCaseExecution() {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var dirt = item(Items.DIRT);
        var env = new SimulationEnv();
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
        env.addStoredItem(mult(stone, 32));
        env.addStoredItem(mult(dirt, 100));

        assertExecutesCleanly(
                env.runSpecialSimulation(mult(stone, 64), CalculationStrategy.REPORT_MISSING_ITEMS),
                stock(mult(stone, 32), mult(dirt, 100)), List.of(p1, p2, p3), 64,
                stock(mult(stone, 32), mult(dirt, 96))); // 种子返还,W 净消耗 4
    }

    /** X5:催化剂 X≠Y(B+A←A)——催化剂完整返还,目标全额交付. */
    @Test
    public void testCatalystExecution() {
        var dirt = item(Items.DIRT);
        var stick = item(Items.STICK);
        var env = new SimulationEnv();
        var p = env.addPattern(new ProcessingPatternBuilder(stick, dirt).addPreciseInput(1, dirt).build());
        env.addStoredItem(dirt);

        assertExecutesCleanly(
                env.runSpecialSimulation(mult(stick, 10), CalculationStrategy.REPORT_MISSING_ITEMS),
                stock(dirt), List.of(p), 10, stock(dirt));
    }

    /** X6:三键分数速率环 + 每轮辅材(A+W→2B,B→C,2C→3A). */
    @Test
    public void testRationalCycleWithAuxExecution() {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var dirt = item(Items.DIRT);
        var env = new SimulationEnv();
        var p0 = env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2))
                .addPreciseInput(1, stone)
                .addPreciseInput(1, dirt)
                .build());
        var p1 = env.addPattern(new ProcessingPatternBuilder(sand).addPreciseInput(1, cobble).build());
        var p2 = env.addPattern(new ProcessingPatternBuilder(mult(stone, 3)).addPreciseInput(2, sand).build());
        env.addStoredItem(stone);
        env.addStoredItem(mult(dirt, 10));

        assertExecutesCleanly(
                env.runSpecialSimulation(mult(stone, 4), CalculationStrategy.REPORT_MISSING_ITEMS),
                stock(stone, mult(dirt, 10)), List.of(p0, p1, p2), 4,
                stock(stone, mult(dirt, 8))); // 种子返还,W 净消耗 2
    }

    /** X7:多产物自引用(1A→2A+1B)——副产物 B 全部返还网络. */
    @Test
    public void testMultiOutputSelfRefExecution() {
        var stone = item(Items.STONE);
        var stick = item(Items.STICK);
        var env = new SimulationEnv();
        var p = env.addPattern(new ProcessingPatternBuilder(mult(stone, 2), stick)
                .addPreciseInput(1, stone)
                .build());
        env.addStoredItem(stone);

        assertExecutesCleanly(
                env.runSpecialSimulation(mult(stone, 4), CalculationStrategy.REPORT_MISSING_ITEMS),
                stock(stone), List.of(p), 4, stock(stone, mult(stick, 4)));
    }

    /** X8:流体循环(1000mb 水→2000mb 岩浆→1000mb 水). */
    @Test
    public void testFluidCycleExecution() {
        var water = new GenericStack(AEFluidKey.of(Fluids.WATER), 1000);
        var lava = new GenericStack(AEFluidKey.of(Fluids.LAVA), 1000);
        var env = new SimulationEnv();
        var p0 = env.addPattern(new ProcessingPatternBuilder(mult(lava, 2)).addPreciseInput(1, water).build());
        var p1 = env.addPattern(new ProcessingPatternBuilder(water).addPreciseInput(1, lava).build());
        env.addStoredItem(water);

        assertExecutesCleanly(
                env.runSpecialSimulation(mult(water, 2), CalculationStrategy.REPORT_MISSING_ITEMS),
                stock(water), List.of(p0, p1), 2000, stock(water));
    }

    /** X9(反面):初始提取低于每轮种子要求 → 死锁(证明种子要求是真实下限). */
    @Test
    public void testInsufficientInitialExtractionDeadlocks() {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var env = new SimulationEnv();
        var crush = env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        var charge = env.addPattern(new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        var back = env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 2));
        env.addStoredItem(sand);

        var plan = env.runSpecialSimulation(mult(stone, 100), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThat(plan.simulation()).isFalse();
        // 人为少提取 1 个种子(计划要求 2,只给 1),充能优先的推送顺序必然死锁
        var result = ExecutionHarness.execute(plan, stock(mult(stone, 2), sand),
                stock(stone, sand), ExecutionHarness.Options.gameDefaults(),
                List.of(charge, crush, back));
        assertThat(result.deadlock()).isTrue();
        assertThat(result.completed()).isFalse();
    }

    /** X10(对照):无配额调度时充能优先批量推送必然死锁;有配额则完成. */
    @Test
    public void testQuotaSchedulerPreventsDeadlock() {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var env = new SimulationEnv();
        var crush = env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        var charge = env.addPattern(new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        var back = env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 2));
        env.addStoredItem(sand);

        var plan = env.runSpecialSimulation(mult(stone, 1000), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThat(plan.simulation()).isFalse();
        var network = stock(mult(stone, 2), sand);

        // 无配额:充能 pattern 一次性把 2 个种子全推掉,粉碎/回转双双断料
        var unscheduled = ExecutionHarness.execute(plan, network,
                ExecutionHarness.Options.gameDefaults().withoutQuota(),
                List.of(charge, crush, back));
        assertThat(unscheduled.deadlock()).isTrue();

        // 有配额:同样的网络与推送顺序,逐轮推进直至完成
        var scheduled = ExecutionHarness.execute(plan, network,
                ExecutionHarness.Options.gameDefaults(), List.of(charge, crush, back));
        assertThat(scheduled.completed())
                .as("有配额应完成 [ticks=%d, deadlock=%s, delivered=%d, cpu=%s, network=%s]",
                        scheduled.ticks(), scheduled.deadlock(), scheduled.delivered(),
                        scheduled.cpuInventory(), scheduled.network())
                .isTrue();
        assertThat(scheduled.delivered()).isEqualTo(1000);
        assertThat(scheduled.network().getOrDefault(stone.what(), 0L)).isEqualTo(2);
    }
}
