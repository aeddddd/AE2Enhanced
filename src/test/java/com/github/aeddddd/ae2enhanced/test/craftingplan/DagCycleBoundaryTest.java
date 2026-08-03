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

import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * Z 组:DAG 循环边界(深层自引用/循环链识别与委托求解).
 * <p>根请求本身无环(原生可解),但中间节点落在自引用/环上样板上——
 * 编译期 SCC 收缩为 CYCLE 叶子,执行期委托 CycleBoundarySolver.</p>
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class DagCycleBoundaryTest {

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

    /** Z1:深层自增殖——请求 D,D 需要 C,C 是自增殖样板(1C→2C). */
    @Test
    public void testDeepSelfDupBoundary() {
        var env = new SimulationEnv();
        var c = item(Items.COBBLESTONE);
        var d = item(Items.DIRT);
        env.addPattern(new ProcessingPatternBuilder(d).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(mult(c, 2)).addPreciseInput(1, c).build());
        env.addStoredItem(c); // 种子 1

        var plan = env.runDagSimulation(mult(d, 10), REPORT);

        assertThat(plan.simulation()).as("计划成功").isFalse();
        var times = primaryMap(plan);
        assertThat(times.get(d.what())).isEqualTo(10);
        assertThat(times.get(c.what())).isEqualTo(10); // dup 净产 1/次 ×10
        assertThat(plan.usedItems().get(c.what())).isEqualTo(1); // 种子
    }

    /** Z2:深层 θ 循环——请求 E,E←D←C,C 在 θ 环上(C→X,C→Y,X+Y→4C). */
    @Test
    public void testDeepThetaCycleBoundary() {
        var env = new SimulationEnv();
        var c = item(Items.STONE);
        var x = item(Items.COBBLESTONE);
        var y = item(Items.SAND);
        var d = item(Items.DIRT);
        var e = item(Items.GRAVEL);
        env.addPattern(new ProcessingPatternBuilder(e).addPreciseInput(1, d).build());
        env.addPattern(new ProcessingPatternBuilder(d).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(x).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(y).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(mult(c, 4))
                .addPreciseInput(1, x)
                .addPreciseInput(1, y)
                .build());
        env.addStoredItem(mult(c, 8));
        env.addStoredItem(y);

        var plan = env.runDagSimulation(mult(e, 8), REPORT);

        assertThat(plan.simulation()).as("计划成功").isFalse();
        var times = primaryMap(plan);
        assertThat(times.get(e.what())).isEqualTo(8);
        assertThat(times.get(d.what())).isEqualTo(8);
        // θ 环:净产 2/轮 ×4 轮,三样板各 4 次
        assertThat(times.get(c.what())).isEqualTo(4);
        assertThat(times.get(x.what())).isEqualTo(4);
        assertThat(times.get(y.what())).isEqualTo(4);
    }

    /** Z3:边界不可解(无种子)→ 整单回落原生,结果与原生一致. */
    @Test
    public void testBoundaryUnsolvableFallsBack() {
        var env = new SimulationEnv();
        var c = item(Items.COBBLESTONE);
        var d = item(Items.DIRT);
        env.addPattern(new ProcessingPatternBuilder(d).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(mult(c, 2)).addPreciseInput(1, c).build());
        // 无 C 库存(无种子)

        var dagPlan = env.runDagSimulation(mult(d, 4), REPORT);
        var nativePlan = env.runSimulation(mult(d, 4), REPORT);

        assertThat(dagPlan.simulation()).isEqualTo(nativePlan.simulation());
        assertThat(primaryMap(dagPlan)).isEqualTo(primaryMap(nativePlan));
    }

    /** Z4:共享边界——E 直接需要环上 key,同时经 D 间接需要(需求沿边累加进同一边界). */
    @Test
    public void testSharedBoundaryDemandAccumulates() {
        var env = new SimulationEnv();
        var c = item(Items.COBBLESTONE);
        var d = item(Items.DIRT);
        var e = item(Items.GRAVEL);
        env.addPattern(new ProcessingPatternBuilder(e)
                .addPreciseInput(1, d)
                .addPreciseInput(1, c)
                .build());
        env.addPattern(new ProcessingPatternBuilder(d).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(mult(c, 2)).addPreciseInput(1, c).build());
        env.addStoredItem(c); // 种子 1

        // E×4:D 需 4 个 C,E 直接需 4 个 C → 边界总需求 8
        var plan = env.runDagSimulation(mult(e, 4), REPORT);

        assertThat(plan.simulation()).as("计划成功").isFalse();
        var times = primaryMap(plan);
        assertThat(times.get(e.what())).isEqualTo(4);
        assertThat(times.get(d.what())).isEqualTo(4);
        assertThat(times.get(c.what())).isEqualTo(8); // dup ×8 满足 4+4
        assertThat(plan.usedItems().get(c.what())).isEqualTo(1);
    }

    /**
     * Z5:切边重试(④)——中性转换对(1 块 ⇄ 9 锭)请求锭:边界不可解(θ=1 且锭非副产物),
     * 切边重编译后产出诚实缺料计划(锭的循环投入记缺料),而非整单回落.
     */
    @Test
    public void testConversionPairCutEdgeHonestMissing() {
        var env = new SimulationEnv();
        var block = item(Items.IRON_BLOCK);
        var ingot = item(Items.IRON_INGOT);
        env.addPattern(new ProcessingPatternBuilder(mult(ingot, 9)).addPreciseInput(1, block).build());
        env.addPattern(new ProcessingPatternBuilder(block).addPreciseInput(9, ingot).build());

        var plan = env.runDagSimulation(mult(ingot, 9), REPORT);

        assertThat(plan.simulation()).as("无锭库存,缺料计划").isTrue();
        var times = primaryMap(plan);
        assertThat(times).as("patternTimes 全量").containsKey(ingot.what());
        assertThat(times.get(ingot.what())).as("锭样板次数,times=%s missing=%s", times, plan.missingItems())
                .isEqualTo(1); // 1 块 → 9 锭
        assertThat(times.get(block.what())).as("块样板次数,times=%s missing=%s", times, plan.missingItems())
                .isEqualTo(1); // 块的循环合成被计入
        assertThat(plan.missingItems().get(ingot.what())).isEqualTo(9); // 切边终端记缺料
    }

    /** Z6:切边后的可行侧——请求块且有 9 锭库存:切边终端从库存满足,计划可行. */
    @Test
    public void testConversionPairCutEdgeFeasibleWithStock() {
        var env = new SimulationEnv();
        var block = item(Items.IRON_BLOCK);
        var ingot = item(Items.IRON_INGOT);
        env.addPattern(new ProcessingPatternBuilder(mult(ingot, 9)).addPreciseInput(1, block).build());
        env.addPattern(new ProcessingPatternBuilder(block).addPreciseInput(9, ingot).build());
        env.addStoredItem(mult(ingot, 9));

        var plan = env.runDagSimulation(block, REPORT);

        assertThat(plan.simulation()).as("9 锭足够合成 1 块").isFalse();
        var times = primaryMap(plan);
        assertThat(times.get(block.what())).isEqualTo(1);
        assertThat(plan.usedItems().get(ingot.what())).isEqualTo(9);
    }
}
