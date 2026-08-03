package com.github.aeddddd.ae2enhanced.test.craftingplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ContainerPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * {@code DagExecutor} / {@code DagCraftingCalculation} 的场景级测试.
 * <p>补充 parity 套件未覆盖的执行路径:策略门控、副产物回插与跨分支复用、
 * 单次容器不预贷、发射台与缺料混合、不干净样板整单回落.</p>
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class DagExecutorScenarioTest {

    private static final CalculationStrategy REPORT = CalculationStrategy.REPORT_MISSING_ITEMS;
    private static final CalculationStrategy CRAFT_LESS = CalculationStrategy.CRAFT_LESS;

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }

    /** S1:CRAFT_LESS 经尝试级 hook 接管(1.1.0 起)——二分搜索最大可产量,结果与原生一致. */
    @Test
    public void testCraftLessStrategyNotTakenOver() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addStoredItem(mult(stone, 3));

        var nativePlan = env.runSimulation(mult(cobble, 8), CRAFT_LESS);
        var dagPlan = env.runDagSimulation(mult(cobble, 8), CRAFT_LESS);

        assertThat(dagPlan.simulation()).isEqualTo(nativePlan.simulation());
        assertThat(primaryMap(dagPlan)).as("patternTimes").isEqualTo(primaryMap(nativePlan));
        assertThat(counterMap(dagPlan.usedItems())).as("usedItems")
                .isEqualTo(counterMap(nativePlan.usedItems()));
    }

    /** S2:不干净样板(多候选输入)→ 编译回落 unclean_inputs,整单由原生接管,结果一致. */
    @Test
    public void testUncleanPatternFallsBackToNative() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var dirt = item(Items.DIRT);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone, dirt).build());
        env.addStoredItem(mult(stone, 4));

        var nativePlan = env.runSimulation(mult(cobble, 3), REPORT);
        var dagPlan = env.runDagSimulation(mult(cobble, 3), REPORT);

        assertThat(dagPlan.simulation()).isEqualTo(nativePlan.simulation());
        assertThat(primaryMap(dagPlan)).as("patternTimes").isEqualTo(primaryMap(nativePlan));
        assertThat(counterMap(dagPlan.usedItems())).as("usedItems")
                .isEqualTo(counterMap(nativePlan.usedItems()));
    }

    /** S3:副产物全部回插库存(1 石头 → 1 圆石 + 2 沙子). */
    @Test
    public void testByproductReinserted() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        env.addPattern(new ProcessingPatternBuilder(cobble, mult(sand, 2))
                .addPreciseInput(1, stone)
                .build());
        env.addStoredItem(mult(stone, 8));

        var request = mult(cobble, 3);
        var nativePlan = env.runSimulation(request, REPORT);
        var dagPlan = env.runDagSimulation(request, REPORT);

        assertParity(nativePlan, dagPlan);
        assertThat(dagPlan.usedItems().get(stone.what())).isEqualTo(3);
    }

    /** S4:副产物供兄弟分支——修复兄弟逆序扫描后与原生一致.
     * R 需 B+D,B 的样板副产 D,D 无样板.原生按槽位顺序处理:B 先合成,
     * 副产物 D 入模拟库存,D 槽位随后提取成功 → 无缺料;
     * DAG 编译器按逆输入序 DFS,逆后序还原后兄弟分支顺序与槽位顺序一致,
     * 副产物同样可供兄弟分支提取,计划可提交. */
    @Test
    public void testByproductSuppliesSiblingBranch() {
        var env = new SimulationEnv();
        var a = item(Items.STONE);
        var b = item(Items.COBBLESTONE);
        var d = item(Items.SAND);
        var r = item(Items.DIRT);
        env.addPattern(new ProcessingPatternBuilder(r)
                .addPreciseInput(1, b)
                .addPreciseInput(1, d)
                .build());
        // 1 A → 1 B + 1 D(副产物)
        env.addPattern(new ProcessingPatternBuilder(b, d).addPreciseInput(1, a).build());
        env.addStoredItem(mult(a, 8));

        var request = mult(r, 2);
        var nativePlan = env.runSimulation(request, REPORT);
        var dagPlan = env.runDagSimulation(request, REPORT);

        // 与原生逐项比对:副产物满足 D 分支,双方均可提交且无缺料
        assertParity(nativePlan, dagPlan);
        assertThat(dagPlan.simulation()).as("DAG 可提交").isFalse();
        assertThat(primaryMap(dagPlan).get(b.what())).isEqualTo(2);
        assertThat(dagPlan.usedItems().get(a.what())).isEqualTo(2);
    }

    /** S5:单次执行(times=1)容器不预贷——首个循环必须先消耗才返还. */
    @Test
    public void testSingleRunContainerNotCredited() {
        var env = new SimulationEnv();
        var bottle = item(Items.HONEY_BOTTLE);
        var block = item(Items.HONEY_BLOCK);
        var glass = item(Items.GLASS_BOTTLE);
        env.addPattern(ContainerPatternBuilder.withContainer(
                new ProcessingPatternBuilder(block).addPreciseInput(4, bottle).build(),
                bottle.what(), glass.what()));
        env.addStoredItem(mult(bottle, 4));

        var request = mult(block, 1);
        var nativePlan = env.runSimulation(request, REPORT);
        var dagPlan = env.runDagSimulation(request, REPORT);

        assertParity(nativePlan, dagPlan);
        assertThat(dagPlan.usedItems().get(bottle.what())).isEqualTo(4);
    }

    /** S6:发射台与缺料混合——可发射输入记 emittedItems,无样板输入记 missingItems. */
    @Test
    public void testEmitterAndMissingMixed() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var diamond = item(Items.DIAMOND);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(cobble)
                .addPreciseInput(1, stone)
                .addPreciseInput(1, diamond)
                .build());
        env.addEmitable(stone.what());

        var request = mult(cobble, 3);
        var nativePlan = env.runSimulation(request, REPORT);
        var dagPlan = env.runDagSimulation(request, REPORT);

        assertParity(nativePlan, dagPlan);
        assertThat(dagPlan.emittedItems().get(stone.what())).isEqualTo(3);
        assertThat(dagPlan.missingItems().get(diamond.what())).isEqualTo(3);
        assertThat(dagPlan.simulation()).as("有缺料判为不可提交").isTrue();
    }

    /** S7:整除无剩余——次数 = 缺口/单次产出 恰好整除时不多做一轮. */
    @Test
    public void testExactDivisionNoSurplus() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var stick = item(Items.STICK);
        env.addPattern(new ProcessingPatternBuilder(mult(stick, 4)).addPreciseInput(2, stone).build());
        env.addStoredItem(mult(stone, 8));

        var request = mult(stick, 8);
        var nativePlan = env.runSimulation(request, REPORT);
        var dagPlan = env.runDagSimulation(request, REPORT);

        assertParity(nativePlan, dagPlan);
        assertThat(primaryMap(dagPlan).get(stick.what())).isEqualTo(2); // 恰好 2 次
        assertThat(dagPlan.usedItems().get(stone.what())).isEqualTo(4);
    }

    // ===== 比对工具 =====

    private static void assertParity(ICraftingPlan nativePlan, ICraftingPlan dagPlan) {
        assertThat(dagPlan.simulation()).as("simulation").isEqualTo(nativePlan.simulation());
        assertThat(primaryMap(dagPlan)).as("patternTimes").isEqualTo(primaryMap(nativePlan));
        assertThat(counterMap(dagPlan.usedItems())).as("usedItems")
                .isEqualTo(counterMap(nativePlan.usedItems()));
        assertThat(counterMap(dagPlan.missingItems())).as("missingItems")
                .isEqualTo(counterMap(nativePlan.missingItems()));
        assertThat(counterMap(dagPlan.emittedItems())).as("emittedItems")
                .isEqualTo(counterMap(nativePlan.emittedItems()));
    }

    private static Map<AEKey, Long> primaryMap(ICraftingPlan plan) {
        Map<AEKey, Long> out = new HashMap<>();
        plan.patternTimes().forEach((pattern, times) -> out.merge(pattern.getPrimaryOutput().what(),
                times, Long::sum));
        return out;
    }

    private static Map<AEKey, Long> counterMap(KeyCounter counter) {
        Map<AEKey, Long> out = new HashMap<>();
        for (var entry : counter) {
            out.put(entry.getKey(), entry.getLongValue());
        }
        return out;
    }
}
