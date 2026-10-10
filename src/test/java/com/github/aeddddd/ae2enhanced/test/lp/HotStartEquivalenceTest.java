package com.github.aeddddd.ae2enhanced.test.lp;

import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.buildMiniWeb;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.canon;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.UnitSolution;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.PruneSettings;
import com.github.aeddddd.ae2enhanced.specialcrafting.StableOrder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpHotStart;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;
import com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.MiniWeb;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * P-A 热启动等价性测试: {@code ae2e.hotStart=off}(冷启动基线)与 {@code on}(全程热启动)
 * 必须给出<b>同一份计划</b>——热启动只复用基/因子化/缩放方案, 不得改变最优解。
 *
 * <p>断言口径(与验收一致):</p>
 * <ul>
 * <li>赤字逐键相同(键集 + 数值);</li>
 * <li>整数化缺料逐键相同(精确);</li>
 * <li>总执行数相同, 且逐样板执行计数在单列单元上逐位相同(多样板单元允许并列漂移);</li>
 * <li>热启动总迭代数 ≤ 冷启动(方向性);</li>
 * <li>至少命中一次真实热启动(否则本测试无意义)。</li>
 * </ul>
 * <p>夹具复用 {@link DeterministicOrderTest} 的基础设施(迷你蛛网 / 弦环网 / 千键环网)。</p>
 */
public class HotStartEquivalenceTest {

    @BeforeAll
    public static void boot() {
        com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap.boot();
    }

    /** 一次求解的观测快照(冷/热两侧同口径)。 */
    private static final class Obs {
        final LpPlanOutcome out;

        Obs(LpPlanOutcome out) {
            this.out = out;
        }
    }

    // ===== 夹具 =====

    /** 迷你蛛网:50 键环 + dup@5/25/45, 种子 K25=1, 请求 K0×200. */
    private static Obs solveMiniWeb(SimulationEnv env, LpHotStart.Chain chain) {
        MiniWeb web = buildMiniWeb(env);
        env.addStoredItem(web.keys[25]);
        return solve(env, web.keys[0], 200, chain);
    }

    /** 弦环网:48 键主环 + 弦 {5,17} + dup@24, 种子 K24=1, 请求 K0×1e9. */
    private static Obs solveChordWeb(SimulationEnv env, LpHotStart.Chain chain) {
        int n = 48;
        int[] offsets = { 5, 17 };
        for (int i = 0; i < n; i++) {
            env.addPattern(new ProcessingPatternBuilder(key((i + 1) % n))
                    .addPreciseInput(1, key(i)).build());
            for (int off : offsets) {
                env.addPattern(new ProcessingPatternBuilder(key((i + off) % n))
                        .addPreciseInput(1, key(i)).build());
            }
        }
        env.addPattern(new ProcessingPatternBuilder(
                com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.mult(key(24), 2))
                .addPreciseInput(1, key(24)).build());
        env.addStoredItem(key(24));
        return solve(env, key(0), 1_000_000_000L, chain);
    }

    /** 千键环网:1000 键主环 + 弦 i→i+133 + 增益源 K500(与 MassiveKindCycleStressTest W4 同构). */
    private static Obs solveThousandRing(SimulationEnv env, LpHotStart.Chain chain) {
        int n = 1000;
        for (int i = 0; i < n; i++) {
            env.addPattern(new ProcessingPatternBuilder(key((i + 1) % n))
                    .addPreciseInput(1, key(i)).build());
            env.addPattern(new ProcessingPatternBuilder(key((i + 133) % n))
                    .addPreciseInput(1, key(i)).build());
        }
        env.addPattern(new ProcessingPatternBuilder(
                com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.mult(key(500), 2))
                .addPreciseInput(1, key(500)).build());
        env.addStoredItem(key(500));
        return solve(env, key(0), 1_000_000_000_000L, chain);
    }

    /**
     * 自举重解夹具(与 MassiveKindCycleStressTest W6 同构):300 键弦环网 +

     * 三个净增益相同的 dup 源(种子只在 dup@150). LP 首选无种子的增益源会被
     * 种子校验拦截 ⇒ 压界重解多轮 ⇒ 同维度模型反复求解 ⇒ 热启动真正命中.
     */
    private static Obs solveBootstrapUnit(SimulationEnv env, LpHotStart.Chain chain) {
        int n = 300;
        com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.buildChordWeb(env, n, 37);
        for (int at : new int[] { 75, 150, 225 }) {
            env.addPattern(new ProcessingPatternBuilder(
                    com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.mult(key(at), 2))
                    .addPreciseInput(1, key(at)).build());
        }
        env.addStoredItem(key(150)); // 只有中段增益源有种子
        return solve(env, key(0), 1_000_000_000_000L, chain);
    }

    private static Obs solve(SimulationEnv env, IAEItemStack root, long target, LpHotStart.Chain chain) {
        ICraftingGrid cc = env.craftingGrid();
        NetworkPatternIndex index = NetworkPatternIndex.of(cc);
        Map<IAEItemStack, Long> stock = new LinkedHashMap<>();
        for (IAEItemStack stack : env.networkStorage()) {
            if (stack.getStackSize() > 0) {
                stock.merge(canon(stack), stack.getStackSize(), Long::sum);
            }
        }
        LpPlanOutcome out = CondensationPlanner.solve(cc, index, root, target, stock,
                new PruneSettings(PruneSettings.Mode.FULL, false, false), chain);
        return new Obs(out);
    }

    /** 简单链 R→A→B(每单元单样板列):用于"单列单元执行计数逐位相同"这条断言。 */
    private static Obs solveSimpleChain(SimulationEnv env, LpHotStart.Chain chain) {
        IAEItemStack r = appeng.util.item.AEItemStack.fromItemStack(
                new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.SAND));
        IAEItemStack a = appeng.util.item.AEItemStack.fromItemStack(
                new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.STONE));
        IAEItemStack b = appeng.util.item.AEItemStack.fromItemStack(
                new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.COBBLESTONE));
        env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, r).build());
        env.addPattern(new ProcessingPatternBuilder(
                com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.mult(b, 2))
                .addPreciseInput(1, a).build());
        env.addStoredItem(com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.mult(r, 3));
        return solve(env, b, 10, chain);
    }

    // ===== 断言 =====

    private static void assertEquivalent(String label, Obs cold, Obs hot) {
        assertEquals(keyOf(cold.out.deficits.keySet()), keyOf(hot.out.deficits.keySet()),
                label + " 赤字键集不一致");
        for (IAEItemStack k : cold.out.deficits.keySet()) {
            assertEquals(cold.out.deficits.get(k), hot.out.deficits.get(k), 1e-6,
                    label + " 赤字值不一致 key=" + StableOrder.keyOf(k));
        }
        assertEquals(execCounts(cold.out).keySet(), execCounts(hot.out).keySet(),
                label + " 执行记录键集不一致");
        assertEquals(totalExecutions(cold.out), totalExecutions(hot.out), label + " 总执行数不一致");
        // 单列单元的执行计数必须逐位相同(多样板单元允许并列漂移)
        Map<String, Double> coldSingle = singleColumnCounts(cold.out);
        Map<String, Double> hotSingle = singleColumnCounts(hot.out);
        assertEquals(coldSingle.keySet(), hotSingle.keySet(), label + " 单列单元键集不一致");
        for (Map.Entry<String, Double> e : coldSingle.entrySet()) {
            assertEquals(Double.doubleToLongBits(e.getValue()),
                    Double.doubleToLongBits(hotSingle.get(e.getKey())),
                    label + " 单列单元执行计数漂移 " + e.getKey());
        }
        assertTrue(hot.out.iterations <= cold.out.iterations,
                label + " 热启动迭代数高于冷启动: " + cold.out.iterations + " → " + hot.out.iterations);
        System.out.printf("[HOT-EQ] %s: 迭代 %d→%d 执行 %d→%d 缺料 %d→%d 单列单元 %d 个%n",
                label, cold.out.iterations, hot.out.iterations, totalExecutions(cold.out),
                totalExecutions(hot.out), cold.out.deficits.size(), hot.out.deficits.size(),
                coldSingle.size());
    }

    private static Map<String, Long> execCounts(LpPlanOutcome out) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Execution exec : out.executions) {
            if (exec.count <= 1e-6) {
                continue;
            }
            counts.merge(execSignature(exec), Math.round(exec.count), Long::sum);
        }
        return counts;
    }

    private static long totalExecutions(LpPlanOutcome out) {
        long total = 0;
        for (Execution exec : out.executions) {
            if (exec.count > 1e-6) {
                total += Math.round(exec.count);
            }
        }
        return total;
    }

    /** 单列单元的 (键集签名 → LP 执行计数), 逐位比对用。 */
    private static Map<String, Double> singleColumnCounts(LpPlanOutcome out) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (UnitSolution unit : out.unitSolutions) {
            if (unit.executions.size() != 1) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (IAEItemStack k : StableOrder.sortedKeys(unit.keys)) {
                sb.append(StableOrder.keyOf(k)).append(';');
            }
            map.put(sb.toString(), unit.executions.get(0).count);
        }
        return map;
    }

    private static List<String> keyOf(java.util.Collection<IAEItemStack> keys) {
        List<String> out = new ArrayList<>();
        for (IAEItemStack k : StableOrder.sortedKeys(keys)) {
            out.add(StableOrder.keyOf(k));
        }
        return out;
    }

    private static String execSignature(Execution exec) {
        StringBuilder sb = new StringBuilder(StableOrder.signatureOf(exec.pattern));
        sb.append("@v{");
        boolean first = true;
        for (Map.Entry<IAEItemStack, Long> e : StableOrder.sortedEntries(exec.variantInputs)) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getValue()).append('x').append(StableOrder.keyOf(e.getKey()));
        }
        return sb.append('}').toString();
    }

    // ===== 用例 =====

    @Test
    public void miniWebHotEqualsCold() {
        LpHotStart.Chain coldChain = LpHotStart.Chain.disabled();
        LpHotStart.Chain hotChain = LpHotStart.Chain.of(LpHotStart.Mode.ON);
        Obs cold = solveMiniWeb(new SimulationEnv(), coldChain);
        Obs hot = solveMiniWeb(new SimulationEnv(), hotChain);
        assertEquivalent("迷你蛛网", cold, hot);
    }

    @Test
    public void chordWebHotEqualsCold() {
        LpHotStart.Chain coldChain = LpHotStart.Chain.disabled();
        LpHotStart.Chain hotChain = LpHotStart.Chain.of(LpHotStart.Mode.ON);
        Obs cold = solveChordWeb(new SimulationEnv(), coldChain);
        Obs hot = solveChordWeb(new SimulationEnv(), hotChain);
        assertEquivalent("弦环网", cold, hot);
    }

    /** 自举重解场景:热启动必须真正命中, 且结果与冷启动逐键一致。 */
    @Test
    public void bootstrapRoundsHotEqualsCold() {
        LpHotStart.Chain coldChain = LpHotStart.Chain.disabled();
        LpHotStart.Chain hotChain = LpHotStart.Chain.of(LpHotStart.Mode.ON);
        Obs cold = solveBootstrapUnit(new SimulationEnv(), coldChain);
        Obs hot = solveBootstrapUnit(new SimulationEnv(), hotChain);
        assertEquivalent("自举重解单元(300 键)", cold, hot);
        System.out.println("[HOT-EQ] 自举重解单元冷启动命中=" + coldChain.hotReuses()
                + " 热启动命中=" + hotChain.hotReuses());
        assertTrue(hotChain.hotReuses() > 0, "该夹具未命中任何热启动(测试失效)");
    }

    @Test
    public void thousandRingHotEqualsCold() {
        LpHotStart.Chain coldChain = LpHotStart.Chain.disabled();
        LpHotStart.Chain hotChain = LpHotStart.Chain.of(LpHotStart.Mode.ON);
        Obs cold = solveThousandRing(new SimulationEnv(), coldChain);
        Obs hot = solveThousandRing(new SimulationEnv(), hotChain);
        assertEquivalent("千键环网", cold, hot);
        // 该夹具的自举轮数决定是否真的命中热启动; 未命中时只报告(不硬断言)
        System.out.println("[HOT-EQ] 千键环网热启动命中次数=" + hotChain.hotReuses());
    }

    @Test
    public void verifyModeMatchesOnMode() {
        LpHotStart.Chain verifyChain = LpHotStart.Chain.of(LpHotStart.Mode.VERIFY);
        LpHotStart.Chain onChain = LpHotStart.Chain.of(LpHotStart.Mode.ON);
        Obs verify = solveChordWeb(new SimulationEnv(), verifyChain);
        Obs on = solveChordWeb(new SimulationEnv(), onChain);
        assertEquivalent("弦环网(verify vs on)", verify, on);
    }

    @Test
    public void offModeNeverReuses() {
        LpHotStart.Chain offChain = LpHotStart.Chain.disabled();
        solveChordWeb(new SimulationEnv(), offChain);
        assertEquals(0, offChain.hotReuses(), "off 档不得发生热启动");
    }

    /** 单列单元夹具:执行计数必须逐位相同(覆盖"单列单元 count 逐位相同"这条验收口径)。 */
    @Test
    public void singleColumnUnitCountsBitIdentical() {
        LpHotStart.Chain coldChain = LpHotStart.Chain.disabled();
        LpHotStart.Chain hotChain = LpHotStart.Chain.of(LpHotStart.Mode.ON);
        Obs cold = solveSimpleChain(new SimulationEnv(), coldChain);
        Obs hot = solveSimpleChain(new SimulationEnv(), hotChain);
        assertEquivalent("简单链", cold, hot);
        assertEquals(2, singleColumnCounts(cold.out).size(), "夹具应含 2 个单列单元(测试失效)");
    }
}
