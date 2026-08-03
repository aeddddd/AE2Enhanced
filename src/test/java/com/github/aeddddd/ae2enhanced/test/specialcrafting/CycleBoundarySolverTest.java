package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.inv.CraftingSimulationState;

import com.github.aeddddd.ae2enhanced.specialcrafting.CycleBoundarySolver;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraft;

/**
 * {@link CycleBoundarySolver} 测试.
 * <p>solveInto 的类型断言为直接单元测试;求解语义经 DAG 引擎整链路验证
 * （DagCycleBoundaryTest 已覆盖深层自增殖/θ 环/无种子回落/共享边界,
 * 此处补充深层两节点环、深层催化环与耗散催化环回落）.</p>
 */
@BootstrapMinecraft
class CycleBoundarySolverTest {

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

    /** solveInto 只接受 Child 模拟库存,其他实现一律返回 false(调用方回落). */
    @Test
    void testSolveIntoRejectsNonChildState() throws InterruptedException {
        var craftingService = mock(ICraftingService.class);
        var calc = mock(CraftingCalculation.class);
        var inv = mock(CraftingSimulationState.class); // 非 ChildCraftingSimulationState

        boolean result = CycleBoundarySolver.solveInto(craftingService, calc,
                AEItemKey.of(Items.STONE), 10, inv);

        assertThat(result).isFalse();
    }

    /** 深层两节点增殖环:请求 D,D←C,C 在环 C→2B、B→C 上,种子 1C. */
    @Test
    void testDeepTwoNodeCycleBoundary() {
        var env = new SimulationEnv();
        var b = item(Items.COBBLESTONE);
        var c = item(Items.STONE);
        var d = item(Items.DIRT);
        env.addPattern(new ProcessingPatternBuilder(d).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(mult(b, 2)).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, b).build());
        env.addStoredItem(c); // 种子 1

        var plan = env.runDagSimulation(mult(d, 10), REPORT);

        assertThat(plan.simulation()).as("计划成功").isFalse();
        var times = primaryMap(plan);
        assertThat(times.get(d.what())).isEqualTo(10);
        // 环:净产 1C/轮 ×10 轮,p0(C→2B)10 次、p1(B→C)20 次
        assertThat(times.get(b.what())).isEqualTo(10);
        assertThat(times.get(c.what())).isEqualTo(20);
        assertThat(plan.usedItems().get(c.what())).isEqualTo(1); // 仅种子
    }

    /** 深层催化环:请求 D,D←X,X 是中性环 1A→1X+1B、1B→1A 发射的环外副产物,种子 1A. */
    @Test
    void testDeepCatalyticCycleBoundary() {
        var env = new SimulationEnv();
        var a = item(Items.STONE);
        var b = item(Items.COBBLESTONE);
        var x = item(Items.SAND);
        var d = item(Items.DIRT);
        env.addPattern(new ProcessingPatternBuilder(d).addPreciseInput(1, x).build());
        env.addPattern(new ProcessingPatternBuilder(x, b).addPreciseInput(1, a).build());
        env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, b).build());
        env.addStoredItem(a); // 环键种子 1A

        var plan = env.runDagSimulation(mult(d, 5), REPORT);

        assertThat(plan.simulation()).as("计划成功").isFalse();
        var times = primaryMap(plan);
        assertThat(times.get(d.what())).isEqualTo(5);
        // 催化环每轮发射 1X,5 轮:pX 5 次、pB 5 次
        assertThat(times.get(x.what())).isEqualTo(5);
        assertThat(times.get(a.what())).isEqualTo(5);
        assertThat(plan.usedItems().get(a.what())).isEqualTo(1); // 仅种子
    }

    /** 深层耗散催化环(2B→1A 净率 < 1)→ 边界不可解,整单回落原生,结果与原生一致. */
    @Test
    void testDeepDissipativeCatalyticCycleFallsBack() {
        var env = new SimulationEnv();
        var a = item(Items.STONE);
        var b = item(Items.COBBLESTONE);
        var x = item(Items.SAND);
        var d = item(Items.DIRT);
        env.addPattern(new ProcessingPatternBuilder(d).addPreciseInput(1, x).build());
        env.addPattern(new ProcessingPatternBuilder(x, b).addPreciseInput(1, a).build());
        env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(2, b).build()); // 耗散
        env.addStoredItem(a);

        var dagPlan = env.runDagSimulation(mult(d, 4), REPORT);
        var nativePlan = env.runSimulation(mult(d, 4), REPORT);

        // 耗散环不可解 → 回落原生,两者一致(环内 B 无独立来源,缺料失败)
        assertThat(dagPlan.simulation()).isEqualTo(nativePlan.simulation());
        assertThat(dagPlan.simulation()).as("缺料失败").isTrue();
        assertThat(primaryMap(dagPlan)).isEqualTo(primaryMap(nativePlan));
    }
}
