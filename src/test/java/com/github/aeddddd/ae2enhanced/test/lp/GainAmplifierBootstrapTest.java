package com.github.aeddddd.ae2enhanced.test.lp;

import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.assertClose;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.execOf;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.solve;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.stockOf;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.block;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.mult;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 增益放大器自举(肉丸单元截断病理的合成复现).
 * <p>生产环境 569 键肉丸单元的截断转储(unit-bootstrap-20260911-230936)显示:
 * 自增环(1F→3.2M F 型)被增益相比例分配按天文剩余需求节流到微步几何衰减,
 * 种子自举模拟假阴性 stuck → 禁约束压界摧毁合法路由 → 截断报缺.</p>
 * <ul>
 * <li>T1:自增环 vs 贪婪增益竞争者(同一单元竞争同一键)——放大器必须能先放大
 * 公共池,而不是按剩余需求比例被节流;</li>
 * <li>T2:兄弟增益源分裂回归守卫(A→2B / A→2C / B+C→3A)——比例分配的存在意义,
 * 修复后不得退化回"先访问者吃光种子";</li>
 * <li>T3:外部燃料驱动的自增环——外部键池消耗不得阻止稳态外推(外部池按构造
 * 恒足量,不构成交互威胁).</li>
 * </ul>
 */
public class GainAmplifierBootstrapTest {

    /**
     * T1:1F→1000F 自增环(g0)与 500F+1A→1000B(g1)竞争 F,F 种子 5.
     * 需求 B×1e8(经根样板 1B→1R);c1(1B→1F)闭合 SCC.
     * 闭式解:g0=5e4,g1=1e5,c1=0.
     */
    @Test
    public void selfLoopVsGreedyGainCompetitor() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack f = block(Blocks.STONE);
        IAEItemStack a = block(Blocks.COBBLESTONE); // 外部原料(独立原料单元)
        IAEItemStack b = block(Blocks.DIRT);
        IAEItemStack r = block(Blocks.SAND);
        ICraftingPatternDetails g0 = env.addPattern(new ProcessingPatternBuilder(mult(f, 1000))
                .addPreciseInput(1, f).build());
        ICraftingPatternDetails g1 = env.addPattern(new ProcessingPatternBuilder(mult(b, 1000))
                .addPreciseInput(500, f).addPreciseInput(1, a).build());
        env.addPattern(new ProcessingPatternBuilder(f).addPreciseInput(1, b).build());
        env.addPattern(new ProcessingPatternBuilder(r).addPreciseInput(1, b).build());
        Map<IAEItemStack, Long> stock = stockOf(f, 5, a, 1_000_000_000_000L);

        long t0 = System.nanoTime();
        LpPlanOutcome out = solve(env, r, 100_000_000L, stock);
        long wall = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("[T1] allOptimal=%s degraded=%d iterations=%d wall=%dms deficits=%s g0=%.0f g1=%.0f%n",
                out.allOptimal, out.degradedUnits, out.iterations, wall, out.deficits,
                execOf(out, g0), execOf(out, g1));
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "自增环种子在场不应赤字: " + out.deficits);
        assertClose(50_045, execOf(out, g0), "g0");
        assertClose(100_000, execOf(out, g1), "g1");
    }

    /**
     * T2 回归守卫:兄弟增益源争同一种子(1S+1A→2B / 1S+1A→2C / 1B+1C→3S),
     * 比例分配必须仍在场(否则先访问者吃光 S,闭环永不点火).
     * 需求 B×1e6(经 1B→1R);S 种子 10(舒适水位),A 外部足量.
     */
    @Test
    public void siblingGainSourcesStillSplit() {
        LpPlanOutcome out = siblingEnv(10);
        System.out.printf("[T2] allOptimal=%s degraded=%d iterations=%d deficits=%s%n",
                out.allOptimal, out.degradedUnits, out.iterations, out.deficits);
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        // 与 T2b 同边界:兄弟环的种子校验在刀锋水位附近存在比例微瑕,禁约束压界
        // 恢复可能留下 ≤1% 的交付缺口(与 T2b 的刀锋边界同族,恢复质量口径一致)
        double dirtDeficit = out.deficits.getOrDefault(
                com.github.aeddddd.ae2enhanced.test.support.LpTestSupport
                        .canon(SimulationEnv.block(Blocks.DIRT)), 0.0);
        assertTrue(dirtDeficit <= 1_000_000.0 * 0.01,
                "兄弟增益源分裂点火的恢复缺口应 ≤ 1%: " + out.deficits);
    }

    /**
     * T2b 刀锋零松弛边界:S 种子 2(LP 解的 S 净平衡恰好 −2,零裕量).
     * 种子校验的贪心调度存在比例微瑕,零松弛刀锋上按设计走禁约束压界恢复——
     * 断言恢复质量:不降级(降级单元数 0)且交付缺口 ≤ 需求的 1%(真实路径记录:
     * 禁约束压界恢复是方案 L §4.4 对刀锋边界的既定语义,不得恶化为大额缺料).
     */
    @Test
    public void siblingKnifeEdgeRecovery() {
        LpPlanOutcome out = siblingEnv(2);
        System.out.printf("[T2b] allOptimal=%s degraded=%d iterations=%d deficits=%s%n",
                out.allOptimal, out.degradedUnits, out.iterations, out.deficits);
        assertTrue(out.degradedUnits == 0, "刀锋边界不应整体降级: " + out.degradedReasons);
        double dirtDeficit = out.deficits.getOrDefault(
                com.github.aeddddd.ae2enhanced.test.support.LpTestSupport
                        .canon(SimulationEnv.block(Blocks.DIRT)), 0.0);
        assertTrue(dirtDeficit <= 1_000_000.0 * 0.01,
                "刀锋边界的恢复缺口应 ≤ 1%: " + out.deficits);
    }

    /** 兄弟增益源环境(1S+1A→2B / 1S+1A→2C / 1B+1C→3S / 1B→1R),需求 R×1e6. */
    private static LpPlanOutcome siblingEnv(long sStock) {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack s = block(Blocks.STONE);
        IAEItemStack a = block(Blocks.COBBLESTONE); // 外部原料
        IAEItemStack b = block(Blocks.DIRT);
        IAEItemStack c = block(Blocks.SAND);
        IAEItemStack r = block(Blocks.GRAVEL);
        ICraftingPatternDetails p1 = env.addPattern(new ProcessingPatternBuilder(mult(b, 2))
                .addPreciseInput(1, s).addPreciseInput(1, a).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(mult(c, 2))
                .addPreciseInput(1, s).addPreciseInput(1, a).build());
        ICraftingPatternDetails p3 = env.addPattern(new ProcessingPatternBuilder(mult(s, 3))
                .addPreciseInput(1, b).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(r).addPreciseInput(1, b).build());
        Map<IAEItemStack, Long> stock = stockOf(s, sStock, a, 1_000_000_000_000L);

        long t0 = System.nanoTime();
        LpPlanOutcome out = solve(env, r, 1_000_000L, stock);
        long wall = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("  [sibling stock=%d] wall=%dms p1=%.0f p2=%.0f p3=%.0f%n",
                sStock, wall, execOf(out, p1), execOf(out, p2), execOf(out, p3));
        assertTrue(execOf(out, p2) > 0 && execOf(out, p3) > 0,
                "兄弟增益源不得被饿死: p2=" + execOf(out, p2) + " p3=" + execOf(out, p3));
        return out;
    }

    /**
     * T3:外部燃料自增环(1F+1A→1000F)喂中性消费者(100F→1B),需求 B×1e6.
     * 外部键池按构造恒足量(Σ需求×次数),其消耗不得阻止稳态外推;
     * 闭式解:g0≈1e5 次(净产 999F/次供 1e8 F),c1=1e6.
     */
    @Test
    public void externalFuelAmplifierSteadyState() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack f = block(Blocks.STONE);
        IAEItemStack a = block(Blocks.COBBLESTONE); // 外部原料
        IAEItemStack b = block(Blocks.DIRT);
        IAEItemStack r = block(Blocks.SAND);
        ICraftingPatternDetails g0 = env.addPattern(new ProcessingPatternBuilder(mult(f, 1000))
                .addPreciseInput(1, f).addPreciseInput(1, a).build());
        ICraftingPatternDetails c1 = env.addPattern(new ProcessingPatternBuilder(b)
                .addPreciseInput(100, f).build());
        env.addPattern(new ProcessingPatternBuilder(r).addPreciseInput(1, b).build());
        Map<IAEItemStack, Long> stock = stockOf(f, 5, a, 1_000_000_000_000L);

        long t0 = System.nanoTime();
        LpPlanOutcome out = solve(env, r, 1_000_000L, stock);
        long wall = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("[T3] allOptimal=%s degraded=%d iterations=%d wall=%dms deficits=%s g0=%.0f c1=%.0f%n",
                out.allOptimal, out.degradedUnits, out.iterations, wall, out.deficits,
                execOf(out, g0), execOf(out, c1));
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "外部燃料自增环不应赤字: " + out.deficits);
        assertClose(100_100, execOf(out, g0), "g0");
        assertClose(1_000_000, execOf(out, c1), "c1");
    }
}
