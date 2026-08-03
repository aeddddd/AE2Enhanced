package com.github.aeddddd.ae2enhanced.test.craftingplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;

import com.github.aeddddd.ae2enhanced.craftingplan.dag.DagPlanAttempt;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * 按量尝试契约测试(阶段 4.5):DAG 尝试级 hook 与原生 runCraftAttempt 契约对齐,
 * CRAFT_LESS 二分搜索可直接驱动——超量 INFEASIBLE、足量 SUCCESS、
 * 模拟尝试带缺料且 simulation 置位.
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class DagCraftLessTest {

    private SimulationEnv limitedEnv() {
        var env = new SimulationEnv();
        var stone = new GenericStack(appeng.api.stacks.AEItemKey.of(Items.STONE), 1);
        var cobble = new GenericStack(appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE), 1);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addStoredItem(new GenericStack(stone.what(), 30));
        return env;
    }

    private DagPlanAttempt.Result attempt(SimulationEnv env, long amount, boolean simulate) {
        var request = new GenericStack(appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE), amount);
        var calc = new CraftingCalculation(mock(Level.class), env.grid(), env.requester(), request,
                CalculationStrategy.CRAFT_LESS);
        return SimulationEnv.withSimulationFeed(calc,
                () -> DagPlanAttempt.tryPlan(calc, env.craftingService(), request.what(), amount, simulate));
    }

    /** L1:超量(64 > 库存 30)→ INFEASIBLE(契约上的"返回 null",驱动二分). */
    @Test
    public void testExceedingAmountInfeasible() {
        var env = limitedEnv();
        var result = attempt(env, 64, false);
        assertThat(result.outcome()).isEqualTo(DagPlanAttempt.Outcome.INFEASIBLE);
    }

    /** L2:足量(30)→ SUCCESS 且非模拟、库存全额抵扣. */
    @Test
    public void testFeasibleAmountSuccess() {
        var env = limitedEnv();
        var result = attempt(env, 30, false);
        assertThat(result.outcome()).isEqualTo(DagPlanAttempt.Outcome.SUCCESS);
        assertThat(result.plan()).isNotNull();
        assertThat(result.plan().simulation()).isFalse();
        assertThat(result.plan().usedItems().get(appeng.api.stacks.AEItemKey.of(Items.STONE)))
                .isEqualTo(30);
    }

    /** L3:超量模拟尝试 → 非空计划带缺料且 simulation 置位(契约"true, _ -> !null"). */
    @Test
    public void testSimulatedAttemptCarriesMissing() {
        var env = limitedEnv();
        var result = attempt(env, 64, true);
        assertThat(result.outcome()).isEqualTo(DagPlanAttempt.Outcome.SUCCESS);
        assertThat(result.plan()).isNotNull();
        assertThat(result.plan().simulation()).isTrue();
        assertThat(result.plan().missingItems().get(appeng.api.stacks.AEItemKey.of(Items.STONE)))
                .isEqualTo(34);
    }
}
