package com.github.aeddddd.ae2enhanced.test.lp;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 修正单纯形结构模糊测试(构造已知可行点的模型做预言机):
 * <ul>
 *   <li>自引用/环状行(A→2A、A⇄B 互转、1:1000 比率展布),仿 material_part 家族单元;</li>
 *   <li>大 RHS(1e5~1e7 量级,仿大单);</li>
 *   <li>先造可行点 x* 再算 b = A·x* —— 模型按构造可行,
 *       求解器必须返回 OPTIMAL 且解满足约束(绝对不允许 NUMERIC_FAILURE);</li>
 *   <li>断言返回解与 x* 残差在各行活动量级容差内(等价顶点亦须满足约束).</li>
 * </ul>
 */
public class RevisedSimplexFuzzTest {

    /** 每种子的用例数(两种子共 5000,覆盖不同病例群体,约 15s). */
    private static final int CASES_PER_SEED = 2500;
    private static final long[] SEEDS = { 20260831L, 20260903L };
    private static final double FEAS_TOL = 1e-7;
    /** 界容差·绝对项(与求解器出口口径 EXIT_BOUND_TOL 一致;残差才是硬约束). */
    private static final double BOUND_TOL = 1e-6;
    /** 界容差·量级项(与求解器出口口径 EXIT_SCALE_TOL 一致,相对 max|x|). */
    private static final double SCALE_TOL = 1e-12;

    @Test
    public void feasibleByConstructionNeverFails() {
        int failures = 0;
        for (long seed : SEEDS) {
            Random master = new Random(seed);
            for (int caseId = 0; caseId < CASES_PER_SEED; caseId++) {
                Random rnd = new Random(master.nextLong());
                LpModel model = buildCase(rnd);
                LpResult result = RevisedSimplex.solve(model);
                if (result.status != LpResult.Status.OPTIMAL) {
                    failures++;
                    System.out.println("[FUZZ] seed=" + seed + " case=" + caseId + " 非最优: "
                            + result.status + " " + result.reason + " m=" + model.a.rows + " n="
                            + model.a.cols);
                    continue;
                }
                verifySolution(model, result.x, seed, caseId);
            }
        }
        assertThat(failures).as("非最优 case 数").isEqualTo(0);
    }

    /** 约束校验:Ax=b 且界内(与出口自检同口径:绝对项 + max|x| 量级项). */
    private static void verifySolution(LpModel model, double[] x, long seed, int caseId) {
        {
            int m = model.a.rows;
            double maxAbsX = 0;
            for (double v : x) {
                maxAbsX = Math.max(maxAbsX, Math.abs(v));
            }
            double scaleTol = SCALE_TOL * maxAbsX;
            double[] ax = new double[m];
            double[] act = new double[m];
            for (int j = 0; j < model.a.cols; j++) {
                assertThat(x[j]).as("seed=%d case=%d 列=%d 下界", seed, caseId, j)
                        .isGreaterThanOrEqualTo(model.lower[j] - BOUND_TOL * (1 + Math.abs(model.lower[j])) - scaleTol);
                assertThat(x[j]).as("seed=%d case=%d 列=%d 上界", seed, caseId, j)
                        .isLessThanOrEqualTo(model.upper[j] + BOUND_TOL * (1 + Math.abs(model.upper[j])) + scaleTol);
                if (x[j] == 0) {
                    continue;
                }
                for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                    double term = model.a.values[p] * x[j];
                    ax[model.a.rowIdx[p]] += term;
                    act[model.a.rowIdx[p]] += Math.abs(term);
                }
            }
            for (int i = 0; i < m; i++) {
                double rowScale = 1 + Math.abs(model.b[i]) + act[i];
                assertThat(Math.abs(ax[i] - model.b[i]))
                        .as("seed=%d case=%d 行=%d 残差", seed, caseId, i)
                        .isLessThanOrEqualTo(FEAS_TOL * rowScale);
            }
        }
    }

    /**
     * 构造一个按构造可行的病态模型:
     * 行 = 物品守恒行(产出正、消耗负),含自增殖(A→kA)与互转环;
     * 列 = 配方执行变量(界 0..1e9)+ 每行一个赤字松弛变量(0..INF,
     * 吸收任意短缺,保证可行——与生产模型同构).
     * b 由随机可行点 x* 生成.
     */
    private static LpModel buildCase(Random rnd) {
        int keys = 20 + rnd.nextInt(110); // 20~130 键,覆盖 126 键单元量级
        int recipes = keys + rnd.nextInt(keys); // 列数约为键数 1~2 倍
        List<Map<Integer, Double>> columns = new ArrayList<>();
        List<double[]> bounds = new ArrayList<>();
        List<Double> costs = new ArrayList<>();

        for (int r = 0; r < recipes; r++) {
            Map<Integer, Double> col = new TreeMap<>();
            int kind = rnd.nextInt(5);
            int out = rnd.nextInt(keys);
            double ratio = ratioOf(rnd); // 1~1000 的产出/消耗比展布
            switch (kind) {
                case 0: // 普通:多输入单输出
                    col.put(out, ratio);
                    int inputs = 1 + rnd.nextInt(4);
                    for (int k = 0; k < inputs; k++) {
                        int in = rnd.nextInt(keys);
                        if (in != out) {
                            col.merge(in, -1.0 - rnd.nextInt(16), Double::sum);
                        }
                    }
                    break;
                case 1: // 自增殖:out → (1+ratio)·out
                    col.put(out, ratio);
                    break;
                case 2: // 互转:A→ratio·B(环由多次采样自然形成)
                    int other = rnd.nextInt(keys);
                    if (other == out) {
                        other = (other + 1) % keys;
                    }
                    col.put(out, -1.0);
                    col.put(other, ratio);
                    break;
                case 3: // 分解:out → 多种(1:1000 量级差)
                    col.put(out, -1.0);
                    int outs = 1 + rnd.nextInt(3);
                    for (int k = 0; k < outs; k++) {
                        int o2 = rnd.nextInt(keys);
                        if (o2 != out) {
                            col.merge(o2, ratio / (1 + rnd.nextInt(10)), Double::sum);
                        }
                    }
                    break;
                default: // 自耗增益:in·out → out(消耗=产出×小数,净增)
                    col.put(out, ratio - Math.floor(ratio) > 0 ? ratio : ratio + 0.5);
                    col.merge(out, -Math.floor(ratio), Double::sum);
                    break;
            }
            columns.add(col);
            bounds.add(new double[] { 0, 1e9 });
            costs.add(1.0 + rnd.nextDouble() * 1e-3);
        }
        // 每行赤字松弛(可行吸收器,与生产模型赤字列同构)
        for (int i = 0; i < keys; i++) {
            Map<Integer, Double> col = new TreeMap<>();
            col.put(i, 1.0);
            columns.add(col);
            bounds.add(new double[] { 0, LpModel.INF });
            costs.add(1e4); // 高成本:能生产就不用赤字
        }

        int n = columns.size();
        // 随机可行点 x*(界内),b = A·x*(大 RHS 量级)
        double[] xStar = new double[n];
        for (int j = 0; j < n; j++) {
            double ub = Math.min(bounds.get(j)[1], 1e7);
            xStar[j] = rnd.nextDouble() < 0.7 ? 0 : rnd.nextDouble() * ub;
        }
        double[] b = new double[keys];
        for (int j = 0; j < n; j++) {
            if (xStar[j] == 0) {
                continue;
            }
            for (Map.Entry<Integer, Double> e : columns.get(j).entrySet()) {
                b[e.getKey()] += e.getValue() * xStar[j];
            }
        }
        double[] cost = new double[n];
        double[] lower = new double[n];
        double[] upper = new double[n];
        for (int j = 0; j < n; j++) {
            cost[j] = costs.get(j);
            lower[j] = bounds.get(j)[0];
            upper[j] = bounds.get(j)[1];
        }
        return new LpModel(SparseMatrix.fromColumns(keys, columns), b, cost, lower, upper);
    }

    private static double ratioOf(Random rnd) {
        // 对数均匀 1~1000(仿 1水晶→1000流体 的比率展布)
        return Math.pow(10.0, rnd.nextDouble() * 3.0);
    }
}
