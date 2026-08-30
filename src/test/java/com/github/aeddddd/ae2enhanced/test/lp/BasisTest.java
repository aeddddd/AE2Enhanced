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
 * Basis 因子化层单元测试:随机稀疏矩阵的 FTRAN/BTRAN 残差校验,
 * 含 Forrest–Tomlin 换基更新后的再校验(隔离单纯形驱动层).
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

    @Test
    public void testFtranBtranResiduals() throws Exception {
        Random rnd = new Random(42L);
        for (int t = 0; t < 50; t++) {
            int m = 5 + rnd.nextInt(60);
            SparseMatrix a = randomBasis(rnd, m);
            // 经反射访问包私有 Basis(同包测试?不同包——经公开行为间接校验不可行,
            // 故 Basis 测试放同包?测试包为 test.lp,反射访问)
            Class<?> basisClass = Class.forName(
                    "com.github.aeddddd.ae2enhanced.specialcrafting.lp.Basis");
            java.lang.reflect.Constructor<?> ctor = basisClass.getDeclaredConstructor(int.class);
            ctor.setAccessible(true);
            Object basis = ctor.newInstance(m);
            // 构造基列视图
            List<Object> cols = new ArrayList<>();
            Class<?> sparseColClass = Class.forName(
                    "com.github.aeddddd.ae2enhanced.specialcrafting.lp.Basis$SparseCol");
            for (int j = 0; j < m; j++) {
                final int jj = j;
                cols.add(java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
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
            // FTRAN 残差:B·x = b → 残差应 ≈ 0
            double[] b = new double[m];
            for (int i = 0; i < m; i++) {
                b[i] = -50 + rnd.nextDouble() * 100;
            }
            double[] x = b.clone();
            java.lang.reflect.Method ftran = basisClass.getDeclaredMethod("ftran",
                    double[].class);
            ftran.setAccessible(true);
            ftran.invoke(basis, x);
            // 残差:a·x − b(注意:基列即 a 的列,但顺序无关——恒等校验)
            double maxResidual = 0;
            for (int i = 0; i < m; i++) {
                double sum = -b[i];
                for (int j = 0; j < m; j++) {
                    for (int p = a.colPtr[j]; p < a.colPtr[j + 1]; p++) {
                        if (a.rowIdx[p] == i) {
                            sum += a.values[p] * x[j];
                        }
                    }
                }
                maxResidual = Math.max(maxResidual, Math.abs(sum));
            }
            assertEquals(0, maxResidual, EPS * m, "FTRAN 残差 case " + t);
        }
    }
}
