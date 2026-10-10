package com.github.aeddddd.ae2enhanced.test.lp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpModel;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SparseMatrix;

/**
 * Basis 因子化层单元测试(隔离单纯形驱动层,经反射访问包私有 Basis).
 * <p>覆盖:随机稀疏基 factorize 后的 FTRAN 残差(现有)、Forrest–Tomlin
 * 换基更新(update)后的 FTRAN/BTRAN 残差——update 路径此前零覆盖.</p>
 */
public class BasisTest {

    private static final double EPS = 1e-7;

    /** 随机非奇异稀疏矩阵(对角占优保证非奇异). */
    private static SparseMatrix randomBasis(Random rnd, int m) {
        List<Map<Integer, Double>> columns = new ArrayList<>();
        for (int j = 0; j < m; j++) {
            Map<Integer, Double> col = new TreeMap<>();
            for (int i = 0; i < m; i++) {
                if (i != j && rnd.nextDouble() < 0.08) {
                    col.put(i, (double) (1 + rnd.nextInt(4)));
                }
            }
            col.put(j, (double) (10 + rnd.nextInt(10))); // 对角占优
            columns.add(col);
        }
        return SparseMatrix.fromColumns(m, columns);
    }

    /** 反射构造并 factorize Basis(列视图经 Proxy 适配内部 SparseCol 接口). */
    private static Object factorizeBasis(int m, SparseMatrix a) throws Exception {
        Class<?> basisClass = Class.forName(
                "com.github.aeddddd.ae2enhanced.specialcrafting.lp.Basis");
        java.lang.reflect.Constructor<?> ctor = basisClass.getDeclaredConstructor(int.class);
        ctor.setAccessible(true);
        Object basis = ctor.newInstance(m);
        List<Object> cols = new ArrayList<>();
        Class<?> sparseColClass = Class.forName(
                "com.github.aeddddd.ae2enhanced.specialcrafting.lp.Basis$SparseCol");
        for (int j = 0; j < m; j++) {
            final int jj = j;
            cols.add(java.lang.reflect.Proxy.newProxyInstance(BasisTest.class.getClassLoader(),
                    new Class<?>[] { sparseColClass },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "nnz":
                                return a.nnzOf(jj);
                            case "indexAt":
                                return a.rowIdx[a.colPtr[jj] + (Integer) args[0]];
                            case "valueAt":
                                return a.values[a.colPtr[jj] + (Integer) args[0]];
                            default:
                                throw new UnsupportedOperationException();
                        }
                    }));
        }
        java.lang.reflect.Method factorize = basisClass.getDeclaredMethod("factorize",
                List.class);
        factorize.setAccessible(true);
        factorize.invoke(basis, cols);
        return basis;
    }

    private static void invokeVec(String method, Object basis, double[] x) throws Exception {
        java.lang.reflect.Method m = basis.getClass().getDeclaredMethod(method, double[].class);
        m.setAccessible(true);
        m.invoke(basis, x);
    }

    /** 残差 max|A·x − b|(A 以三元组遍历). */
    private static double residual(SparseMatrix a, double[] x, double[] b) {
        double maxResidual = 0;
        for (int i = 0; i < b.length; i++) {
            double sum = -b[i];
            for (int j = 0; j < b.length; j++) {
                for (int p = a.colPtr[j]; p < a.colPtr[j + 1]; p++) {
                    if (a.rowIdx[p] == i) {
                        sum += a.values[p] * x[j];
                    }
                }
            }
            maxResidual = Math.max(maxResidual, Math.abs(sum));
        }
        return maxResidual;
    }

    /** 随机稀疏列(非对角元 8% 密度). */
    private static Map<Integer, Double> randomColumn(Random rnd, int m) {
        Map<Integer, Double> col = new TreeMap<>();
        for (int i = 0; i < m; i++) {
            if (rnd.nextDouble() < 0.08) {
                col.put(i, (double) (1 + rnd.nextInt(4)));
            }
        }
        if (col.isEmpty()) {
            col.put(rnd.nextInt(m), (double) (1 + rnd.nextInt(4))); // 至少一个非零元
        }
        return col;
    }

    @Test
    public void testFtranResiduals() throws Exception {
        Random rnd = new Random(42L);
        for (int t = 0; t < 50; t++) {
            int m = 5 + rnd.nextInt(60);
            SparseMatrix a = randomBasis(rnd, m);
            Object basis = factorizeBasis(m, a);
            double[] b = new double[m];
            for (int i = 0; i < m; i++) {
                b[i] = -50 + rnd.nextDouble() * 100;
            }
            double[] x = b.clone();
            invokeVec("ftran", basis, x);
            assertEquals(0, residual(a, x, b), EPS * m, "FTRAN 残差 case " + t);
        }
    }

    /**
     * FTRAN/BTRAN 不得改变因子化状态(可重复调用) — P-A 热启动复用旧基的前提:
     * 同一个 {@code Basis} 在不同右端上反复使用, 每次都必须给出与物化基一致的结果.
     */
    @Test
    public void testFactorIsReusableAcrossRightHandSides() throws Exception {
        Random rnd = new Random(20261011L);
        for (int t = 0; t < 30; t++) {
            int m = 5 + rnd.nextInt(60);
            SparseMatrix a = randomBasis(rnd, m);
            Object basis = factorizeBasis(m, a);
            double[] b1 = new double[m];
            double[] b2 = new double[m];
            for (int i = 0; i < m; i++) {
                b1[i] = -50 + rnd.nextDouble() * 100;
                b2[i] = -50 + rnd.nextDouble() * 100;
            }
            double[] x1 = b1.clone();
            double[] x1again = b1.clone();
            invokeVec("ftran", basis, x1);
            invokeVec("ftran", basis, x1again);
            for (int i = 0; i < m; i++) {
                assertEquals(Double.doubleToLongBits(x1[i]), Double.doubleToLongBits(x1again[i]),
                        "同一 Basis 重复 FTRAN 结果不逐位一致 case " + t);
            }
            double[] x2 = b2.clone();
            invokeVec("ftran", basis, x2);
            assertEquals(0, residual(a, x1, b1), EPS * m, "复用后第一次 FTRAN 残差 case " + t);
            assertEquals(0, residual(a, x2, b2), EPS * m, "复用后第二次 FTRAN 残差 case " + t);
            // BTRAN 同样可重复且不破坏后续 FTRAN
            double[] y = new double[m];
            double[] yAgain = new double[m];
            for (int i = 0; i < m; i++) {
                y[i] = -50 + rnd.nextDouble() * 100;
                yAgain[i] = y[i];
            }
            invokeVec("btran", basis, y);
            invokeVec("btran", basis, yAgain);
            for (int i = 0; i < m; i++) {
                assertEquals(Double.doubleToLongBits(y[i]), Double.doubleToLongBits(yAgain[i]),
                        "同一 Basis 重复 BTRAN 结果不逐位一致 case " + t);
            }
            double[] x3 = b1.clone();
            invokeVec("ftran", basis, x3);
            assertEquals(0, residual(a, x3, b1), EPS * m, "BTRAN 之后 FTRAN 残差 case " + t);
        }
    }

    /**
     * P-C 前提: 对逐位相同的系数矩阵, 记录的逐轮 Ruiz 因子必须能"重放"出与首次计算
     * 逐位相同的缩放结果(a/b/cost/lower/upper 全部逐位比对) — 热启动与冷启动因此
     * 在同一缩放空间里求解, 等价性才可能成立.
     */
    @Test
    public void testScalingPlanReplayIsBitIdentical() throws Exception {
        Random rnd = new Random(31337L);
        Class<?> dualClass = Class.forName(
                "com.github.aeddddd.ae2enhanced.specialcrafting.lp.DualSimplex");
        Class<?> planClass = Class.forName(
                "com.github.aeddddd.ae2enhanced.specialcrafting.lp.DualSimplex$ScalingPlan");
        java.lang.reflect.Method compute = dualClass.getDeclaredMethod("computeScaling",
                SparseMatrix.class, double[].class, double[].class, double[].class, double[].class,
                long.class, boolean.class);
        compute.setAccessible(true);
        java.lang.reflect.Method apply = dualClass.getDeclaredMethod("applyScaling", planClass,
                SparseMatrix.class, double[].class, double[].class, double[].class, double[].class);
        apply.setAccessible(true);
        for (int t = 0; t < 20; t++) {
            int m = 4 + rnd.nextInt(30);
            SparseMatrix a = randomBasis(rnd, m);
            int n = a.cols;
            double[] b = new double[m];
            double[] cost = new double[n];
            double[] lower = new double[n];
            double[] upper = new double[n];
            for (int i = 0; i < m; i++) {
                b[i] = -20 + rnd.nextDouble() * 40; // 含 0 与负值, 覆盖符号规整
            }
            for (int j = 0; j < n; j++) {
                cost[j] = rnd.nextDouble() < 0.3 ? 1 : 0;
                lower[j] = 0;
                upper[j] = rnd.nextDouble() < 0.3 ? 5 + rnd.nextInt(20) : LpModel.INF;
            }
            SparseMatrix a1 = copy(a);
            double[] b1 = b.clone();
            double[] c1 = cost.clone();
            double[] lo1 = lower.clone();
            double[] up1 = upper.clone();
            Object plan = compute.invoke(null, a1, b1, c1, lo1, up1, 0L, Boolean.TRUE);

            SparseMatrix a2 = copy(a);
            double[] b2 = b.clone();
            double[] c2 = cost.clone();
            double[] lo2 = lower.clone();
            double[] up2 = upper.clone();
            apply.invoke(null, plan, a2, b2, c2, lo2, up2);

            for (int p = 0; p < a1.values.length; p++) {
                assertEquals(Double.doubleToLongBits(a1.values[p]),
                        Double.doubleToLongBits(a2.values[p]), "重放后系数不逐位一致 case " + t);
            }
            for (int i = 0; i < m; i++) {
                assertEquals(Double.doubleToLongBits(b1[i]), Double.doubleToLongBits(b2[i]),
                        "重放后右端不逐位一致 case " + t);
            }
            for (int j = 0; j < n; j++) {
                assertEquals(Double.doubleToLongBits(c1[j]), Double.doubleToLongBits(c2[j]),
                        "重放后成本不逐位一致 case " + t);
                assertEquals(Double.doubleToLongBits(lo1[j]), Double.doubleToLongBits(lo2[j]),
                        "重放后下界不逐位一致 case " + t);
                assertEquals(Double.doubleToLongBits(up1[j]), Double.doubleToLongBits(up2[j]),
                        "重放后上界不逐位一致 case " + t);
            }
        }
    }

    private static SparseMatrix copy(SparseMatrix a) {
        return SparseMatrix.of(a.rows, a.cols, a.colPtr.clone(), a.rowIdx.clone(), a.values.clone());
    }

    /**
     * 换基更新(Forrest–Tomlin)后的 FTRAN/BTRAN 残差:
     * 初始基 B₀ 随机,进入列 a_q 随机;α = B₀⁻¹a_q 经 update 入账后,
     * 新基 B₁(B₀ 的第 p 列换成 a_q)的 FTRAN/BTRAN 残差必须 ≈ 0.
     */
    @Test
    public void testUpdateThenFtranBtranResiduals() throws Exception {
        Random rnd = new Random(7L);
        for (int t = 0; t < 50; t++) {
            int m = 5 + rnd.nextInt(60);
            SparseMatrix b0 = randomBasis(rnd, m);
            // 进入列(B₁ 中替换主元位的列)
            Map<Integer, Double> qCol = randomColumn(rnd, m);

            Object basis = factorizeBasis(m, b0);
            // α = B₀⁻¹·a_q(FTRAN 于进入列的密集展开,就地求解)
            double[] alpha = new double[m];
            qCol.forEach((row, v) -> alpha[row] = v);
            invokeVec("ftran", basis, alpha);
            // 主元位 = |α| 最大元(对齐真实比率测试主元,保证 eta 主元非零)
            int pivotPos = 0;
            for (int i = 1; i < m; i++) {
                if (Math.abs(alpha[i]) > Math.abs(alpha[pivotPos])) {
                    pivotPos = i;
                }
            }
            if (alpha[pivotPos] == 0) {
                continue; // α 全零(进入列零向量)——本 case 无换基意义
            }
            // 稀疏化 α(update 的 eta 表示)
            int nnz = 0;
            for (double v : alpha) {
                if (v != 0) {
                    nnz++;
                }
            }
            int[] alphaIdx = new int[nnz];
            double[] alphaVal = new double[nnz];
            int at = 0;
            for (int i = 0; i < m; i++) {
                if (alpha[i] != 0) {
                    alphaIdx[at] = i;
                    alphaVal[at] = alpha[i];
                    at++;
                }
            }
            java.lang.reflect.Method update = basis.getClass().getDeclaredMethod("update",
                    int.class, int[].class, double[].class);
            update.setAccessible(true);
            update.invoke(basis, pivotPos, alphaIdx, alphaVal);

            // 物化新基 B₁(B₀ 第 pivotPos 列替换为 a_q)
            List<Map<Integer, Double>> cols1 = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                Map<Integer, Double> col = new TreeMap<>();
                if (j == pivotPos) {
                    col.putAll(qCol);
                } else {
                    for (int p = b0.colPtr[j]; p < b0.colPtr[j + 1]; p++) {
                        col.put(b0.rowIdx[p], b0.values[p]);
                    }
                }
                cols1.add(col);
            }
            SparseMatrix b1 = SparseMatrix.fromColumns(m, cols1);

            double[] b = new double[m];
            for (int i = 0; i < m; i++) {
                b[i] = -50 + rnd.nextDouble() * 100;
            }
            double[] x = b.clone();
            invokeVec("ftran", basis, x);
            assertEquals(0, residual(b1, x, b), EPS * m, "换基后 FTRAN 残差 case " + t);

            double[] c = new double[m];
            for (int i = 0; i < m; i++) {
                c[i] = -50 + rnd.nextDouble() * 100;
            }
            double[] y = c.clone();
            invokeVec("btran", basis, y);
            // BTRAN 约定(见 Basis.btran/RevisedSimplex 定价调用):输入为基序语义
            // (c_B[i] = 基位 i 处基本变量的目标系数),输出散回矩阵行语义,
            // 满足 y·(B₁ 第 j 列) = c_B[j].逐列校验残差.
            double maxResidual = 0;
            for (int j = 0; j < m; j++) {
                double sum = -c[j];
                for (int p = b1.colPtr[j]; p < b1.colPtr[j + 1]; p++) {
                    sum += b1.values[p] * y[b1.rowIdx[p]];
                }
                maxResidual = Math.max(maxResidual, Math.abs(sum));
            }
            assertEquals(0, maxResidual, EPS * m, "换基后 BTRAN 残差 case " + t);
        }
    }
}
