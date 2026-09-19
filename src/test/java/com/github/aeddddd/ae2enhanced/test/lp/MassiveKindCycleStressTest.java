package com.github.aeddddd.ae2enhanced.test.lp;

import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.canon;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.execOf;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.solve;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.mult;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpModelBuilder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * W 组:种类极多 × 多交叉环压测(诊断用).
 * <p>与 E 组(天文数量)/X 组(极大数量×交叉环)互补:本组把<b>键的种类数</b>
 * 推到数百~一千,环结构高度交叉(弦环/多环互链/压缩-膨胀交叉),断言:</p>
 * <ul>
 * <li>可行场景:allOptimal、零降级(degradedUnits=0)、零赤字;</li>
 * <li>守恒 oracle:逐键 库存+Σ产出−Σ消耗 ≥ 需求(根键 ≥ 订单量);</li>
 * <li>墙钟观测(单单元自举重解预算 3s / 重解轮数 min(2K,256) 的边界).</li>
 * </ul>
 */
public class MassiveKindCycleStressTest {

    private static Block[] palette() {
        return new Block[] { Blocks.STONE, Blocks.COBBLESTONE, Blocks.DIRT, Blocks.PLANKS,
                Blocks.SAND, Blocks.GRAVEL, Blocks.LOG, Blocks.GLASS, Blocks.CLAY,
                Blocks.BRICK_BLOCK };
    }

    /** 第 i 个互不相同的键(10 种方块 × metadata 分段). */
    private static IAEItemStack key(int i) {
        Block[] palette = palette();
        return AEItemStack.fromItemStack(new ItemStack(palette[i % palette.length], 1,
                i / palette.length));
    }

    /**
     * 守恒 oracle:逐键 库存 + Σ(out·t) − Σ(in·t) ≥ demand;根键额外要求 ≥ 订单量.
     * 返回违例描述(空串 = 通过).
     */
    private static String conservationViolation(LpPlanOutcome out, Map<IAEItemStack, Long> stock,
            IAEItemStack root, long target) {
        Map<IAEItemStack, Double> balance = new HashMap<>();
        Map<IAEItemStack, Double> gross = new HashMap<>();
        for (SccLpSolve.Execution e : out.executions) {
            if (e.count <= 1e-6) {
                continue;
            }
            for (IAEItemStack o : e.pattern.getCondensedOutputs()) {
                if (o != null) {
                    IAEItemStack k = canon(o);
                    balance.merge(k, o.getStackSize() * e.count, Double::sum);
                    gross.merge(k, o.getStackSize() * e.count, Double::sum);
                }
            }
            for (Map.Entry<IAEItemStack, Long> in : com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpModelBuilder
                    .condensedInputs(e.pattern, e.variantInputs).entrySet()) {
                balance.merge(in.getKey(), -in.getValue() * e.count, Double::sum);
                gross.merge(in.getKey(), in.getValue() * e.count, Double::sum);
            }
        }
        StringBuilder sb = new StringBuilder();
        IAEItemStack rootKey = canon(root);
        for (Map.Entry<IAEItemStack, Double> e : balance.entrySet()) {
            double net = e.getValue() + stock.getOrDefault(e.getKey(), 0L);
            double floor = e.getKey().equals(rootKey) ? target : 0;
            double tol = Math.max(1, 1e-6 * gross.getOrDefault(e.getKey(), 0.0));
            if (net < floor - tol) {
                sb.append("\n  键 ").append(e.getKey()).append(" 净平衡 ").append((long) net)
                        .append(" < 需求 ").append(floor);
            }
        }
        return sb.toString();
    }

    /**
     * W1:巨型弦环网——N 键主环(1:1) + 两组弦(i→i+37, i→i+91)全部互链成一个
     * 大 SCC,单一增益源 dup 在种子键上.可行;需求 1e12.
     */
    @Test
    public void giantChordRingWeb() {
        int n = 300;
        SimulationEnv env = new SimulationEnv();
        buildChordWeb(env, n, 37, 91);
        env.addPattern(new ProcessingPatternBuilder(mult(key(150), 2))
                .addPreciseInput(1, key(150)).build());
        Map<IAEItemStack, Long> stock = new HashMap<>();
        stock.put(canon(key(150)), 1L);

        long target = 1_000_000_000_000L;
        long t0 = System.nanoTime();
        LpPlanOutcome out = solve(env, key(0), target, stock);
        long wall = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("[W1] N=%d allOptimal=%s degraded=%d units=%d lpUnits=%d iterations=%d wall=%dms(+solve) deficits=%d 种%n",
                n, out.allOptimal, out.degradedUnits, out.units, out.lpUnits, out.iterations, out.wallMs,
                out.deficits.size());
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "可行场景不应赤字: " + out.deficits);
        String violation = conservationViolation(out, stock, key(0), target);
        assertTrue(violation.isEmpty(), "守恒违例:" + violation);
    }

    /**
     * W2:多交叉增益环——R 个 H3 型增益环(A_r→2B_r, B_r→C_r, 2C_r→3A_r),
     * 相邻环 A_r⇄A_{r+1} 双向 1:1 互链,全图一个大 SCC(3R 键).种子在中段环.
     * 可行;需求 A_0 × 1e12.
     */
    @Test
    public void manyInterlinkedGainRings() {
        int rings = 30;
        SimulationEnv env = new SimulationEnv();
        IAEItemStack[] a = new IAEItemStack[rings];
        for (int r = 0; r < rings; r++) {
            a[r] = key(r * 3);
            IAEItemStack b = key(r * 3 + 1);
            IAEItemStack c = key(r * 3 + 2);
            env.addPattern(new ProcessingPatternBuilder(mult(b, 2)).addPreciseInput(1, a[r]).build());
            env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, b).build());
            env.addPattern(new ProcessingPatternBuilder(mult(a[r], 3)).addPreciseInput(2, c).build());
        }
        for (int r = 0; r + 1 < rings; r++) {
            env.addPattern(new ProcessingPatternBuilder(a[r + 1]).addPreciseInput(1, a[r]).build());
            env.addPattern(new ProcessingPatternBuilder(a[r]).addPreciseInput(1, a[r + 1]).build());
        }
        Map<IAEItemStack, Long> stock = new HashMap<>();
        stock.put(canon(a[15]), 2L); // 中段环种子

        long target = 1_000_000_000_000L;
        LpPlanOutcome out = solve(env, a[0], target, stock);
        System.out.printf("[W2] R=%d allOptimal=%s degraded=%d units=%d lpUnits=%d iterations=%d wall=%dms deficits=%d 种%n",
                rings, out.allOptimal, out.degradedUnits, out.units, out.lpUnits, out.iterations,
                out.wallMs, out.deficits.size());
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "可行场景不应赤字: " + out.deficits);
        String violation = conservationViolation(out, stock, a[0], target);
        assertTrue(violation.isEmpty(), "守恒违例:" + violation);
    }

    /**
     * W3:深层压缩×膨胀交叉链——16 层,每层 2K_{i+1}→K_i(压缩)与 K_i→K_{i+1}
     * (1:1 回流)交叉成环,末端 dup(1→2)自增.种子 K15=1.需求 K0 × 1e10.
     * 考验种子自举的级联深度(压缩每级需求翻倍:K15 毛需求 = 2^15·D ≈ 3.3e14).
     */
    @Test
    public void deepCompressionExpansionCross() {
        int depth = 16;
        SimulationEnv env = new SimulationEnv();
        for (int i = 0; i < depth; i++) {
            env.addPattern(new ProcessingPatternBuilder(key(i))
                    .addPreciseInput(2, key(i + 1)).build());
            env.addPattern(new ProcessingPatternBuilder(key(i + 1))
                    .addPreciseInput(1, key(i)).build());
        }
        env.addPattern(new ProcessingPatternBuilder(mult(key(depth), 2))
                .addPreciseInput(1, key(depth)).build());
        Map<IAEItemStack, Long> stock = new HashMap<>();
        stock.put(canon(key(depth)), 1L);

        long target = 10_000_000_000L;
        LpPlanOutcome out = solve(env, key(0), target, stock);
        System.out.printf("[W3] depth=%d allOptimal=%s degraded=%d units=%d lpUnits=%d iterations=%d wall=%dms deficits=%d 种%n",
                depth, out.allOptimal, out.degradedUnits, out.units, out.lpUnits, out.iterations,
                out.wallMs, out.deficits.size());
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "可行场景不应赤字: " + out.deficits);
        String violation = conservationViolation(out, stock, key(0), target);
        assertTrue(violation.isEmpty(), "守恒违例:" + violation);
    }

    /**
     * W4:千键环网——N=1000 主环 + 一组弦,单增益源.观测大规模单元的
     * LP 求解/种子模拟/重演三层的墙钟与轮数边界.
     */
    @Test
    public void thousandKeyRing() {
        int n = 1000;
        SimulationEnv env = new SimulationEnv();
        for (int i = 0; i < n; i++) {
            env.addPattern(new ProcessingPatternBuilder(key((i + 1) % n))
                    .addPreciseInput(1, key(i)).build());
            env.addPattern(new ProcessingPatternBuilder(key((i + 133) % n))
                    .addPreciseInput(1, key(i)).build());
        }
        env.addPattern(new ProcessingPatternBuilder(mult(key(500), 2))
                .addPreciseInput(1, key(500)).build());
        Map<IAEItemStack, Long> stock = new HashMap<>();
        stock.put(canon(key(500)), 1L);

        long target = 1_000_000_000_000L;
        LpPlanOutcome out = solve(env, key(0), target, stock);
        System.out.printf("[W4] N=%d allOptimal=%s degraded=%d units=%d lpUnits=%d iterations=%d wall=%dms deficits=%d 种%n",
                n, out.allOptimal, out.degradedUnits, out.units, out.lpUnits, out.iterations, out.wallMs,
                out.deficits.size());
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "可行场景不应赤字: " + out.deficits);
        String violation = conservationViolation(out, stock, key(0), target);
        assertTrue(violation.isEmpty(), "守恒违例:" + violation);
    }

    /**
     * W5:种类极多的大 BOM 订单——根样板消耗 100 种中间体,每种中间体各自
     * 挂在独立的交叉双增益环簇上(X1 拓扑),簇间经根共享(冷凝 DAG 多单元).
     * 全部种子齐备,可行;观测跨单元需求传播与重演规模.
     */
    @Test
    public void wideBomWithCycleClusters() {
        int kinds = 100;
        SimulationEnv env = new SimulationEnv();
        buildWideBomClusters(env, kinds);
        Map<IAEItemStack, Long> stock = new HashMap<>();
        for (int k = 0; k < kinds; k++) {
            stock.put(canon(key(1 + k * 3)), 2L); // 每簇种子
        }

        long target = 1_000_000_000L;
        LpPlanOutcome out = solve(env, key(0), target, stock);
        System.out.printf("[W5] kinds=%d allOptimal=%s degraded=%d units=%d lpUnits=%d iterations=%d wall=%dms deficits=%d 种%n",
                kinds, out.allOptimal, out.degradedUnits, out.units, out.lpUnits, out.iterations,
                out.wallMs, out.deficits.size());
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "可行场景不应赤字: " + out.deficits);
        String violation = conservationViolation(out, stock, key(0), target);
        assertTrue(violation.isEmpty(), "守恒违例:" + violation);
    }

    /**
     * W6:大规模下的禁约束重解——300 键弦环网,3 个增益源 dup@75/150/225,
     * 净增益相同;种子只在 dup@150.若 LP 首选无种子的 dup,种子校验拦截 →
     * 禁约束压界重解换路(大单元 3s 墙钟预算/2K 轮数上限的实测).
     */
    @Test
    public void massiveUnitBootstrapResolve() {
        int n = 300;
        SimulationEnv env = new SimulationEnv();
        buildChordWeb(env, n, 37);
        ICraftingPatternDetails dup75 = env.addPattern(new ProcessingPatternBuilder(mult(key(75), 2))
                .addPreciseInput(1, key(75)).build());
        ICraftingPatternDetails dup150 = env.addPattern(new ProcessingPatternBuilder(mult(key(150), 2))
                .addPreciseInput(1, key(150)).build());
        ICraftingPatternDetails dup225 = env.addPattern(new ProcessingPatternBuilder(mult(key(225), 2))
                .addPreciseInput(1, key(225)).build());
        Map<IAEItemStack, Long> stock = new HashMap<>();
        stock.put(canon(key(150)), 1L); // 只有中段增益源有种子

        long target = 1_000_000_000_000L;
        LpPlanOutcome out = solve(env, key(0), target, stock);
        System.out.printf("[W6] allOptimal=%s degraded=%d units=%d iterations=%d wall=%dms deficits=%d 种%n"
                + "     dup75=%.0f dup150=%.0f dup225=%.0f%n",
                out.allOptimal, out.degradedUnits, out.units, out.iterations, out.wallMs,
                out.deficits.size(), execOf(out, dup75), execOf(out, dup150), execOf(out, dup225));
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "有种子增益源应可交付: " + out.deficits);
        String violation = conservationViolation(out, stock, key(0), target);
        assertTrue(violation.isEmpty(), "守恒违例:" + violation);
    }

    /**
     * W8:多轮禁约束级联——12 层压缩链(2K_{i+1}→K_i),每层都有 dup 增益源,
     * 种子只在最深层 K12.LP 阶段③首选最浅 dup(执行数最少)逐层被种子校验
     * 拦截 → 12 轮压界重解,最终落到 dup@12(执行数 2^12·D).考验重解轮数
     * 上限 min(2K,256) 与级联收敛性.
     */
    @Test
    public void cascadingBootstrapResolve() {
        int depth = 12;
        SimulationEnv env = new SimulationEnv();
        ICraftingPatternDetails[] dups = new ICraftingPatternDetails[depth + 1];
        for (int i = 0; i < depth; i++) {
            env.addPattern(new ProcessingPatternBuilder(key(i))
                    .addPreciseInput(2, key(i + 1)).build());
        }
        for (int i = 0; i <= depth; i++) {
            dups[i] = env.addPattern(new ProcessingPatternBuilder(mult(key(i), 2))
                    .addPreciseInput(1, key(i)).build());
        }
        Map<IAEItemStack, Long> stock = new HashMap<>();
        stock.put(canon(key(depth)), 1L);

        long target = 1_000_000L;
        LpPlanOutcome out = solve(env, key(0), target, stock);
        System.out.printf("[W8] depth=%d allOptimal=%s degraded=%d iterations=%d wall=%dms deficits=%d 种%n",
                depth, out.allOptimal, out.degradedUnits, out.iterations, out.wallMs, out.deficits.size());
        for (int i = 0; i <= depth; i++) {
            System.out.printf("[W8]   dup@%d=%.0f%n", i, execOf(out, dups[i]));
        }
        assertTrue(out.allOptimal, "应全程最优(级联重解应在轮数预算内收敛): " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "深层种子应可交付: " + out.deficits);
        // dup@12 承担全部放大:2^12 × 1e6 ≈ 4.096e9(净增 1/次)
        double expected = Math.pow(2, depth) * target;
        double actual = execOf(out, dups[depth]);
        assertTrue(Math.abs(actual - expected) <= Math.max(64, expected * 1e-6),
                "dup@12 期望 " + (long) expected + " 实际 " + (long) actual);
        String violation = conservationViolation(out, stock, key(0), target);
        assertTrue(violation.isEmpty(), "守恒违例:" + violation);
    }

    /**
     * W9:大规模混合可行/缺料——100 簇交叉双增益环的大 BOM,第 50 簇无种子
     * (不可交付),其余种子齐备.断言:赤字恰好 1 种(该簇 A,1e9),
     * 其余 99 簇正常交付,不错报不漏报.
     */
    @Test
    public void wideBomOneClusterMissing() {
        int kinds = 100;
        int missingCluster = 50;
        SimulationEnv env = new SimulationEnv();
        buildWideBomClusters(env, kinds);
        Map<IAEItemStack, Long> stock = new HashMap<>();
        for (int k = 0; k < kinds; k++) {
            if (k != missingCluster) {
                stock.put(canon(key(1 + k * 3)), 2L);
            }
        }

        long target = 1_000_000_000L;
        LpPlanOutcome out = solve(env, key(0), target, stock);
        System.out.printf("[W9] allOptimal=%s degraded=%d units=%d iterations=%d wall=%dms deficits=%s%n",
                out.allOptimal, out.degradedUnits, out.units, out.iterations, out.wallMs, out.deficits);
        IAEItemStack missingKey = canon(key(1 + missingCluster * 3));
        assertTrue(out.deficits.size() == 1 && out.deficits.containsKey(missingKey),
                "赤字应恰好为缺种子簇的 A: " + out.deficits);
        double deficit = out.deficits.get(missingKey);
        assertTrue(Math.abs(deficit - target) <= Math.max(64, target * 1e-6),
                "缺料量期望 ~1e9 实际 " + (long) deficit);
    }

    /** 构造 N 键弦环网:主环 1:1 + 指定偏移的弦,全部互链成一个大 SCC. */
    private static void buildChordWeb(SimulationEnv env, int n, int... chordOffsets) {
        for (int i = 0; i < n; i++) {
            env.addPattern(new ProcessingPatternBuilder(key((i + 1) % n))
                    .addPreciseInput(1, key(i)).build());
            for (int off : chordOffsets) {
                env.addPattern(new ProcessingPatternBuilder(key((i + off) % n))
                        .addPreciseInput(1, key(i)).build());
            }
        }
    }

    /**
     * 构造宽 BOM:kinds 簇交叉双增益环(X1 拓扑)挂在公共根样板上,
     * 根样板消耗每种中间体 A_k(W5/W9 共用).
     */
    private static void buildWideBomClusters(SimulationEnv env, int kinds) {
        ProcessingPatternBuilder root = new ProcessingPatternBuilder(key(0));
        for (int k = 0; k < kinds; k++) {
            IAEItemStack a = key(1 + k * 3);
            IAEItemStack b = key(1 + k * 3 + 1);
            IAEItemStack c = key(1 + k * 3 + 2);
            env.addPattern(new ProcessingPatternBuilder(mult(b, 2)).addPreciseInput(1, a).build());
            env.addPattern(new ProcessingPatternBuilder(mult(a, 3))
                    .addPreciseInput(1, b).addPreciseInput(1, c).build());
            env.addPattern(new ProcessingPatternBuilder(mult(c, 2)).addPreciseInput(1, a).build());
            root.addPreciseInput(1, a);
        }
        env.addPattern(root.build());
    }
}
