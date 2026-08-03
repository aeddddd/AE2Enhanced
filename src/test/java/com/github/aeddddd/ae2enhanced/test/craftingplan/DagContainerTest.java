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
 * 容器物合成的 DAG 批量处理(parity 对齐).
 * <p>原生对容器样板逐次(times=1)循环;DAG 批量记账(消耗 N、回记 N 容器),
 * 数学等价——以蜂蜜瓶式(4 瓶 → 1 块 + 4 玻璃瓶)与桶式(容器被下游复用)
 * 两个场景与原生逐字段比对.</p>
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class DagContainerTest {

    private static final CalculationStrategy REPORT = CalculationStrategy.REPORT_MISSING_ITEMS;

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
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

    private static void assertParity(SimulationEnv env, GenericStack request) {
        var nativePlan = env.runSimulation(request, REPORT);
        var dagPlan = env.runDagSimulation(request, REPORT);
        assertThat(primaryMap(dagPlan)).as("patternTimes").isEqualTo(primaryMap(nativePlan));
        assertThat(counterMap(dagPlan.usedItems())).as("usedItems")
                .isEqualTo(counterMap(nativePlan.usedItems()));
        assertThat(counterMap(dagPlan.missingItems())).as("missingItems")
                .isEqualTo(counterMap(nativePlan.missingItems()));
        assertThat(counterMap(dagPlan.emittedItems())).as("emittedItems")
                .isEqualTo(counterMap(nativePlan.emittedItems()));
        assertThat(dagPlan.simulation()).as("simulation").isEqualTo(nativePlan.simulation());
    }

    /** C1:蜂蜜瓶式(4 蜂蜜瓶 → 1 蜂蜜块,返还 4 玻璃瓶)大单——批量与原生逐次循环全等. */
    @Test
    public void testHoneyBottleBulkParity() {
        var env = new SimulationEnv();
        var bottle = item(Items.HONEY_BOTTLE);
        var block = item(Items.HONEY_BLOCK);
        var glass = item(Items.GLASS_BOTTLE);
        env.addPattern(ContainerPatternBuilder.withContainer(
                new ProcessingPatternBuilder(block).addPreciseInput(4, bottle).build(),
                bottle.what(), glass.what()));
        env.addStoredItem(mult(bottle, 64));

        assertParity(env, mult(block, 16));
    }

    /** C2:桶式复用——容器(空桶)同时是下游输入,拓扑序先回记后提取,批量仍等价. */
    @Test
    public void testContainerReusedDownstreamParity() {
        var env = new SimulationEnv();
        var milkBucket = item(Items.MILK_BUCKET);
        var bucket = item(Items.BUCKET);
        var milk = item(Items.DIRT); // 以 dirt 代牛奶(测试物品)
        var cake = item(Items.CAKE);
        // 1 奶桶 → 1 蛋糕,返还 1 空桶;1 空桶 + 1 奶 → 1 奶桶
        env.addPattern(ContainerPatternBuilder.withContainer(
                new ProcessingPatternBuilder(cake).addPreciseInput(1, milkBucket).build(),
                milkBucket.what(), bucket.what()));
        env.addPattern(new ProcessingPatternBuilder(milkBucket)
                .addPreciseInput(1, bucket)
                .addPreciseInput(1, milk)
                .build());
        env.addStoredItem(mult(milkBucket, 8));
        env.addStoredItem(mult(milk, 64));

        assertParity(env, mult(cake, 10));
    }

    /** C3:深层容器——容器样板嵌在更大订单中间层,不回落、与原生一致. */
    @Test
    public void testDeepContainerInLargerOrderParity() {
        var env = new SimulationEnv();
        var bottle = item(Items.HONEY_BOTTLE);
        var block = item(Items.HONEY_BLOCK);
        var glass = item(Items.GLASS_BOTTLE);
        var target = item(Items.CHEST);
        env.addPattern(new ProcessingPatternBuilder(target).addPreciseInput(2, block).build());
        env.addPattern(ContainerPatternBuilder.withContainer(
                new ProcessingPatternBuilder(block).addPreciseInput(4, bottle).build(),
                bottle.what(), glass.what()));
        env.addStoredItem(mult(bottle, 128));

        assertParity(env, mult(target, 8));
    }

    /** C5:催化剂链(4 低 + 催化 → 1 高,催化容器返还)——种子必须计入初始提取.
     * 回归:全额预贷会把 usedItems(催化)抹成 0,CPU 不提取种子,执行卡死. */
    @Test
    public void testCatalystChainSeedExtraction() {
        var env = new SimulationEnv();
        var low = item(Items.STONE);
        var high = item(Items.COBBLESTONE);
        var catalyst = item(Items.NETHER_STAR);
        // 4 低 + 1 催化 → 1 高,催化剂以容器物返还
        env.addPattern(ContainerPatternBuilder.withContainer(
                new ProcessingPatternBuilder(high)
                        .addPreciseInput(4, low)
                        .addPreciseInput(1, catalyst)
                        .build(),
                catalyst.what(), catalyst.what()));
        env.addStoredItem(mult(low, 64));
        env.addStoredItem(catalyst); // 种子 1

        var plan = env.runDagSimulation(mult(high, 8), REPORT);

        assertThat(plan.simulation()).as("计划成功").isFalse();
        // 种子必须出现在初始提取中(原生逐次循环高水位同为 1)
        assertThat(plan.usedItems().get(catalyst.what())).isEqualTo(1);
        // 与原生逐项比对
        var nativePlan = env.runSimulation(mult(high, 8), REPORT);
        assertThat(primaryMap(plan)).as("patternTimes").isEqualTo(primaryMap(nativePlan));
        assertThat(counterMap(plan.usedItems())).as("usedItems")
                .isEqualTo(counterMap(nativePlan.usedItems()));
    }

    /** C4:库存不足时判为不可提交且有缺料记录.
     * 供给感知钳制(1.1.0)后:DAG 与原生 limitQty 首败迭代截断语义对齐,
     * 调用次数截断({block:1}),截断部分以缺料形式上报. */
    @Test
    public void testContainerMissingParity() {
        var env = new SimulationEnv();
        var bottle = item(Items.HONEY_BOTTLE);
        var block = item(Items.HONEY_BLOCK);
        var glass = item(Items.GLASS_BOTTLE);
        env.addPattern(ContainerPatternBuilder.withContainer(
                new ProcessingPatternBuilder(block).addPreciseInput(4, bottle).build(),
                bottle.what(), glass.what()));
        env.addStoredItem(mult(bottle, 4));

        var nativePlan = env.runSimulation(mult(block, 8), REPORT);
        var dagPlan = env.runDagSimulation(mult(block, 8), REPORT);

        assertThat(dagPlan.simulation()).as("DAG 判为不可提交").isTrue();
        assertThat(nativePlan.simulation()).as("原生判为不可提交").isTrue();
        assertThat(dagPlan.missingItems().isEmpty()).as("DAG 有缺料记录").isFalse();
    }
}
