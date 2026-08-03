package com.github.aeddddd.ae2enhanced.test.craftingplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * DAG 引擎 parity 对齐测试:同一环境下,DAG 计划与原生计划的关键字段逐项相等.
 * <p>比对字段:patternTimes(按样板)、usedItems、missingItems、emittedItems;
 * bytes 为近似记账(CPU 选择用),不在比对范围(规划文档已声明).</p>
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class DagParityTest {

    private static final CalculationStrategy REPORT = CalculationStrategy.REPORT_MISSING_ITEMS;

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }

    /** P1:简单两步链 ×N(部分中间库存). */
    @Test
    public void testSimpleChain() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var stick = item(Items.STICK);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(mult(stick, 2)).addPreciseInput(1, cobble).build());
        env.addStoredItem(mult(stone, 3));

        assertParity(env, mult(stick, 8));
    }

    /** P2:共享中间物(D 需 B+C,B、C 各自需 A)——节点合并语义. */
    @Test
    public void testSharedIntermediate() {
        var env = new SimulationEnv();
        var a = item(Items.STONE);
        var b = item(Items.COBBLESTONE);
        var c = item(Items.SAND);
        var d = item(Items.DIRT);
        env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build());
        env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, a).build());
        env.addPattern(new ProcessingPatternBuilder(d).addPreciseInput(1, b).addPreciseInput(1, c).build());
        env.addStoredItem(mult(a, 10));

        assertParity(env, mult(d, 4));
    }

    /** P3:中间物库存全部/部分抵扣. */
    @Test
    public void testIntermediateStockCredit() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addStoredItem(mult(cobble, 3));
        env.addStoredItem(mult(stone, 64));

        assertParity(env, mult(cobble, 5));
    }

    /** P4:发射台提供终端输入. */
    @Test
    public void testEmitterInput() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addEmitable(stone.what());

        assertParity(env, mult(cobble, 7));
    }

    /** P5:缺料报告(REPORT_MISSING_ITEMS)——无样板终端. */
    @Test
    public void testMissingTerminal() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var diamond = item(Items.DIAMOND);
        env.addPattern(new ProcessingPatternBuilder(cobble)
                .addPreciseInput(1, stone)
                .addPreciseInput(1, diamond)
                .build());
        env.addStoredItem(mult(stone, 64));

        assertParity(env, mult(cobble, 4));
    }

    /** P6:余量回插(每份产 4,请求 5 → 2 次,余 3). */
    @Test
    public void testSurplusReinsert() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var stick = item(Items.STICK);
        env.addPattern(new ProcessingPatternBuilder(mult(stick, 4)).addPreciseInput(2, stone).build());
        env.addStoredItem(mult(stone, 64));

        assertParity(env, mult(stick, 5));
    }

    /** P7:请求物自身库存不参与扣除(镜像原生 ignore(output)). */
    @Test
    public void testRequestedStockIgnored() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addStoredItem(mult(cobble, 500)); // 请求物库存:不参与计划
        env.addStoredItem(mult(stone, 64));

        assertParity(env, mult(cobble, 10));
    }

    /** P8:深层长链大单(深度 40,无递归爆炸). */
    @Test
    public void testDeepChainLargeAmount() {
        var env = new SimulationEnv();
        Item[] chain = { Items.STONE, Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL,
                Items.CLAY_BALL, Items.BRICK, Items.FLINT, Items.COAL, Items.CHARCOAL };
        // 40 层:环状复用 10 个物品但按 (item, 层级) 区分——用倍率链代替:每层 1→1
        // 简化为 10 层链(测试环境物品有限),重点在大数量
        for (int i = 0; i < chain.length - 1; i++) {
            var out = item(chain[i + 1]);
            var in = item(chain[i]);
            env.addPattern(new ProcessingPatternBuilder(out).addPreciseInput(1, in).build());
        }
        env.addStoredItem(mult(item(chain[0]), 100_000));

        assertParity(env, mult(item(chain[chain.length - 1]), 100_000));
    }

    /** P9:自引用在根(循环)→ DAG 回落,结果与原生一致. */
    @Test
    public void testCycleFallsBackToNative() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        env.addStoredItem(mult(stone, 5));

        assertParity(env, mult(stone, 4));
    }

    // ===== 比对工具 =====

    private static void assertParity(SimulationEnv env, GenericStack request) {
        var nativePlan = env.runSimulation(request, REPORT);
        var dagPlan = env.runDagSimulation(request, REPORT);

        assertThat(dagPlan.simulation()).as("simulation").isEqualTo(nativePlan.simulation());
        assertThat(patternMap(dagPlan)).as("patternTimes").isEqualTo(patternMap(nativePlan));
        assertThat(counterMap(dagPlan.usedItems())).as("usedItems").isEqualTo(counterMap(nativePlan.usedItems()));
        assertThat(counterMap(dagPlan.missingItems())).as("missingItems")
                .isEqualTo(counterMap(nativePlan.missingItems()));
        assertThat(counterMap(dagPlan.emittedItems())).as("emittedItems")
                .isEqualTo(counterMap(nativePlan.emittedItems()));
    }

    private static Map<AEKey, Long> patternMap(ICraftingPlan plan) {
        Map<AEKey, Long> out = new HashMap<>();
        plan.patternTimes().forEach((pattern, times) -> {
            AEKey primary = pattern.getPrimaryOutput().what();
            out.merge(primary, times, Long::sum);
        });
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
