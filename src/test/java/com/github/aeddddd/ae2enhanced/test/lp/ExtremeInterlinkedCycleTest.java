package com.github.aeddddd.ae2enhanced.test.lp;

import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.assertClose;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.canon;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.execOf;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.solve;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.stockOf;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.block;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.mult;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * X 组:极大订单 × 复杂交叉成环的准确性压测(诊断用).
 * <p>与 E 组(简单 3 键环 @ Long.MAX)互补:本组把需求推到 1e17 量级,
 * 同时把环结构复杂化(交叉增益环/互转球/多环竞争),断言:</p>
 * <ul>
 * <li>可行场景:allOptimal、零降级、零赤字、计数满足守恒闭式解(相对 1e-6);</li>
 * <li>缺料场景:缺失量与闭式真值的相对误差 &lt; 1e-6(检验禁行松弛/赤字提取
 * 阈值在大目标值下的泄漏);</li>
 * <li>墙钟有界(单单元自举重解预算 3s,整单打印观测).</li>
 * </ul>
 */
public class ExtremeInterlinkedCycleTest {

    /** 极大订单:1e17(低于 DMAX_CAP=1e18,但 double ULP 已到 16). */
    private static final long D = 100_000_000_000_000_000L;

    /** X1:交叉双增益环(1A→2B, 1B+1C→3A, 1A→2C),极大订单,种子 A=2.
     * 闭式解:x1=D/4, x2=D/2, x3=D/4(B:2x1=x2;C:2x3=x2;A:3x2−x1−x3=D). */
    @Test
    public void interlinkedDoubleGainRing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        IAEItemStack c = block(Blocks.DIRT);
        ICraftingPatternDetails p1 = env.addPattern(
                new ProcessingPatternBuilder(mult(b, 2)).addPreciseInput(1, a).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(mult(a, 3))
                .addPreciseInput(1, b).addPreciseInput(1, c).build());
        ICraftingPatternDetails p3 = env.addPattern(
                new ProcessingPatternBuilder(mult(c, 2)).addPreciseInput(1, a).build());

        LpPlanOutcome out = solve(env, a, D, stockOf(a, 2));
        System.out.printf("[X1] allOptimal=%s degraded=%d iterations=%d wall=%dms deficits=%s%n",
                out.allOptimal, out.degradedUnits, out.iterations, out.wallMs, out.deficits);
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "可行场景不应赤字: " + out.deficits);
        assertClose(D / 4.0, execOf(out, p1), "p1");
        assertClose(D / 2.0, execOf(out, p2), "p2");
        assertClose(D / 4.0, execOf(out, p3), "p3");
    }

    /** X2:三键增益环 + emitter 辅材(H3 极大版),A+W→2B, B→C, 2C→3A,D=1e17.
     * 闭式解:t0=D/2, t1=D, t2=D/2,净产 2A/轮. */
    @Test
    public void threeKeyRingEmitterAux() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        IAEItemStack c = block(Blocks.SAND);
        IAEItemStack w = block(Blocks.GRAVEL);
        ICraftingPatternDetails p0 = env.addPattern(new ProcessingPatternBuilder(mult(b, 2))
                .addPreciseInput(1, a).addPreciseInput(1, w).build());
        ICraftingPatternDetails p1 = env.addPattern(
                new ProcessingPatternBuilder(c).addPreciseInput(1, b).build());
        ICraftingPatternDetails p2 = env.addPattern(
                new ProcessingPatternBuilder(mult(a, 3)).addPreciseInput(2, c).build());
        env.addEmitable(w);

        LpPlanOutcome out = solve(env, a, D, stockOf(a, 2));
        System.out.printf("[X2] allOptimal=%s degraded=%d iterations=%d wall=%dms deficits=%s%n",
                out.allOptimal, out.degradedUnits, out.iterations, out.wallMs, out.deficits);
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "emitter 辅材下不应赤字: " + out.deficits);
        assertClose(D / 2.0, execOf(out, p0), "p0");
        assertClose((double) D, execOf(out, p1), "p1");
        assertClose(D / 2.0, execOf(out, p2), "p2");
    }

    /** X3:互转球(5 键两两 1:1) + 增益源(melt:C→1000L),极大订单 L×1e17,C emitter.
     * 闭式解:melt = D/1000,环流净零. */
    @Test
    public void transmutationBallExtreme() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack l = block(Blocks.STONE);
        IAEItemStack c = block(Blocks.COBBLESTONE);
        Block[] palette = { Blocks.SAND, Blocks.GRAVEL, Blocks.LOG, Blocks.GLASS, Blocks.CLAY };
        IAEItemStack[] ball = new IAEItemStack[palette.length];
        for (int i = 0; i < ball.length; i++) {
            ball[i] = block(palette[i]);
        }
        for (int i = 0; i < ball.length; i++) {
            for (int j = 0; j < ball.length; j++) {
                if (i != j) {
                    env.addPattern(new ProcessingPatternBuilder(ball[j])
                            .addPreciseInput(1, ball[i]).build());
                }
            }
        }
        env.addPattern(new ProcessingPatternBuilder(ball[0]).addPreciseInput(1, l).build());
        env.addPattern(new ProcessingPatternBuilder(l).addPreciseInput(1, ball[0]).build());
        ICraftingPatternDetails melt = env.addPattern(
                new ProcessingPatternBuilder(mult(l, 1000)).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, mult(l, 1000)).build());
        env.addEmitable(c);

        LpPlanOutcome out = solve(env, l, D, stockOf());
        System.out.printf("[X3] allOptimal=%s degraded=%d iterations=%d wall=%dms deficits=%s%n",
                out.allOptimal, out.degradedUnits, out.iterations, out.wallMs, out.deficits);
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "emitter 供种下不应赤字: " + out.deficits);
        assertClose(D / 1000.0, execOf(out, melt), "melt");
    }

    /** X4:极大订单 × 真缺料——缺失量精度(禁行松弛/提取阈值泄漏检验).
     * H3 环,W 库存 1e16(需求 D/2=5e16 → 真缺 4e16),根交付缺口 8e16. */
    @Test
    public void extremeMissingAccuracy() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        IAEItemStack c = block(Blocks.SAND);
        IAEItemStack w = block(Blocks.GRAVEL);
        env.addPattern(new ProcessingPatternBuilder(mult(b, 2))
                .addPreciseInput(1, a).addPreciseInput(1, w).build());
        env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, b).build());
        env.addPattern(new ProcessingPatternBuilder(mult(a, 3)).addPreciseInput(2, c).build());

        LpPlanOutcome out = solve(env, a, D, stockOf(a, 2, w, 10_000_000_000_000_000L));
        System.out.printf("[X4] allOptimal=%s degraded=%d iterations=%d wall=%dms deficits=%s%n",
                out.allOptimal, out.degradedUnits, out.iterations, out.wallMs, out.deficits);
        Double wDeficit = out.deficits.get(canon(w));
        assertTrue(wDeficit != null, "W 应报缺 4e16: " + out.deficits);
        assertClose(4.0e16, wDeficit, "W 缺料");
        // 原生"幻影生产"显示语义:缺料场景交付缺口由原料缺料承担,根键不重复报缺
        assertTrue(!out.deficits.containsKey(canon(a)),
                "根 A 的交付缺口应由 W 缺料表达,不再重复报缺: " + out.deficits);
    }

    /** X5:混合量级缺料——大缺料(4e16)与小缺料(5e7)并存,小缺料不得被吞.
     * p0 消耗 A+W+Z:W 缺 4e16,Z 库存 = D/2 − 5e7(缺 5e7). */
    @Test
    public void mixedMagnitudeMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        IAEItemStack c = block(Blocks.SAND);
        IAEItemStack w = block(Blocks.GRAVEL);
        IAEItemStack z = block(Blocks.CLAY);
        env.addPattern(new ProcessingPatternBuilder(mult(b, 2))
                .addPreciseInput(1, a).addPreciseInput(1, w).addPreciseInput(1, z).build());
        env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, b).build());
        env.addPattern(new ProcessingPatternBuilder(mult(a, 3)).addPreciseInput(2, c).build());

        long zStock = D / 2 - 50_000_000L;
        LpPlanOutcome out = solve(env, a, D,
                stockOf(a, 2, w, 10_000_000_000_000_000L, z, zStock));
        System.out.printf("[X5] allOptimal=%s degraded=%d iterations=%d wall=%dms deficits=%s%n",
                out.allOptimal, out.degradedUnits, out.iterations, out.wallMs, out.deficits);
        Double zDeficit = out.deficits.get(canon(z));
        assertTrue(zDeficit != null, "Z 的小额缺料(5e7)不得被大目标值的容差吞掉: " + out.deficits);
        assertClose(5.0e7, zDeficit, "Z 缺料");
    }

    /** X6:Long.MAX × 交叉双增益环(E 组边界在复杂环上的延伸).
     * 语义基线 = E1:需求封顶 1e18 执行,超出部分根键记缺,不得降级/异常. */
    @Test
    public void longMaxInterlinkedRing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        IAEItemStack c = block(Blocks.DIRT);
        ICraftingPatternDetails p1 = env.addPattern(
                new ProcessingPatternBuilder(mult(b, 2)).addPreciseInput(1, a).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(mult(a, 3))
                .addPreciseInput(1, b).addPreciseInput(1, c).build());
        ICraftingPatternDetails p3 = env.addPattern(
                new ProcessingPatternBuilder(mult(c, 2)).addPreciseInput(1, a).build());

        LpPlanOutcome out = solve(env, a, Long.MAX_VALUE, stockOf(a, 2));
        System.out.printf("[X6] allOptimal=%s degraded=%d iterations=%d wall=%dms deficits=%s%n",
                out.allOptimal, out.degradedUnits, out.iterations, out.wallMs, out.deficits);
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        // 无封顶语义(2026-09 起执行数上界改为真实闭包,不再按 1e18 封顶):
        // p2 执行数 ~Long.MAX/3,根键全额交付无记缺
        double delivered = 3 * execOf(out, p2) - execOf(out, p1) - execOf(out, p3);
        Double aDeficit = out.deficits.get(canon(a));
        assertTrue(aDeficit == null || aDeficit < 1e6, "全额交付无根键记缺: " + out.deficits);
        System.out.printf("[X6] delivered=%.3e deficit=%.3e sum=%.3e (target=%.3e)%n",
                delivered, aDeficit == null ? 0 : aDeficit, delivered + (aDeficit == null ? 0 : aDeficit),
                (double) Long.MAX_VALUE);
        // 守恒:交付 + 赤字 ≈ 需求(种子 2 在双精度 9.2e18 下不可感知)
        assertClose((double) Long.MAX_VALUE, delivered + (aDeficit == null ? 0 : aDeficit), "交付+赤字");
    }

    // ===== 工具(公共助手见 LpTestSupport) =====
}
