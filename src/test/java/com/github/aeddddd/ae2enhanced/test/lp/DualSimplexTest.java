package com.github.aeddddd.ae2enhanced.test.lp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.specialcrafting.lp.DualSimplex;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpModel;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpModelDump;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpResult;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SparseMatrix;

/**
 * 界变量对偶单纯形验证.
 * <ul>
 * <li>小题精确解(人工可算);</li>
 * <li>lp-dumps/ 真实病态转储:原样(带 1e18 封顶,旧单纯形全灭于此)必须收敛;
 * 放开执行数上界后必须收敛到 HiGHS 基准 obj=0(该订单流平衡意义不缺料).</li>
 * </ul>
 * 用法:{code ./gradlew test --tests DualSimplexTest -Dae2e.lpdump.dir=<目录>}.
 */
public class DualSimplexTest {

    /** min x+y, x+y+d−s=4, 0≤x,y≤10, 成本(1,1,1,0) → obj=4(零成本松弛起步). */
    @Test
    public void tinyLpExact() {
        SparseMatrix a = SparseMatrix.fromColumns(1, java.util.Arrays.asList(
                java.util.Collections.singletonMap(0, 1.0), // x
                java.util.Collections.singletonMap(0, 1.0), // y
                java.util.Collections.singletonMap(0, -1.0), // s(零成本盈余:起步基)
                java.util.Collections.singletonMap(0, 1.0))); // d(赤字,成本 1)
        LpModel m = new LpModel(a, new double[] { 4 }, new double[] { 1, 1, 0, 1 },
                new double[4], new double[] { 10, 10, LpModel.INF, LpModel.INF });
        LpResult r = DualSimplex.solve(m);
        assertThat(r.status).as(r.reason).isEqualTo(LpResult.Status.OPTIMAL);
        assertThat(r.objective).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(r.x[0] + r.x[1]).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(r.x[3]).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    /** min x+2y → x=4,y=0,obj=4(界选取 + 退化解). */
    @Test
    public void tinyLpCosts() {
        SparseMatrix a = SparseMatrix.fromColumns(1, java.util.Arrays.asList(
                java.util.Collections.singletonMap(0, 1.0),
                java.util.Collections.singletonMap(0, 1.0),
                java.util.Collections.singletonMap(0, -1.0),
                java.util.Collections.singletonMap(0, 1.0)));
        LpModel m = new LpModel(a, new double[] { 4 }, new double[] { 1, 2, 0, 1 },
                new double[4], new double[] { 10, 10, LpModel.INF, LpModel.INF });
        LpResult r = DualSimplex.solve(m);
        assertThat(r.status).as(r.reason).isEqualTo(LpResult.Status.OPTIMAL);
        assertThat(r.objective).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(r.x[0]).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(r.x[1]).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    /** 上界翻转:min u+v, 2u+v+d−s=4, 0≤u≤1(封顶),v≥0 → u=1,v=2,obj=3. */
    @Test
    public void tinyBoundFlip() {
        SparseMatrix a = SparseMatrix.fromColumns(1, java.util.Arrays.asList(
                java.util.Collections.singletonMap(0, 2.0), // u
                java.util.Collections.singletonMap(0, 1.0), // v
                java.util.Collections.singletonMap(0, -1.0), // s
                java.util.Collections.singletonMap(0, 1.0))); // d
        LpModel m = new LpModel(a, new double[] { 4 }, new double[] { 1, 1, 0, 1 },
                new double[4], new double[] { 1, LpModel.INF, LpModel.INF, LpModel.INF });
        LpResult r = DualSimplex.solve(m);
        assertThat(r.status).as(r.reason).isEqualTo(LpResult.Status.OPTIMAL);
        assertThat(r.objective).isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(r.x[0]).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(r.x[1]).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    /** 成环增益:A→2B, B→1.5A 型净增环 + 需求行的最小赤字 LP(合成单元同构). */
    @Test
    public void tinyCycle() {
        // 键 A,B: 行 A: 1.5·x2 − x1 + dA − sA = demand(2) − stock(0)
        //         行 B: 2·x1 − x2 + dB − sB = 0
        // min dA+dB → 应得 dA=dB=0, x1=0.8·? 解 1.5x2−x1=2, 2x1−x2=0 → x2=2x1
        // → 1.5·2x1−x1=2 → 2x1=2 → x1=1, x2=2.
        SparseMatrix a = SparseMatrix.fromColumns(2, java.util.Arrays.asList(
                mapOf(new int[] { 0, 1 }, new double[] { -1, 2 }), // x1
                mapOf(new int[] { 0, 1 }, new double[] { 1.5, -1 }), // x2
                mapOf(new int[] { 0 }, new double[] { -1 }), // sA
                mapOf(new int[] { 1 }, new double[] { -1 }), // sB
                mapOf(new int[] { 0 }, new double[] { 1 }), // dA
                mapOf(new int[] { 1 }, new double[] { 1 }))); // dB
        LpModel m = new LpModel(a, new double[] { 2, 0 }, new double[] { 0, 0, 0, 0, 1, 1 },
                new double[6], new double[] { LpModel.INF, LpModel.INF, LpModel.INF, LpModel.INF,
                        LpModel.INF, LpModel.INF });
        LpResult r = DualSimplex.solve(m);
        assertThat(r.status).as(r.reason).isEqualTo(LpResult.Status.OPTIMAL);
        assertThat(r.objective).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(r.x[0]).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(r.x[1]).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    public void dumpedModelsAsIs() throws Exception {
        for (File file : dumps()) {
            LpModel model = LpModelDump.read(file);
            long t0 = System.nanoTime();
            LpResult r = DualSimplex.solve(model);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("[DUAL] 原样 " + file.getName() + " → " + r.status
                    + " obj=" + r.objective + " 迭代=" + r.iterations + " " + ms + "ms"
                    + (r.reason != null ? " (" + r.reason + ")" : ""));
            assertThat(r.status).as("原样 %s (%s)", file.getName(), r.reason)
                    .isEqualTo(LpResult.Status.OPTIMAL);
            assertThat(r.objective).as("原样 %s 目标非负", file.getName())
                    .isGreaterThanOrEqualTo(-1e-6);
        }
    }

    @Test
    public void dumpedModelsRelaxed() throws Exception {
        for (File file : dumps()) {
            LpModel model = LpModelDump.read(file);
            double[] upper = model.upper.clone();
            for (int j = 0; j < upper.length; j++) {
                if (upper[j] >= 1e17 && upper[j] < LpModel.INF) {
                    upper[j] = LpModel.INF;
                }
            }
            LpModel relaxed = new LpModel(model.a, model.b, model.cost, model.lower, upper);
            long t0 = System.nanoTime();
            LpResult r = DualSimplex.solve(relaxed);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("[DUAL] 放开 " + file.getName() + " → " + r.status
                    + " obj=" + r.objective + " 迭代=" + r.iterations + " " + ms + "ms"
                    + (r.reason != null ? " (" + r.reason + ")" : ""));
            assertThat(r.status).as("放开 %s (%s)", file.getName(), r.reason)
                    .isEqualTo(LpResult.Status.OPTIMAL);
            // HiGHS 基准:obj=0(该订单流平衡意义不缺料;含禁行的阶段②③模型亦应 ≈0)
            assertThat(r.objective).as("放开 %s 目标 ≈0", file.getName())
                    .isBetween(-1e-6, 1e-2);
        }
    }

    private static File[] dumps() {
        String dirPath = System.getProperty("ae2e.lpdump.dir");
        org.junit.jupiter.api.Assumptions.assumeTrue(dirPath != null, "未指定 -Dae2e.lpdump.dir");
        File dir = new File(dirPath);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".lpm"));
        org.junit.jupiter.api.Assumptions.assumeTrue(files != null && files.length > 0,
                "目录无 .lpm 转储: " + dir);
        java.util.Arrays.sort(files);
        return files;
    }

    private static java.util.Map<Integer, Double> mapOf(int[] rows, double[] vals) {
        java.util.Map<Integer, Double> out = new java.util.TreeMap<>();
        for (int i = 0; i < rows.length; i++) {
            out.put(rows[i], vals[i]);
        }
        return out;
    }
}
