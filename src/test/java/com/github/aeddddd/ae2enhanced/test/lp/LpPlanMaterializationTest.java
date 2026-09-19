package com.github.aeddddd.ae2enhanced.test.lp;

import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.buildMiniWeb;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.canon;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.block;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.mult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.MiniWeb;
import com.github.aeddddd.ae2enhanced.test.support.ExecutionHarness;
import com.github.aeddddd.ae2enhanced.test.support.PlanView;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * M5 物化与对账测试:LP 路径全链(求解 → 重演对账 → 物化原生树 → dive/populatePlan).
 * <p>断言 patternTimes(树遍历 crafts 聚合)、usedItems(populatePlan)、
 * missingItems(dive 记账)与手工推导一致.</p>
 */
public class LpPlanMaterializationTest {

    /** 催化合并单元:1A→1X+1B, 1B→1A,种子 A=1,请求 X=10 → p1=10, p2=9, used=A×1. */
    @Test
    public void catalyticMergedUnit() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack b = block(Blocks.DIRT);
        ICraftingPatternDetails p1 = env.addPattern(new ProcessingPatternBuilder(x, b)
                .addPreciseInput(1, a).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(a)
                .addPreciseInput(1, b).build());
        env.addStoredItem(a);

        PlanView plan = PlanView.of(env.runLp(mult(x, 10)));
        assertFalse(plan.simulation(), "计划应成功: " + plan.missingItems());
        assertEquals(10L, plan.patternTimes().getOrDefault(p1, 0L));
        assertEquals(9L, plan.patternTimes().getOrDefault(p2, 0L));
        assertEquals(1L, plan.usedItems().getOrDefault(canon(a), 0L), "used = 种子 A×1");
        assertTrue(plan.missingItems().isEmpty(), "不应缺料: " + plan.missingItems());
    }

    /** 跨单元链:X←A(原料),库存 A=4,请求 X=10 → used=A×4, missing=A×6,模拟标志. */
    @Test
    public void crossUnitChainMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack x = block(Blocks.STONE);
        IAEItemStack a = block(Blocks.COBBLESTONE);
        ICraftingPatternDetails px = env.addPattern(new ProcessingPatternBuilder(x)
                .addPreciseInput(1, a).build());
        env.addStoredItem(mult(a, 4));

        PlanView plan = PlanView.of(env.runLp(mult(x, 10)));
        assertTrue(plan.simulation(), "缺料计划应置模拟标志");
        assertEquals(10L, plan.patternTimes().getOrDefault(px, 0L));
        // usedItems 口径:LpCraftingJob.populatePlan 会把 missing 条目回注 plan 列表
        // (既有显示修复,LP 路径同)——used 4 + missing 6 合并显示为 10
        assertEquals(10L, plan.usedItems().getOrDefault(canon(a), 0L), "used(4) + missing 回注(6)");
        assertEquals(6L, plan.missingItems().getOrDefault(canon(a), 0L));
    }

    /** 无种子自增环:1A→2A 零库存请求 10 → missing=A×10,零执行. */
    @Test
    public void noSeedDupMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        ICraftingPatternDetails dup = env.addPattern(new ProcessingPatternBuilder(mult(a, 2))
                .addPreciseInput(1, a).build());

        PlanView plan = PlanView.of(env.runLp(mult(a, 10)));
        assertTrue(plan.simulation());
        assertEquals(10L, plan.missingItems().getOrDefault(canon(a), 0L));
        assertEquals(0L, plan.patternTimes().getOrDefault(dup, 0L), "无种子不应有执行");
    }

    /** 有种子自增环:库存 A=1,请求 10 → 全额生产 dup=10, used=A×1(点火种子,期末返还),无缺失. */
    @Test
    public void seededDupMaterialized() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        ICraftingPatternDetails dup = env.addPattern(new ProcessingPatternBuilder(mult(a, 2))
                .addPreciseInput(1, a).build());
        env.addStoredItem(a);

        PlanView plan = PlanView.of(env.runLp(mult(a, 10)));
        assertFalse(plan.simulation(), "计划应成功: " + plan.missingItems());
        assertEquals(10L, plan.patternTimes().getOrDefault(dup, 0L));
        assertEquals(1L, plan.usedItems().getOrDefault(canon(a), 0L), "used = 种子 A×1");
        assertTrue(plan.missingItems().isEmpty());
    }

    /** 迷你蛛网(M4 重解路径):dup25=99,环流 25→49→0 各 100,used=种子 K25×1. */
    @Test
    public void miniWebMaterialized() {
        SimulationEnv env = new SimulationEnv();
        MiniWeb web = buildMiniWeb(env);
        env.addStoredItem(web.keys[25]);

        PlanView plan = PlanView.of(env.runLp(mult(web.keys[0], 100)));
        assertFalse(plan.simulation(), "计划应成功: " + plan.missingItems());
        assertEquals(99L, plan.patternTimes().getOrDefault(web.dup25, 0L));
        for (int i = 25; i <= 49; i++) {
            assertEquals(100L, plan.patternTimes().getOrDefault(web.ring[i], 0L), "环流 p" + i);
        }
        assertEquals(1L, plan.usedItems().getOrDefault(canon(web.keys[25]), 0L), "used = 种子 K25×1");
        assertTrue(plan.missingItems().isEmpty());
    }

    /** 发射台键:请求 100 全由发射满足,无执行/无 used/无缺失. */
    @Test
    public void emitterMaterialized() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack e = block(Blocks.STONE);
        env.addEmitable(e);

        PlanView plan = PlanView.of(env.runLp(mult(e, 100)));
        assertFalse(plan.simulation(), "发射台计划应成功");
        assertTrue(plan.patternTimes().isEmpty(), "发射台不应有样板执行");
        assertTrue(plan.usedItems().isEmpty(), "发射台不应有 used: " + plan.usedItems());
        assertTrue(plan.missingItems().isEmpty());
    }

    /** 全额生产:请求物自身库存不抵交付——库存 X 不动,照常生产 X×10,
     * 实取原料 a×10(与原生 CraftingJob.ignore(output) 同语义). */
    @Test
    public void stockDirectDelivery() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack x = block(Blocks.STONE);
        IAEItemStack a = block(Blocks.COBBLESTONE);
        ICraftingPatternDetails p = env.addPattern(
                new ProcessingPatternBuilder(x).addPreciseInput(1, a).build());
        env.addStoredItem(mult(x, 64));
        env.addStoredItem(mult(a, 64));

        PlanView plan = PlanView.of(env.runLp(mult(x, 10)));
        assertFalse(plan.simulation());
        assertEquals(10L, plan.patternTimes().getOrDefault(p, 0L), "库存不抵交付,全额生产");
        assertEquals(10L, plan.usedItems().getOrDefault(canon(a), 0L), "used = 原料 a×10");
        assertEquals(0L, plan.usedItems().getOrDefault(canon(x), 0L), "used 不含请求物本身");
        assertTrue(plan.missingItems().isEmpty());
    }

    /** 执行层端到端:催化合并单元计划提交执行——完成、无死锁、CPU 无残留、交付足额. */
    @Test
    public void catalyticExecutionEndToEnd() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack b = block(Blocks.DIRT);
        ICraftingPatternDetails p1 = env.addPattern(new ProcessingPatternBuilder(x, b)
                .addPreciseInput(1, a).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(a)
                .addPreciseInput(1, b).build());
        env.addStoredItem(a);

        PlanView plan = PlanView.of(env.runLp(mult(x, 10)));
        assertFalse(plan.simulation());
        Map<IAEItemStack, Long> network = new LinkedHashMap<>();
        network.put(canon(a), 1L);
        for (List<ICraftingPatternDetails> order : com.github.aeddddd.ae2enhanced.test.support.ExecutionHarness
                .pushOrders(java.util.Arrays.asList(p1, p2))) {
            com.github.aeddddd.ae2enhanced.test.support.ExecutionHarness.Result result =
                    com.github.aeddddd.ae2enhanced.test.support.ExecutionHarness.execute(plan, network,
                            com.github.aeddddd.ae2enhanced.test.support.ExecutionHarness.Options
                                    .gameDefaults(), order);
            assertTrue(result.completed, "订单应完成 [ticks=" + result.ticks + "]");
            assertFalse(result.deadlock);
            result.cpuInventory.forEach((k, v) -> assertEquals(0L, (long) v, "CPU 残留 " + k));
            assertEquals(10L, result.delivered);
        }
    }

    /** 执行层端到端:有种子自增环计划(used=种子 1,crafts=9)提交执行. */
    @Test
    public void seededDupExecutionEndToEnd() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        ICraftingPatternDetails dup = env.addPattern(new ProcessingPatternBuilder(mult(a, 2))
                .addPreciseInput(1, a).build());
        env.addStoredItem(a);

        PlanView plan = PlanView.of(env.runLp(mult(a, 10)));
        assertFalse(plan.simulation());
        Map<IAEItemStack, Long> network = new LinkedHashMap<>();
        network.put(canon(a), 1L);
        com.github.aeddddd.ae2enhanced.test.support.ExecutionHarness.Result result =
                com.github.aeddddd.ae2enhanced.test.support.ExecutionHarness.execute(plan, network,
                        com.github.aeddddd.ae2enhanced.test.support.ExecutionHarness.Options
                                .gameDefaults(),
                        java.util.Collections.singletonList(dup));
        assertTrue(result.completed, "订单应完成 [ticks=" + result.ticks + "]");
        assertFalse(result.deadlock);
        result.cpuInventory.forEach((k, v) -> assertEquals(0L, (long) v, "CPU 残留 " + k));
        assertEquals(10L, result.delivered);
    }

    // ===== 工具(公共助手见 LpTestSupport) =====
}
