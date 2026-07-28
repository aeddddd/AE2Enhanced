package com.github.aeddddd.ae2enhanced.test.specialcrafting;

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

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialRecipeDetector;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * 催化环(中性/增殖环发射环外副产物)测试组.
 * <p>场景:1A → 1X + 1B(X 主产出,B 副产物)、1B → 1A,请求 X——
 * 循环经副产物闭合,X 不在环键上;detector/求解/边界/深层 DAG 全链路.</p>
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class CatalyticCycleTest {

    private static final CalculationStrategy REPORT = CalculationStrategy.REPORT_MISSING_ITEMS;

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static Map<AEKey, Long> primaryMap(ICraftingPlan plan) {
        Map<AEKey, Long> out = new HashMap<>();
        plan.patternTimes().forEach((pattern, times) -> out.merge(pattern.getPrimaryOutput().what(),
                times, Long::sum));
        return out;
    }

    /** 催化环环境:1A → 1X + 1B、1B → nA. */
    private SimulationEnv catalyticEnv(int aPerB) {
        var env = new SimulationEnv();
        var a = item(Items.STONE);
        var b = item(Items.COBBLESTONE);
        var x = item(Items.SAND);
        env.addPattern(new ProcessingPatternBuilder(x, b).addPreciseInput(1, a).build());
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(a.what(), aPerB))
                .addPreciseInput(1, b).build());
        return env;
    }

    /** T1:中性催化环(1B→1A),种子 1A,请求 X×5 → 两样板各 5 次,种子 1. */
    @Test
    public void testNeutralCatalyticCycle() {
        var env = catalyticEnv(1);
        var a = item(Items.STONE);
        var x = item(Items.SAND);
        env.addStoredItem(a);

        var plan = env.runSpecialSimulation(new GenericStack(x.what(), 5), REPORT);

        assertThat(plan.simulation()).as("计划成功").isFalse();
        var times = primaryMap(plan);
        assertThat(times.get(x.what())).isEqualTo(5);
        assertThat(times.get(a.what())).isEqualTo(5);
        assertThat(plan.usedItems().get(a.what())).isEqualTo(1); // 种子
    }

    /** T2:增殖催化环(1B→2A,环键还有净产),同样可发射副产物求解. */
    @Test
    public void testProductiveCatalyticCycle() {
        var env = catalyticEnv(2);
        var a = item(Items.STONE);
        var x = item(Items.SAND);
        env.addStoredItem(a);

        var plan = env.runSpecialSimulation(new GenericStack(x.what(), 5), REPORT);

        assertThat(plan.simulation()).as("计划成功").isFalse();
        var times = primaryMap(plan);
        assertThat(times.get(x.what())).isEqualTo(5);
        assertThat(times.get(a.what())).isEqualTo(5);
    }

    /** T3:无种子 → 回落原生,与原生结果一致(缺料). */
    @Test
    public void testNoSeedFallsBackToNative() {
        var env = catalyticEnv(1);
        var x = item(Items.SAND);

        var specialPlan = env.runSpecialSimulation(new GenericStack(x.what(), 5), REPORT);
        var nativePlan = env.runSimulation(new GenericStack(x.what(), 5), REPORT);

        assertThat(specialPlan.simulation()).isEqualTo(nativePlan.simulation());
        assertThat(primaryMap(specialPlan)).isEqualTo(primaryMap(nativePlan));
    }

    /** T4:detector 命中催化环请求(根路由前提). */
    @Test
    public void testDetectorHitsCatalyticRequest() {
        var env = catalyticEnv(1);
        var x = item(Items.SAND);
        assertThat(SpecialRecipeDetector.mayInvolveSpecialRecipes(env.craftingService(), x.what()))
                .isTrue();
    }

    /** T5:深层 DAG——请求 E,E←X,X 由催化环供应;边界应落在 X(而非环键). */
    @Test
    public void testDeepDagCatalyticBoundary() {
        var env = catalyticEnv(1);
        var a = item(Items.STONE);
        var x = item(Items.SAND);
        var e = item(Items.GRAVEL);
        env.addPattern(new ProcessingPatternBuilder(e).addPreciseInput(1, x).build());
        env.addStoredItem(a);

        var plan = env.runDagSimulation(new GenericStack(e.what(), 5), REPORT);

        assertThat(plan.simulation()).as("计划成功").isFalse();
        var times = primaryMap(plan);
        assertThat(times.get(e.what())).isEqualTo(5);
        assertThat(times.get(x.what())).isEqualTo(5);
        assertThat(times.get(a.what())).isEqualTo(5);
        assertThat(plan.usedItems().get(a.what())).isEqualTo(1);
    }
}
