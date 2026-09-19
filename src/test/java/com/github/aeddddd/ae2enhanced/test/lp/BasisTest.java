package com.github.aeddddd.ae2enhanced.test.lp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

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
