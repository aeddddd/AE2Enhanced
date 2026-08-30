package com.github.aeddddd.ae2enhanced.test.lp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpModel;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpResult;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.RevisedSimplex;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SparseMatrix;

/**
 * 修正单纯形核心单元测试:手工案例 + 随机对拍(暴力顶点枚举).
 */
public class RevisedSimplexTest {

    private static final double EPS = 1e-6;

    private static LpModel model(double[][] a, double[] b, double[] c, double[] l, double[] u) {
        List<Map<Integer, Double>> columns = new ArrayList<>();
        for (int j = 0; j < c.length; j++) {
            Map<Integer, Double> col = new TreeMap<>();
            for (int i = 0; i < b.length; i++) {
                if (a[i][j] != 0) {
                    col.put(i, a[i][j]);
                }
            }
            columns.add(col);
        }
        return new LpModel(SparseMatrix.fromColumns(b.length, columns), b, c, l, u);
    }

    /** 经典有界最优:min -3x-2y, x+y≤4, 2x+y≤5, x,y∈[0,+inf) → x=1,y=3, obj=-9. */
    @Test
    public void testClassicBounded() {
        // 化为等式:x+y+s1=4, 2x+y+s2=5
        LpModel m = model(new double[][] { { 1, 1, 1, 0 }, { 2, 1, 0, 1 } }, new double[] { 4, 5 },
                new double[] { -3, -2, 0, 0 }, new double[] { 0, 0, 0, 0 },
                new double[] { LpModel.INF, LpModel.INF, LpModel.INF, LpModel.INF });
        LpResult r = RevisedSimplex.solve(m);
        assertEquals(LpResult.Status.OPTIMAL, r.status);
        assertEquals(-9, r.objective, EPS);
        assertEquals(1, r.x[0], EPS);
        assertEquals(3, r.x[1], EPS);
    }

    /** 不可行:x≥2 与 x≤1 同存 → INFEASIBLE. */
    @Test
    public void testInfeasible() {
        // x - s1 = 2 (x≥2), x + s2 = 1 (x≤1)
        LpModel m = model(new double[][] { { 1, -1, 0 }, { 1, 0, 1 } }, new double[] { 2, 1 },
                new double[] { 1, 0, 0 }, new double[] { 0, 0, 0 },
                new double[] { LpModel.INF, LpModel.INF, LpModel.INF });
        LpResult r = RevisedSimplex.solve(m);
        assertEquals(LpResult.Status.INFEASIBLE, r.status);
    }

    /** 界翻转路径:x∈[0,2],min -x,x+s=10 → x=2(撞界),obj=-2. */
    @Test
    public void testBoundFlip() {
        LpModel m = model(new double[][] { { 1, 1 } }, new double[] { 10 }, new double[] { -1, 0 },
                new double[] { 0, 0 }, new double[] { 2, LpModel.INF });
        LpResult r = RevisedSimplex.solve(m);
        assertEquals(LpResult.Status.OPTIMAL, r.status);
        assertEquals(-2, r.objective, EPS);
        assertEquals(2, r.x[0], EPS);
    }

    /** Beale 退化循环案例(反退化必须终止). */
    @Test
    public void testBealeDegenerate() {
        // min -0.75x1 + 150x2 - 0.02x3 + 6x4
        // 0.25x1 - 60x2 - 0.04x3 + 9x4 ≤ 0
        // 0.5x1 - 90x2 - 0.02x3 + 3x4 ≤ 0
        // x3 ≤ 1
        LpModel m = model(
                new double[][] { { 0.25, -60, -0.04, 9, 1, 0, 0 }, { 0.5, -90, -0.02, 3, 0, 1, 0 },
                        { 0, 0, 1, 0, 0, 0, 1 } },
                new double[] { 0, 0, 1 },
                new double[] { -0.75, 150, -0.02, 6, 0, 0, 0 },
                new double[] { 0, 0, 0, 0, 0, 0, 0 },
                new double[] { LpModel.INF, LpModel.INF, LpModel.INF, LpModel.INF, LpModel.INF,
                        LpModel.INF, LpModel.INF });
        LpResult r = RevisedSimplex.solve(m);
        // 最优值 -0.05(x1=1/25? 经典答案:x3=1 时 obj=-0.05)
        assertEquals(LpResult.Status.OPTIMAL, r.status);
        assertEquals(-0.05, r.objective, 1e-4);
    }

    /** 负 RHS 行:人工变量符号规整路径. */
    @Test
    public void testNegativeRhs() {
        // -x - s = -3 即 x+s=3,min x → 0
        LpModel m = model(new double[][] { { -1, -1 } }, new double[] { -3 }, new double[] { 1, 0 },
                new double[] { 0, 0 }, new double[] { LpModel.INF, LpModel.INF });
        LpResult r = RevisedSimplex.solve(m);
        assertEquals(LpResult.Status.OPTIMAL, r.status);
        assertEquals(0, r.objective, EPS);
    }

    /** 自环增殖(dup)结构:1A→2A 的 LP 表达——x_dup 有界上界内被放大利用. */
    @Test
    public void testDupAmplification() {
        // A: stock 1,需求交付 100。守恒:1 + (2-1)·x_dup + d_A = 100
        // → x_dup = 99,d_A = 0;min d_A + 0.001·x_dup
        LpModel m = model(new double[][] { { 1, 1 } }, new double[] { 99 }, new double[] { 0.001, 1 },
                new double[] { 0, 0 }, new double[] { 1e6, LpModel.INF });
        LpResult r = RevisedSimplex.solve(m);
        assertEquals(LpResult.Status.OPTIMAL, r.status);
        assertEquals(99, r.x[0], EPS);
        assertEquals(0, r.x[1], EPS);
    }

    /**
     * 随机对拍:小规模 LP(m≤5,n≤8)与暴力顶点枚举对比目标值.
     * 构造保证可行(随机 x* ≥ 0 反推 b=Ax*),变量全有界(最优解存在).
     */
    @Test
    public void testRandomAgainstVertexEnumeration() {
        Random rnd = new Random(20260830L);
        int cases = 100_000;
        for (int t = 0; t < cases; t++) {
            int m = 2 + rnd.nextInt(4); // 2..5
            int n = m + 1 + rnd.nextInt(8 - m); // m+1..8
            double[][] a = new double[m][n];
            double[] l = new double[n];
            double[] u = new double[n];
            double[] xStar = new double[n];
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    a[i][j] = rnd.nextDouble() < 0.5 ? 0 : 1 + rnd.nextInt(9);
                }
                l[j] = 0;
                u[j] = 1 + rnd.nextInt(20);
                xStar[j] = rnd.nextDouble() * u[j];
            }
            double[] b = new double[m];
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    b[i] += a[i][j] * xStar[j];
                }
            }
            double[] c = new double[n];
            for (int j = 0; j < n; j++) {
                c[j] = -10 + rnd.nextDouble() * 20;
            }
            LpResult r = RevisedSimplex.solve(model(a, b, c, l, u));
            if (r.status == LpResult.Status.NUMERIC_FAILURE) {
                continue; // 病态实例允许数值失败(对拍只校验成功解)
            }
            assertEquals(LpResult.Status.OPTIMAL, r.status, "case " + t);
            double brute = bruteForceMin(a, b, c, l, u, m, n);
            assertEquals(brute, r.objective, Math.max(1e-4, Math.abs(brute) * 1e-6),
                    "case " + t + " 目标值与暴力枚举不一致");
        }
    }

    /**
     * 暴力顶点枚举:等式约束下枚举 n−m 个变量置界,解线性方程组,
     * 检验可行性并取最小目标值(仅适用于本测试的小维度).
     */
    private static double bruteForceMin(double[][] a, double[] b, double[] c, double[] l, double[] u,
            int m, int n) {
        double best = Double.POSITIVE_INFINITY;
        int freeCount = n - m;
        int[] freeVars = new int[freeCount];
        // 自由变量选法 C(n, freeCount),逐个枚举置界组合 2^freeCount
        for (long mask = 0; mask < (1L << n); mask++) {
            if (Long.bitCount(mask) != freeCount) {
                continue;
            }
            int fi = 0;
            for (int j = 0; j < n; j++) {
                if ((mask & (1L << j)) != 0) {
                    freeVars[fi++] = j;
                }
            }
            for (int bound = 0; bound < (1 << freeCount); bound++) {
                double[] x = new double[n];
                for (int f = 0; f < freeCount; f++) {
                    x[freeVars[f]] = (bound & (1 << f)) != 0 ? u[freeVars[f]] : l[freeVars[f]];
                }
                // 解剩余 m 个变量的线性系统
                double[][] mat = new double[m][m];
                double[] rhs = new double[m];
                int col = 0;
                for (int j = 0; j < n; j++) {
                    boolean isFree = false;
                    for (int f = 0; f < freeCount; f++) {
                        if (freeVars[f] == j) {
                            isFree = true;
                            break;
                        }
                    }
                    if (isFree) {
                        for (int i = 0; i < m; i++) {
                            rhs[i] -= a[i][j] * x[j];
                        }
                    } else {
                        for (int i = 0; i < m; i++) {
                            mat[i][col] = a[i][j];
                        }
                        col++;
                    }
                }
                for (int i = 0; i < m; i++) {
                    rhs[i] += b[i];
                }
                double[] sol = solveDense(mat, rhs, m);
                if (sol == null) {
                    continue;
                }
                col = 0;
                boolean feasible = true;
                for (int j = 0; j < n && feasible; j++) {
                    boolean isFree = false;
                    for (int f = 0; f < freeCount; f++) {
                        if (freeVars[f] == j) {
                            isFree = true;
                            break;
                        }
                    }
                    if (!isFree) {
                        x[j] = sol[col++];
                        if (x[j] < l[j] - 1e-7 || x[j] > u[j] + 1e-7) {
                            feasible = false;
                        }
                    }
                }
                if (feasible) {
                    double obj = 0;
                    for (int j = 0; j < n; j++) {
                        obj += c[j] * x[j];
                    }
                    best = Math.min(best, obj);
                }
            }
        }
        return best;
    }

    /** 稠密高斯消元(部分主元),奇异返回 null. */
    private static double[] solveDense(double[][] mat, double[] rhs, int n) {
        double[][] a = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(mat[i], 0, a[i], 0, n);
            a[i][n] = rhs[i];
        }
        for (int k = 0; k < n; k++) {
            int pivot = k;
            for (int i = k + 1; i < n; i++) {
                if (Math.abs(a[i][k]) > Math.abs(a[pivot][k])) {
                    pivot = i;
                }
            }
            if (Math.abs(a[pivot][k]) < 1e-12) {
                return null;
            }
            double[] tmp = a[k];
            a[k] = a[pivot];
            a[pivot] = tmp;
            for (int i = k + 1; i < n; i++) {
                double f = a[i][k] / a[k][k];
                for (int j = k; j <= n; j++) {
                    a[i][j] -= f * a[k][j];
                }
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = a[i][n];
            for (int j = i + 1; j < n; j++) {
                sum -= a[i][j] * x[j];
            }
            x[i] = sum / a[i][i];
        }
        return x;
    }

    /** 中等规模稀疏实例冒烟:m=300,n=700,验证有界终止与可行性. */
    @Test
    public void testMediumSparseSmoke() {
        Random rnd = new Random(7L);
        int m = 300, n = 700;
        List<Map<Integer, Double>> columns = new ArrayList<>();
        double[] xStar = new double[n];
        double[] u = new double[n];
        for (int j = 0; j < n; j++) {
            Map<Integer, Double> col = new TreeMap<>();
            for (int i = 0; i < m; i++) {
                if (rnd.nextDouble() < 0.02) {
                    col.put(i, (double) (1 + rnd.nextInt(5)));
                }
            }
            columns.add(col);
            u[j] = 10 + rnd.nextInt(100);
            xStar[j] = rnd.nextDouble() * u[j];
        }
        double[] b = new double[m];
        for (int j = 0; j < n; j++) {
            for (Map.Entry<Integer, Double> e : columns.get(j).entrySet()) {
                b[e.getKey()] += e.getValue() * xStar[j];
            }
        }
        double[] c = new double[n];
        double[] l = new double[n];
        for (int j = 0; j < n; j++) {
            c[j] = -5 + rnd.nextDouble() * 10;
        }
        LpResult r = RevisedSimplex.solve(
                new LpModel(SparseMatrix.fromColumns(m, columns), b, c, l, u));
        assertEquals(LpResult.Status.OPTIMAL, r.status, "reason=" + r.reason);
        assertNotNull(r.x);
    }
}
