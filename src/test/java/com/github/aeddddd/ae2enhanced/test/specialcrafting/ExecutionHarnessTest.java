package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static com.github.aeddddd.ae2enhanced.test.specialcrafting.SimulationEnv.block;
import static com.github.aeddddd.ae2enhanced.test.specialcrafting.SimulationEnv.item;
import static com.github.aeddddd.ae2enhanced.test.specialcrafting.SimulationEnv.mult;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;

/**
 * X 组:执行层语义自动化测试(ExecutionHarness),1.12.2 移植版.
 * <p>核心断言:① 任务完成;② CPU 无残留(收官后清空);③ 网络守恒
 * (期末 = 期初 + 净产出 - 交付);④ 对全部推送排列成立.</p>
 * <p>1.20.1 的流体用例(X8)不单独移植:1.12.2 的 ae2fc 流体样板以 FluidDrop
 * 假物品形式存在,与物品 key 走同一代码路径.</p>
 */
public class ExecutionHarnessTest {

    private static Map<IAEItemStack, Long> stock(IAEItemStack... stacks) {
        Map<IAEItemStack, Long> map = new LinkedHashMap<>();
        for (IAEItemStack stack : stacks) {
            map.merge(RecursiveCraftingHelper.canon(stack), stack.getStackSize(), Long::sum);
        }
        return map;
    }

    /**
     * 对全部推送排列执行并断言:完成、无死锁、CPU 无残留、交付足额、网络期末精确匹配.
     */
    private static void assertExecutesCleanly(PlanView plan, Map<IAEItemStack, Long> network,
            List<ICraftingPatternDetails> patterns, long expectedDelivered,
            Map<IAEItemStack, Long> expectedNetworkEnd) {
        assertThat(plan.simulation()).as("计划应成功").isFalse();
        for (List<ICraftingPatternDetails> order : ExecutionHarness.pushOrders(patterns)) {
            ExecutionHarness.Result result = ExecutionHarness.execute(plan, network,
                    ExecutionHarness.Options.gameDefaults(), order);
            assertThat(result.completed)
                    .as("订单应完成 [ticks=%d, deadlock=%s, cpu=%s, network=%s]",
                            result.ticks, result.deadlock, result.cpuInventory, result.network)
                    .isTrue();
            assertThat(result.deadlock).isFalse();
            assertThat(result.cpuInventory).as("CPU 收官后必须无残留")
                    .allSatisfy((k, v) -> assertThat(v).as("CPU 残留 %s", k).isZero());
            assertThat(result.delivered).isEqualTo(expectedDelivered);
            for (Map.Entry<IAEItemStack, Long> entry : expectedNetworkEnd.entrySet()) {
                assertThat(result.network.getOrDefault(entry.getKey(), 0L))
                        .as("网络期末 %s", entry.getKey())
                        .isEqualTo(entry.getValue());
            }
        }
    }

    /** X1:自引用复制(1A→2A)——种子 1,请求 10,全额生产 10 次,
     * 执行后种子随 CPU 剩余返还网络(种子保留). */
    @Test
    public void testSelfRefDuplicationExecution() {
        IAEItemStack stone = block(Blocks.STONE);
        SimulationEnv env = new SimulationEnv();
        ICraftingPatternDetails dup = env.addPattern(
                new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        env.addStoredItem(stone);

        assertExecutesCleanly(PlanView.of(env.runDag(mult(stone, 10))),
                stock(stone), java.util.Collections.singletonList(dup), 10, stock(stone));
    }

    /** X2:θ 循环 ×100——每轮种子(2A+1C)即可,全排列完成且守恒.
     * 全额生产语义:净需 100 → 50 crush/49 charge/50 back(LP 最小执行数解),
     * 期末石 2 - 99 + 200 - 100 = 3,砂 1 + 49 - 50 = 0. */
    @Test
    public void testThetaX100AllOrders() {
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        SimulationEnv env = new SimulationEnv();
        ICraftingPatternDetails crush = env.addPattern(
                new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        ICraftingPatternDetails charge = env.addPattern(
                new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        ICraftingPatternDetails back = env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 2)); // 每轮种子
        env.addStoredItem(sand);

        assertExecutesCleanly(PlanView.of(env.runDag(mult(stone, 100))),
                stock(mult(stone, 2), sand), java.util.Arrays.asList(crush, charge, back), 100,
                stock(mult(stone, 3), mult(sand, 0))); // 期末:2 - 99 + 200 - 100 = 3,砂 1 + 49 - 50 = 0
    }

    /** X3:θ 循环 ×1000(499 轮)——配额调度下串行预算也完成. */
    @Test
    public void testThetaX1000SerialBudget() {
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        SimulationEnv env = new SimulationEnv();
        ICraftingPatternDetails crush = env.addPattern(
                new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        ICraftingPatternDetails charge = env.addPattern(
                new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        ICraftingPatternDetails back = env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 2));
        env.addStoredItem(sand);

        PlanView plan = PlanView.of(env.runDag(mult(stone, 1000)));
        assertThat(plan.simulation()).isFalse();
        ExecutionHarness.Result result = ExecutionHarness.execute(plan, stock(mult(stone, 2), sand),
                ExecutionHarness.Options.gameDefaults().serial(),
                java.util.Arrays.asList(crush, charge, back));
        assertThat(result.completed).isTrue();
        assertThat(result.delivered).isEqualTo(1000);
        // 期末:2 - 999 + 2000 - 1000 = 3(全额生产 [500,499,500])
        assertThat(result.network.getOrDefault(RecursiveCraftingHelper.canon(stone), 0L)).isEqualTo(3);
    }

    /** X4:用户 ABC 案例(16A+16B+W→64C,64C+W→64A)——种子 32A,全额生产 64 → 2 轮,
     * 种子期末随 CPU 剩余返还. */
    @Test
    public void testUserAbcCaseExecution() {
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        IAEItemStack dirt = block(Blocks.DIRT);
        SimulationEnv env = new SimulationEnv();
        ICraftingPatternDetails p1 = env.addPattern(
                new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(mult(sand, 64))
                .addPreciseInput(16, stone)
                .addPreciseInput(16, cobble)
                .addPreciseInput(1, dirt)
                .build());
        ICraftingPatternDetails p3 = env.addPattern(new ProcessingPatternBuilder(mult(stone, 64))
                .addPreciseInput(64, sand)
                .addPreciseInput(1, dirt)
                .build());
        env.addStoredItem(mult(stone, 32));
        env.addStoredItem(mult(dirt, 100));

        assertExecutesCleanly(PlanView.of(env.runDag(mult(stone, 64))),
                stock(mult(stone, 32), mult(dirt, 100)), java.util.Arrays.asList(p1, p2, p3), 64,
                stock(mult(stone, 32), mult(dirt, 96))); // 种子期末返还,W 净消耗 4(2 轮 × 2)
    }

    /** X5:催化剂 X≠Y(B+A←A)——催化剂完整返还,目标全额交付. */
    @Test
    public void testCatalystExecution() {
        IAEItemStack dirt = block(Blocks.DIRT);
        IAEItemStack stick = item(Items.STICK);
        SimulationEnv env = new SimulationEnv();
        ICraftingPatternDetails p = env.addPattern(
                new ProcessingPatternBuilder(stick, dirt).addPreciseInput(1, dirt).build());
        env.addStoredItem(dirt);

        assertExecutesCleanly(PlanView.of(env.runDag(mult(stick, 10))),
                stock(dirt), java.util.Collections.singletonList(p), 10, stock(dirt));
    }

    /** X6:三键分数速率环 + 每轮辅材(A+W→2B,B→C,2C→3A). */
    @Test
    public void testRationalCycleWithAuxExecution() {
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        IAEItemStack dirt = block(Blocks.DIRT);
        SimulationEnv env = new SimulationEnv();
        ICraftingPatternDetails p0 = env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2))
                .addPreciseInput(1, stone)
                .addPreciseInput(1, dirt)
                .build());
        ICraftingPatternDetails p1 = env.addPattern(
                new ProcessingPatternBuilder(sand).addPreciseInput(1, cobble).build());
        ICraftingPatternDetails p2 = env.addPattern(
                new ProcessingPatternBuilder(mult(stone, 3)).addPreciseInput(2, sand).build());
        env.addStoredItem(stone);
        env.addStoredItem(mult(dirt, 10));

        assertExecutesCleanly(PlanView.of(env.runDag(mult(stone, 4))),
                stock(stone, mult(dirt, 10)), java.util.Arrays.asList(p0, p1, p2), 4,
                stock(stone, mult(dirt, 8))); // 种子返还,W 净消耗 2
    }

    /** X7:多产物自引用(1A→2A+1B)——全额生产 4 次,种子期末返还,副产物 B 全部返还网络. */
    @Test
    public void testMultiOutputSelfRefExecution() {
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack stick = item(Items.STICK);
        SimulationEnv env = new SimulationEnv();
        ICraftingPatternDetails p = env.addPattern(new ProcessingPatternBuilder(mult(stone, 2), stick)
                .addPreciseInput(1, stone)
                .build());
        env.addStoredItem(stone);

        assertExecutesCleanly(PlanView.of(env.runDag(mult(stone, 4))),
                stock(stone), java.util.Collections.singletonList(p), 4, stock(stone, mult(stick, 4)));
    }

    /** X9(反面):初始提取缺失种子(0 石) → 死锁(证明种子要求是真实下限). */
    @Test
    public void testInsufficientInitialExtractionDeadlocks() {
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        SimulationEnv env = new SimulationEnv();
        ICraftingPatternDetails crush = env.addPattern(
                new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        ICraftingPatternDetails charge = env.addPattern(
                new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        ICraftingPatternDetails back = env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 2));
        env.addStoredItem(sand);

        PlanView plan = PlanView.of(env.runDag(mult(stone, 100)));
        assertThat(plan.simulation()).isFalse();
        // 人为不提取石种子(计划要求 2,只给砂),任何推送顺序都无法启动
        ExecutionHarness.Result result = ExecutionHarness.execute(plan, stock(mult(stone, 2), sand),
                stock(sand), ExecutionHarness.Options.gameDefaults(),
                java.util.Arrays.asList(charge, crush, back));
        assertThat(result.deadlock).isTrue();
        assertThat(result.completed).isFalse();
    }

    /** X10(对照):无配额调度时充能优先批量推送必然死锁;有配额则完成. */
    @Test
    public void testQuotaSchedulerPreventsDeadlock() {
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        SimulationEnv env = new SimulationEnv();
        ICraftingPatternDetails crush = env.addPattern(
                new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        ICraftingPatternDetails charge = env.addPattern(
                new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        ICraftingPatternDetails back = env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 2));
        env.addStoredItem(sand);

        PlanView plan = PlanView.of(env.runDag(mult(stone, 1000)));
        assertThat(plan.simulation()).isFalse();
        Map<IAEItemStack, Long> network = stock(mult(stone, 2), sand);

        // 无配额:充能 pattern 一次性把 2 个种子全推掉,粉碎/回转双双断料
        ExecutionHarness.Result unscheduled = ExecutionHarness.execute(plan, network,
                ExecutionHarness.Options.gameDefaults().withoutQuota(),
                java.util.Arrays.asList(charge, crush, back));
        assertThat(unscheduled.deadlock).isTrue();

        // 有配额:同样的网络与推送顺序,逐轮推进直至完成
        ExecutionHarness.Result scheduled = ExecutionHarness.execute(plan, network,
                ExecutionHarness.Options.gameDefaults(), java.util.Arrays.asList(charge, crush, back));
        assertThat(scheduled.completed)
                .as("有配额应完成 [ticks=%d, deadlock=%s, delivered=%d, cpu=%s, network=%s]",
                        scheduled.ticks, scheduled.deadlock, scheduled.delivered,
                        scheduled.cpuInventory, scheduled.network)
                .isTrue();
        assertThat(scheduled.delivered).isEqualTo(1000);
        // 期末:2 - 999 + 2000 - 1000 = 3(全额生产 [500,499,500])
        assertThat(scheduled.network.getOrDefault(RecursiveCraftingHelper.canon(stone), 0L)).isEqualTo(3);
    }
}
