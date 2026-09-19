package com.github.aeddddd.ae2enhanced.test.lp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpModel;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpModelDump;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpResult;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.RevisedSimplex;

/**
 * 失败模型离线回放:生产环境求解失败时模型被转储到 lp-dumps/,
 * 本测试逐字节回放同一份模型复现数值缺陷(无需游戏环境).
 * <p>除 OPTIMAL 状态外,按 Fuzz 同款口径校验解的约束残差 Ax≈b 与界内
 * (真实故障转储的残差校验价值高于合成模型).</p>
 * <p>用法:{code ./gradlew test --tests LpModelReplayTest -Dae2e.lpdump.dir=<目录>};
 * 未指定目录时跳过.</p>
 */
public class LpModelReplayTest {

    private static final double FEAS_TOL = 1e-7;
    private static final double BOUND_TOL = 1e-6;
    private static final double SCALE_TOL = 1e-12;

    @Test
    public void replayDumpedModels() throws Exception {
        String dirPath = System.getProperty("ae2e.lpdump.dir");
        org.junit.jupiter.api.Assumptions.assumeTrue(dirPath != null, "未指定 -Dae2e.lpdump.dir");
        File dir = new File(dirPath);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".lpm"));
        org.junit.jupiter.api.Assumptions.assumeTrue(files != null && files.length > 0,
                "目录无 .lpm 转储: " + dir);
        java.util.Arrays.sort(files);
        for (File file : files) {
            LpModel model = LpModelDump.read(file);
            LpResult result = RevisedSimplex.solve(model);
            System.out.println("[REPLAY] " + file.getName() + " → " + result.status
                    + (result.reason != null ? " (" + result.reason + ")" : "")
                    + " 迭代=" + result.iterations);
            assertThat(result.status).as("回放 %s", file.getName())
                    .isEqualTo(LpResult.Status.OPTIMAL);
            verifyFeasible(model, result.x, file.getName());
        }
    }

    /** 约束校验:Ax=b 且界内(与 Fuzz 测试/求解器出口同口径:绝对项 + max|x| 量级项). */
    private static void verifyFeasible(LpModel model, double[] x, String label) {
        int m = model.a.rows;
        double maxAbsX = 0;
        for (double v : x) {
            maxAbsX = Math.max(maxAbsX, Math.abs(v));
        }
        double scaleTol = SCALE_TOL * maxAbsX;
        double[] ax = new double[m];
        double[] act = new double[m];
        for (int j = 0; j < model.a.cols; j++) {
            assertThat(x[j]).as("%s 列=%d 下界", label, j)
                    .isGreaterThanOrEqualTo(model.lower[j] - BOUND_TOL * (1 + Math.abs(model.lower[j])) - scaleTol);
            assertThat(x[j]).as("%s 列=%d 上界", label, j)
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
                    .as("%s 行=%d 残差", label, i)
                    .isLessThanOrEqualTo(FEAS_TOL * rowScale);
        }
    }
}
