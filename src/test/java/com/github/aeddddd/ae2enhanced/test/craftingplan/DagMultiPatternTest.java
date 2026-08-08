package com.github.aeddddd.ae2enhanced.test.craftingplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.world.item.Items;

import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * 多样板接管(DAG 多分支)测试:镜像原生多分支"分支 1 尽力 → 分支 2"语义,
 * 但按供给容量整批——修复"多样板 key × 极大数量"的 O(数量) 下单陷阱.
 * <ul>
 * <li>原料充足:分支 1 全满足,逐字段 parity;</li>
 * <li>分支 1 原料有限:分支 1 尽力 + 分支 2 补足,逐字段 parity;</li>
 * <li>两分支均不足:缺料语义 parity;</li>
 * <li>极大数量(1e12):DAG O(1) 完成且分支分配正确(原生此时为小时级,不可比).</li>
 * </ul>
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class DagMultiPatternTest {

    private static final CalculationStrategy REPORT = CalculationStrategy.REPORT_MISSING_ITEMS;

    private static AEItemKey key(net.minecraft.world.item.Item item) {
        return AEItemKey.of(item);
    }

    private record Env(SimulationEnv env, IPatternDetails branch1, IPatternDetails branch2) {
    }

    /** R(stone) ← M(iron_ingot);M 有两个干净分支:C1(iron_ore)、C2(raw_iron). */
    private static Env multiEnv(long stockC1, long stockC2) {
        var env = new SimulationEnv();
        var p1 = new ProcessingPatternBuilder(new GenericStack(key(Items.IRON_INGOT), 1))
                .addPreciseInput(1, new GenericStack(key(Items.IRON_ORE), 1)).build();
        var p2 = new ProcessingPatternBuilder(new GenericStack(key(Items.IRON_INGOT), 1))
                .addPreciseInput(1, new GenericStack(key(Items.RAW_IRON), 1)).build();
        env.addPattern(p1);
        env.addPattern(p2);
        env.addPattern(new ProcessingPatternBuilder(new GenericStack(key(Items.STONE), 1))
                .addPreciseInput(1, new GenericStack(key(Items.IRON_INGOT), 1)).build());
        env.addStoredItem(key(Items.IRON_ORE), stockC1);
        env.addStoredItem(key(Items.RAW_IRON), stockC2);
        return new Env(env, p1, p2);
    }

    private static GenericStack request(long amount) {
        return new GenericStack(key(Items.STONE), amount);
    }

    @Test
    public void dualBranchAbundantParity() {
        var e = multiEnv(1_000_000, 1_000_000);
        var nativePlan = e.env().runSimulation(request(1000), REPORT, 60_000);
        var dagPlan = e.env().runDagSimulation(request(1000), REPORT);

        assertThat(nativePlan.simulation()).isFalse();
        assertThat(dagPlan.simulation()).isFalse();
        // 分支 1 全做(分支 2 不参与)
        assertThat(nativePlan.patternTimes().get(e.branch1())).isEqualTo(1000L);
        assertThat(nativePlan.patternTimes().get(e.branch2())).isNull();
        assertThat(dagPlan.patternTimes()).isEqualTo(nativePlan.patternTimes());
        assertThat(toMap(dagPlan.usedItems())).isEqualTo(toMap(nativePlan.usedItems()));
    }

    @Test
    public void dualBranchScarceParity() {
        var e = multiEnv(400, 1_000_000);
        var nativePlan = e.env().runSimulation(request(1000), REPORT, 60_000);
        var dagPlan = e.env().runDagSimulation(request(1000), REPORT);

        assertThat(nativePlan.simulation()).isFalse();
        assertThat(dagPlan.simulation()).isFalse();
        // 分支 1 尽力 400,分支 2 补足 600
        assertThat(nativePlan.patternTimes().get(e.branch1())).isEqualTo(400L);
        assertThat(nativePlan.patternTimes().get(e.branch2())).isEqualTo(600L);
        assertThat(dagPlan.patternTimes()).isEqualTo(nativePlan.patternTimes());
        assertThat(toMap(dagPlan.usedItems())).isEqualTo(toMap(nativePlan.usedItems()));
    }

    @Test
    public void dualBranchMissingParity() {
        var e = multiEnv(400, 300);
        var nativePlan = e.env().runSimulation(request(1000), REPORT, 60_000);
        var dagPlan = e.env().runDagSimulation(request(1000), REPORT);

        // 缺料语义(原生模拟尝试 = 乐观幻影生产):分支 1 包揽全部 1000 次
        // (400 真实 + 600 幻影),分支 2 不参与,缺料记在分支 1 原料层 iron_ore×600
        assertThat(nativePlan.simulation()).isTrue();
        assertThat(dagPlan.simulation()).isTrue();
        assertThat(nativePlan.patternTimes().get(e.branch1())).isEqualTo(1000L);
        assertThat(nativePlan.patternTimes().get(e.branch2())).isNull();
        assertThat(nativePlan.missingItems().get(key(Items.IRON_ORE))).isEqualTo(600L);
        assertThat(dagPlan.patternTimes()).isEqualTo(nativePlan.patternTimes());
        assertThat(toMap(dagPlan.usedItems())).isEqualTo(toMap(nativePlan.usedItems()));
        assertThat(toMap(dagPlan.missingItems())).isEqualTo(toMap(nativePlan.missingItems()));
    }

    @Test
    public void multiPatternHugeAmount() {
        var e = multiEnv(400_000_000_000L, 4_000_000_000_000L);
        long t0 = System.nanoTime();
        var dagPlan = e.env().runDagSimulation(request(1_000_000_000_000L), REPORT);
        long dagMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(dagPlan.simulation()).as("DAG 应可行").isFalse();
        // 分支 1 尽力 4e11,分支 2 补足 6e11
        assertThat(dagPlan.patternTimes().get(e.branch1())).isEqualTo(400_000_000_000L);
        assertThat(dagPlan.patternTimes().get(e.branch2())).isEqualTo(600_000_000_000L);
        assertThat(dagPlan.usedItems().get(key(Items.IRON_ORE))).isEqualTo(400_000_000_000L);
        assertThat(dagPlan.usedItems().get(key(Items.RAW_IRON))).isEqualTo(600_000_000_000L);
        System.out.printf("[Scale] 多样板(DAG 接管): 数量=1e12, 耗时=%,d ms%n", dagMs);
        assertThat(dagMs).as("多样板接管后应按量 O(1)").isLessThan(2_000);
    }

    private static Map<AEKey, Long> toMap(KeyCounter counter) {
        Map<AEKey, Long> out = new HashMap<>();
        for (var key : counter.keySet()) {
            out.put(key, counter.get(key));
        }
        return out;
    }
}
