package com.github.aeddddd.ae2enhanced.test.lp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.specialcrafting.lp.ExactSimplex;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpModel;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpResult;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SparseMatrix;

/**
 * 精确有理数字典单纯形({@link ExactSimplex})验证:小题精确答案、成环增益、
 * 界约束、精确不可行证明.
 */
public class ExactSimplexTest {

    private static SparseMatrix cols(int rows, java.util.List<java.util.Map<Integer, Double>> columns) {
        return SparseMatrix.fromColumns(rows, columns);
    }

    private static java.util.Map<Integer, Double> col(int row, double v) {
        return java.util.Collections.singletonMap(row, v);
    }

    /** min x+y, x+y+d−s=4, 成本(1,1,1,0) → obj=4. */
    @Test
    public void tinyExact() {
        SparseMatrix a = cols(1, java.util.Arrays.asList(col(0, 1), col(0, 1), col(0, -1), col(0, 1)));
        LpModel m = new LpModel(a, new double[] { 4 }, new double[] { 1, 1, 0, 1 },
                new double[4], new double[] { LpModel.INF, LpModel.INF, LpModel.INF, LpModel.INF });
        LpResult r = ExactSimplex.solve(m);
        assertThat(r.status).as(r.reason).isEqualTo(LpResult.Status.OPTIMAL);
        assertThat(r.objective).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(r.x[0] + r.x[1]).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(r.x[3]).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-12));
    }

    /** min x+2y → x=4,y=0,obj=4. */
    @Test
    public void tinyCosts() {
        SparseMatrix a = cols(1, java.util.Arrays.asList(col(0, 1), col(0, 1), col(0, -1), col(0, 1)));
        LpModel m = new LpModel(a, new double[] { 4 }, new double[] { 1, 2, 0, 1 },
                new double[4], new double[] { LpModel.INF, LpModel.INF, LpModel.INF, LpModel.INF });
        LpResult r = ExactSimplex.solve(m);
        assertThat(r.status).as(r.reason).isEqualTo(LpResult.Status.OPTIMAL);
        assertThat(r.objective).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(r.x[0]).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(r.x[1]).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-12));
    }

    /** 上界行:min u+v, 2u+v+d−s=4, u∈[0,1] → u=1,v=2,obj=3. */
    @Test
    public void tinyUpperBound() {
        SparseMatrix a = cols(1, java.util.Arrays.asList(col(0, 2), col(0, 1), col(0, -1), col(0, 1)));
        LpModel m = new LpModel(a, new double[] { 4 }, new double[] { 1, 1, 0, 1 },
                new double[4], new double[] { 1, LpModel.INF, LpModel.INF, LpModel.INF });
        LpResult r = ExactSimplex.solve(m);
        assertThat(r.status).as(r.reason).isEqualTo(LpResult.Status.OPTIMAL);
        assertThat(r.objective).isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(r.x[0]).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(r.x[1]).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-12));
    }

    /** 成环增益:1.5x2−x1+dA−sA=2, 2x1−x2+dB−sB=0 → obj=0, x1=1,x2=2(分数系数). */
    @Test
    public void tinyCycleExact() {
        SparseMatrix a = cols(2, java.util.Arrays.asList(
                mapOf(new int[] { 0, 1 }, new double[] { -1, 2 }), // x1
                mapOf(new int[] { 0, 1 }, new double[] { 1.5, -1 }), // x2
                col(0, -1), col(1, -1), // sA, sB
                col(0, 1), col(1, 1))); // dA, dB
        LpModel m = new LpModel(a, new double[] { 2, 0 }, new double[] { 0, 0, 0, 0, 1, 1 },
                new double[6], new double[] { LpModel.INF, LpModel.INF, LpModel.INF, LpModel.INF,
                        LpModel.INF, LpModel.INF });
        LpResult r = ExactSimplex.solve(m);
        assertThat(r.status).as(r.reason).isEqualTo(LpResult.Status.OPTIMAL);
        assertThat(r.objective).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(r.x[0]).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(r.x[1]).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-12));
    }

    /** 精确不可行证明:x=1 与 x=2 两行矛盾(无松弛/赤字列)→ INFEASIBLE. */
    @Test
    public void provenInfeasible() {
        // 单列在两行各有系数 1:x=1 与 x=2 矛盾
        SparseMatrix a = cols(2, java.util.Collections.singletonList(
                mapOf(new int[] { 0, 1 }, new double[] { 1, 1 })));
        LpModel m = new LpModel(a, new double[] { 1, 2 }, new double[] { 1 },
                new double[] { 0 }, new double[] { LpModel.INF });
        LpResult r = ExactSimplex.solve(m);
        assertThat(r.status).as(r.reason).isEqualTo(LpResult.Status.INFEASIBLE);
    }

    private static java.util.Map<Integer, Double> mapOf(int[] rows, double[] vals) {
        java.util.Map<Integer, Double> out = new java.util.TreeMap<>();
        for (int i = 0; i < rows.length; i++) {
            out.put(rows[i], vals[i]);
        }
        return out;
    }
}
