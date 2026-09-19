package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

/**
 * 有界变量两阶段修正单纯形, legacy LP 求解器, 无外部依赖.
 * 求解 {@code min c·x, A x = b, l ≤ x ≤ u}: Phase 1 最小化人工变量和以求可行基,
 * 和超容差判 {@link LpResult.Status#INFEASIBLE}, Phase 2 固定人工变量为 0 优化原目标.
 * 数值策略: 最陡边定价停滞时降级 Bland, 主元过小拒绝重定价, 比率测试用 Harris 松弛,
 * 基奇异时以人工单位列修补, 出口自检不过先做残差精化再复核.
 * 仅当 {@code ae2e.lpSolver=legacy} 时使用, 默认求解器为 DualSimplex.
 */
public final class RevisedSimplex {

    private static final double FEAS_TOL = 1e-7;
    private static final double OPT_TOL = 1e-9;
    private static final double ZERO_TOL = 1e-10;
    /** 出口界容差的绝对项, 相对被违例的界, 高于 Harris 可行性松弛量级一个数量级. */
    private static final double EXIT_BOUND_TOL = 1e-6;
    /** 出口界容差的量级项, 相对 max|xb|, 混合量级模型下 xb 漂移按此口径验收. */
    private static final double EXIT_SCALE_TOL = 1e-12;
    /** 迭代硬上限, 防御用; Bland 规则下理论有限终止. */
    private static final int MAX_ITER = 200_000;
    /** 连续零步长迭代阈值, 超限切换 Bland 规则直至出现非零步长. */
    private static final int STALL_LIMIT = 500;
    /** 最陡边候选集大小, 全量定价后按 |d_j| 取前 K 个再算精确最陡边. */
    private static final int EDGE_CANDIDATES = 32;
    /** 主元健康阈值的相对项, 相对 max|α|, 低于则拒绝该进入候选. */
    private static final double PIVOT_REL = 1e-6;
    /** 主元健康阈值的绝对项; eta 主元的误差放大率约为 1/p, 过小必须拒绝或重分解. */
    private static final double PIVOT_ABS = 1e-3;
    /** 换基后立即重分解的主元阈值, 相对项, 相对 max|α|. */
    private static final double IMMEDIATE_REFACTOR_REL = 1e-4;
    /** 换基后立即重分解的主元阈值, 绝对项, 介于健康阈与常规主元之间. */
    private static final double IMMEDIATE_REFACTOR_ABS = 1e-2;
    /** 单迭代进入候选拒绝预算, 超限接受非健康主元兜底. */
    private static final int MAX_REJECT = 8;

    private RevisedSimplex() {
    }

    /**
     * 求解 LP, 纯函数, 内部状态一次性, 线程安全.
     * <p>求解链: 先对模型做两轮 Ruiz 行列均衡, 因子取 2 的幂使双精度下无舍入,
     * 默认轨迹求解后反缩放并按原模型口径校验; 未通过则依次回退缩放 Bland 轨迹,
     * 未缩放默认轨迹, 未缩放 Bland 轨迹. 任何返回的 OPTIMAL 都通过原模型校验.</p>
     */
    /** 数值失败兜底重启次数, 微扰与列置换交替. */
    private static final int PERTURB_RESTARTS = Integer.getInteger("ae2e.perturbRestarts", 6);

    public static LpResult solve(LpModel model) {
        LpResult r = solveChainOnce(model);
        if (r.status != LpResult.Status.NUMERIC_FAILURE) {
            return r;
        }
        // 数值失败兜底: 交替施加 b 微扰与列置换, 打散退化顶点后换轨迹重试.
        // 微扰量级 1e-9 相对, 远小于出口容差 1e-7; 解须按原模型口径校验.
        for (int restart = 1; restart <= PERTURB_RESTARTS; restart++) {
            LpModel perturbed = (restart & 1) == 1 ? perturbB(model, restart) : permuteColumns(model, restart);
            LpResult pr = solveChainOnce(perturbed);
            if (pr.status != LpResult.Status.OPTIMAL) {
                continue;
            }
            // 校验解对原模型口径; 列置换后 pr.x 需先还原列序
            double[] originalX = pr.x;
            if ((restart & 1) == 0) {
                originalX = unpermuteX(model, pr.x, restart);
            }
            if (feasibleOnOriginal(model, originalX)) {
                return LpResult.optimal(originalX, computeObjective(model, originalX), pr.iterations);
            }
        }
        return r;
    }

    /** 确定性列置换, 按 restart 播种的 Fisher-Yates, 改变主元进入顺序以换轨迹. */
    private static int[] columnPermutation(int n, int restart) {
        int[] perm = new int[n];
        for (int j = 0; j < n; j++) {
            perm[j] = j;
        }
        java.util.Random rng = new java.util.Random(restart * 0x9E3779B97F4A7C15L);
        for (int j = n - 1; j > 0; j--) {
            int k = rng.nextInt(j + 1);
            int tmp = perm[j];
            perm[j] = perm[k];
            perm[k] = tmp;
        }
        return perm;
    }

    /** 列置换模型: 新模型第 j 列取原模型第 perm[j] 列. */
    private static LpModel permuteColumns(LpModel model, int restart) {
        int n = model.a.cols;
        int[] perm = columnPermutation(n, restart);
        List<Map<Integer, Double>> columns = new ArrayList<>(n);
        double[] cost = new double[n];
        double[] lower = new double[n];
        double[] upper = new double[n];
        for (int j = 0; j < n; j++) {
            int src = perm[j];
            Map<Integer, Double> col = new TreeMap<>();
            for (int p = model.a.colPtr[src]; p < model.a.colPtr[src + 1]; p++) {
                col.put(model.a.rowIdx[p], model.a.values[p]);
            }
            columns.add(col);
            cost[j] = model.cost[src];
            lower[j] = model.lower[src];
            upper[j] = model.upper[src];
        }
        return new LpModel(SparseMatrix.fromColumns(model.a.rows, columns), model.b, cost, lower, upper);
    }

    /** 列置换模型的解向量还原为原模型列序, 确定性重算同一置换. */
    private static double[] unpermuteX(LpModel original, double[] px, int restart) {
        int[] perm = columnPermutation(original.a.cols, restart);
        double[] x = new double[original.a.cols];
        for (int j = 0; j < px.length; j++) {
            x[perm[j]] = px[j];
        }
        return x;
    }

    /** 按原模型目标系数重算目标值. */
    private static double computeObjective(LpModel model, double[] x) {
        double obj = 0;
        for (int j = 0; j < model.a.cols; j++) {
            obj += model.cost[j] * x[j];
        }
        return obj;
    }

    /** 单趟求解链: 缩放默认轨迹, 缩放 Bland 轨迹, 未缩放双轨迹. */
    private static LpResult solveChainOnce(LpModel model) {
        ScaledModel sm = scale(model);
        LpResult r = solveInternal(sm.model, false);
        if (r.status == LpResult.Status.OPTIMAL) {
            LpResult v = unscaleAndVerify(r, sm, model);
            if (v != null) {
                return v;
            }
        } else if (r.status == LpResult.Status.NUMERIC_FAILURE) {
            LpResult r2 = solveInternal(sm.model, true);
            if (r2.status == LpResult.Status.OPTIMAL) {
                LpResult v = unscaleAndVerify(r2, sm, model);
                if (v != null) {
                    return v;
                }
            }
        } else {
            return r; // INFEASIBLE/ITERATION_LIMIT: 缩放不改变可行性本质, 原样返回
        }
        // 缩放路径未收敛或反缩放校验未过: 未缩放原模型兜底, 两档轨迹
        LpResult orig = solveInternal(model, false);
        if (orig.status == LpResult.Status.OPTIMAL
                || orig.status != LpResult.Status.NUMERIC_FAILURE) {
            return orig;
        }
        LpResult origBland = solveInternal(model, true);
        return origBland.status == LpResult.Status.OPTIMAL ? origBland : orig;
    }

    /** b 的确定性微扰, 按 restart 播种; 非零行按 1e-9 相对扰动, 零行不动. */
    private static LpModel perturbB(LpModel model, int restart) {
        double[] b = model.b.clone();
        for (int i = 0; i < b.length; i++) {
            if (b[i] == 0.0) {
                continue;
            }
            // 确定性伪随机, 由行号与 restart 哈希播种, 幅度 ±1e-9×|b_i|
            long h = (long) (i + 1) * 0x9E3779B97F4A7C15L * restart;
            h ^= h >>> 33;
            h *= 0xFF51AFD7ED558CCDL;
            h ^= h >>> 33;
            double noise = ((double) (h >>> 11) / (double) (1L << 53)) * 2.0 - 1.0;
            b[i] = b[i] * (1.0 + 1e-9 * noise) + Math.signum(b[i]) * 1e-9;
        }
        return new LpModel(model.a, b, model.cost, model.lower, model.upper);
    }

    /** 微扰解在原模型口径下的界与残差复核, 与 {@link #unscaleAndVerify} 同容差. */
    private static boolean feasibleOnOriginal(LpModel src, double[] x) {
        int n = src.a.cols;
        int m = src.a.rows;
        double scaleTol = EXIT_SCALE_TOL * maxAbs(x);
        for (int j = 0; j < n; j++) {
            double lb = src.lower[j];
            double ub = src.upper[j];
            if (lb > -LpModel.INF / 2
                    && x[j] < lb - EXIT_BOUND_TOL * (1 + Math.abs(lb)) - scaleTol) {
                return false;
            }
            if (ub < LpModel.INF / 2
                    && x[j] > ub + EXIT_BOUND_TOL * (1 + Math.abs(ub)) + scaleTol) {
                return false;
            }
        }
        double[] residual = new double[m];
        double[] activity = new double[m];
        for (int j = 0; j < n; j++) {
            if (x[j] == 0) {
                continue;
            }
            for (int p = src.a.colPtr[j]; p < src.a.colPtr[j + 1]; p++) {
                double term = src.a.values[p] * x[j];
                residual[src.a.rowIdx[p]] += term;
                activity[src.a.rowIdx[p]] += Math.abs(term);
            }
        }
        for (int i = 0; i < m; i++) {
            double res = residual[i] - src.b[i];
            if (Math.abs(res) > FEAS_TOL * (1 + Math.abs(src.b[i]) + activity[i])) {
                return false;
            }
        }
        return true;
    }

    /** 缩放后的模型与反缩放信息: x = C·x', C = diag(colScale). */
    private static final class ScaledModel {
        final LpModel model;
        final double[] colScale;

        ScaledModel(LpModel model, double[] colScale) {
            this.model = model;
            this.colScale = colScale;
        }
    }

    private static final double LN2 = 0.6931471805599453;

    /**
     * 两轮 Ruiz 行列均衡, 即 R·A·C, 行与列的 max|系数| 轮流压到约 1.
     * 因子一律取 2 的幂, 2 的幂乘法在双精度下无舍入, 缩放与反缩放全程精确;
     * 无限界原样保留不缩放.
     */
    private static ScaledModel scale(LpModel src) {
        SparseMatrix a = src.a;
        int m = a.rows;
        int n = a.cols;
        double[] values = a.values.clone();
        double[] rowScale = new double[m];
        double[] colScale = new double[n];
        Arrays.fill(rowScale, 1);
        Arrays.fill(colScale, 1);
        for (int round = 0; round < 2; round++) {
            // 行均衡: 每行 max|a_ij| 压到约 1, 因子取 2 的幂
            double[] rowMax = new double[m];
            for (int p = 0; p < values.length; p++) {
                rowMax[a.rowIdx[p]] = Math.max(rowMax[a.rowIdx[p]], Math.abs(values[p]));
            }
            double[] rowF = new double[m];
            for (int i = 0; i < m; i++) {
                rowF[i] = pow2Factor(rowMax[i]);
                rowScale[i] *= rowF[i];
            }
            for (int p = 0; p < values.length; p++) {
                if (rowF[a.rowIdx[p]] != 1) {
                    values[p] *= rowF[a.rowIdx[p]];
                }
            }
            // 列均衡: 每列 max|a_ij| 压到约 1, 因子取 2 的幂
            for (int j = 0; j < n; j++) {
                double colMax = 0;
                for (int p = a.colPtr[j]; p < a.colPtr[j + 1]; p++) {
                    colMax = Math.max(colMax, Math.abs(values[p]));
                }
                double f = pow2Factor(colMax);
                if (f != 1) {
                    colScale[j] *= f;
                    for (int p = a.colPtr[j]; p < a.colPtr[j + 1]; p++) {
                        values[p] *= f;
                    }
                }
            }
        }
        // b' = R·b, c' = C·c, l' = l/C, u' = u/C, 由 x = C·x' 得出; 无限界原样保留
        double[] b = new double[m];
        for (int i = 0; i < m; i++) {
            b[i] = src.b[i] * rowScale[i];
        }
        double[] cost = new double[n];
        double[] lower = new double[n];
        double[] upper = new double[n];
        for (int j = 0; j < n; j++) {
            cost[j] = src.cost[j] * colScale[j];
            double lo = src.lower[j];
            double up = src.upper[j];
            lower[j] = lo <= -LpModel.INF / 2 ? -LpModel.INF : lo / colScale[j];
            upper[j] = up >= LpModel.INF / 2 ? LpModel.INF : up / colScale[j];
        }
        return new ScaledModel(
                new LpModel(SparseMatrix.of(m, n, a.colPtr, a.rowIdx, values), b, cost,
                        lower, upper),
                colScale);
    }

    /** 使 max 缩放到约 1 的 2 的幂因子, 0 或空行空列不缩放. */
    private static double pow2Factor(double max) {
        if (!(max > 0)) {
            return 1;
        }
        int e = (int) Math.round(Math.log(max) / LN2);
        if (e == 0) {
            return 1;
        }
        return Math.pow(2.0, -e);
    }

    /**
     * 反缩放, x = C·x', 并用原模型口径校验界与残差, 容差与出口自检相同.
     * 校验通过返回 OPTIMAL 结果, 目标值按原模型重算; 不通过返回 null, 由调用方回退未缩放路径.
     */
    @Nullable
    private static LpResult unscaleAndVerify(LpResult r, ScaledModel sm, LpModel src) {
        int n = src.a.cols;
        int m = src.a.rows;
        double[] x = new double[n];
        for (int j = 0; j < n; j++) {
            x[j] = r.x[j] * sm.colScale[j];
        }
        double scaleTol = EXIT_SCALE_TOL * maxAbs(x);
        for (int j = 0; j < n; j++) {
            double lb = src.lower[j];
            double ub = src.upper[j];
            if (lb > -LpModel.INF / 2
                    && x[j] < lb - EXIT_BOUND_TOL * (1 + Math.abs(lb)) - scaleTol) {
                return null;
            }
            if (ub < LpModel.INF / 2
                    && x[j] > ub + EXIT_BOUND_TOL * (1 + Math.abs(ub)) + scaleTol) {
                return null;
            }
        }
        double[] residual = new double[m];
        double[] activity = new double[m];
        for (int j = 0; j < n; j++) {
            if (x[j] == 0) {
                continue;
            }
            for (int p = src.a.colPtr[j]; p < src.a.colPtr[j + 1]; p++) {
                double term = src.a.values[p] * x[j];
                residual[src.a.rowIdx[p]] += term;
                activity[src.a.rowIdx[p]] += Math.abs(term);
            }
        }
        for (int i = 0; i < m; i++) {
            double res = residual[i] - src.b[i];
            if (Math.abs(res) > FEAS_TOL * (1 + Math.abs(src.b[i]) + activity[i])) {
                return null;
            }
        }
        double obj = 0;
        for (int j = 0; j < n; j++) {
            obj += src.cost[j] * x[j];
        }
        return LpResult.optimal(x, obj, r.iterations);
    }

    /**
     * 求解主体.
     *
     * @param forceBland true 表示全程 Bland 规则, 反循环保证, 速度慢但轨迹稳健,
     *                   供 NUMERIC_FAILURE 后的重试使用
     */
    private static LpResult solveInternal(LpModel model, boolean forceBland) {
        int m = model.a.rows;
        int n = model.a.cols;
        if (m == 0) {
            // 无约束: 变量取目标最优侧界
            double[] x = new double[n];
            double obj = 0;
            for (int j = 0; j < n; j++) {
                x[j] = model.cost[j] >= 0 ? model.lower[j] : model.upper[j];
                obj += model.cost[j] * x[j];
            }
            return LpResult.optimal(x, obj, 0);
        }
        // 变量布局: 0..n-1 为结构列, n..n+m-1 为人工列 e_i
        int total = n + m;
        double[] lower = Arrays.copyOf(model.lower, total);
        double[] upper = Arrays.copyOf(model.upper, total);
        double[] costPhase1 = new double[total];
        double[] costPhase2 = Arrays.copyOf(model.cost, total);
        // b 符号规整: 不改行, 人工列取 sign(b_i)·e_i, 使初基解为 |b| 可行
        double[] b = model.b.clone();
        double[] artSign = new double[m];
        for (int i = 0; i < m; i++) {
            artSign[i] = b[i] < 0 ? -1 : 1;
            lower[n + i] = 0;
            upper[n + i] = LpModel.INF;
            costPhase1[n + i] = 1;
        }

        // 基状态
        int[] basic = new int[m]; // 位置 → 变量
        int[] where = new int[total]; // 变量 → 位置, -1 表示非基
        Arrays.fill(where, -1);
        for (int i = 0; i < m; i++) {
            basic[i] = n + i;
            where[n + i] = i;
        }
        double[] x = new double[total];
        for (int j = 0; j < n; j++) {
            // 初值: 有限下界取下界, 否则有限上界取上界, 双侧无限取 0
            if (lower[j] > -LpModel.INF / 2) {
                x[j] = lower[j];
            } else if (upper[j] < LpModel.INF / 2) {
                x[j] = upper[j];
            } else {
                x[j] = 0;
            }
        }
        Basis basis = new Basis(m);
        boolean phase1 = true;
        int iterations = 0;
        int stall = 0;
        boolean bland = forceBland;
        /** 基修补预算, 单位列替换病态基列的次数上限. 修补只能救偶发单列病态,
         * 基列集整体近相关时应快速失败, 交给上层换轨迹重试. */
        int[] repairBudget = { Integer.getInteger("ae2e.repairBudget", 8) };
        /** 可行性恢复预算, 对偶单纯形步数上限, 防恢复死循环. */
        int[] restoreBudget = { Integer.getInteger("ae2e.restoreBudget", 4 * m) };
        /** 诊断: phase1 出口人工变量残留和, 失败时写入 reason 供日志定位. */
        double phase1Residue = Double.NaN;
        /** 诊断: phase1 到 phase2 钳制后基变量最大越界量. */
        double transitionMaxViol = 0;

        try {
            refactor(basis, model, artSign, basic, where, x, lower, upper, n, m, repairBudget);
            while (true) {
                // 原始解: x_B = B⁻¹(b − A_N·x_N), 必须扣除非基变量坐在非零界上的
                // 列贡献, 否则界翻转后 RHS 即错
                double[] xb = computeBasicRhs(model, artSign, where, x, b, n, total, m);
                basis.ftran(xb);
                for (int i = 0; i < m; i++) {
                    x[basic[i]] = xb[i];
                }
                if (!phase1 && !Double.isNaN(phase1Residue) && transitionMaxViol == 0) {
                    // 相变后首个顶点: 量测钳制导致的基变量最大越界, 诊断用
                    for (int i = 0; i < m; i++) {
                        double lo = lower[basic[i]];
                        double up = upper[basic[i]];
                        if (lo > -LpModel.INF / 2 && xb[i] < lo) {
                            transitionMaxViol = Math.max(transitionMaxViol, lo - xb[i]);
                        }
                        if (up < LpModel.INF / 2 && xb[i] > up) {
                            transitionMaxViol = Math.max(transitionMaxViol, xb[i] - up);
                        }
                    }
                }
                // 对偶: y = c_B·B⁻¹
                double[] y = new double[m];
                for (int i = 0; i < m; i++) {
                    y[i] = (phase1 ? costPhase1 : costPhase2)[basic[i]];
                }
                basis.btran(y);
                // 进入变量选择分定价, 候选比率测试与主元健康检查: 主元过小的候选被
                // 拒绝并重定价, 因为小主元换基会放大 eta 链误差; 预算耗尽, Bland
                // 模式或全集重定价时接受非健康主元兜底, 兜底换基后立即重分解
                double[] cost = phase1 ? costPhase1 : costPhase2;
                int entering = -1;
                int enterDir = 0; // +1 从下界升, -1 从上界降
                double[] alpha = null;
                double theta = 0;
                double thetaMaxA = 0;
                double maxAbsAlpha = 0;
                int leavingPos = -1;
                boolean leaveAtLower = false;
                boolean pivotUnhealthy = false;
                boolean[] rejected = null;
                int rejectedCount = 0;
                boolean forced = false;
                while (true) {
                    // 定价: 非基变量约简成本, 跳过已拒绝的候选
                    int cand = -1;
                    int candDir = 0;
                    if (!bland) {
                        // 候选集精确最陡边: 先按 |d_j| 取前 K 个
                        int[] candSet = new int[EDGE_CANDIDATES];
                        int[] candSetDir = new int[EDGE_CANDIDATES];
                        double[] candAbs = new double[EDGE_CANDIDATES];
                        int candCount = 0;
                        for (int j = 0; j < total; j++) {
                            if (where[j] >= 0 || j >= n || (rejected != null && rejected[j])) {
                                continue; // 人工变量离基后禁止再进基,
                                // 其上界为 INF, 再进基会使 RHS 爆炸
                            }
                            double dj = reducedCost(model, artSign, cost, y, j, n);
                            int dir = directionOf(dj, x[j], lower[j], upper[j]);
                            if (dir == 0) {
                                continue;
                            }
                            double abs = dir > 0 ? -dj : dj;
                            if (candCount < EDGE_CANDIDATES) {
                                candSet[candCount] = j;
                                candSetDir[candCount] = dir;
                                candAbs[candCount] = abs;
                                candCount++;
                            } else {
                                int minAt = 0;
                                for (int q = 1; q < EDGE_CANDIDATES; q++) {
                                    if (candAbs[q] < candAbs[minAt]) {
                                        minAt = q;
                                    }
                                }
                                if (abs > candAbs[minAt]) {
                                    candSet[minAt] = j;
                                    candSetDir[minAt] = dir;
                                    candAbs[minAt] = abs;
                                }
                            }
                        }
                        // 候选集上计算精确最陡边
                        double bestEdge = 0;
                        for (int q = 0; q < candCount; q++) {
                            double[] edgeAlpha = columnOf(model, artSign, candSet[q], n);
                            basis.ftran(edgeAlpha);
                            double norm = 0;
                            for (double v : edgeAlpha) {
                                norm += v * v;
                            }
                            double edge = candAbs[q] / Math.sqrt(norm + 1e-30);
                            if (edge > bestEdge) {
                                bestEdge = edge;
                                cand = candSet[q];
                                candDir = candSetDir[q];
                            }
                        }
                    } else {
                        // Bland 规则: 最小指标的可进变量, 人工变量同样禁止再进基
                        for (int j = 0; j < total; j++) {
                            if (where[j] >= 0 || j >= n || (rejected != null && rejected[j])) {
                                continue;
                            }
                            double dj = reducedCost(model, artSign, cost, y, j, n);
                            int dir = directionOf(dj, x[j], lower[j], upper[j]);
                            if (dir != 0) {
                                cand = j;
                                candDir = dir;
                                break;
                            }
                        }
                    }
                    if (cand < 0) {
                        if (rejectedCount > 0) {
                            // 剩余候选均被拒绝过: 清空拒绝集强制重定价,
                            // 本次接受任意主元, 保证最优性判定不漏候选
                            rejected = null;
                            rejectedCount = 0;
                            forced = true;
                            continue;
                        }
                        break; // entering = -1, 最优性达成
                    }
                    // 候选进入列 α = B⁻¹A_j
                    double[] candAlpha = columnOf(model, artSign, cand, n);
                    basis.ftran(candAlpha);
                    double candMaxAbs = 0;
                    for (double v : candAlpha) {
                        candMaxAbs = Math.max(candMaxAbs, Math.abs(v));
                    }
                    // Harris 阶段一: 按松弛余量求步长. slack 按界标定, 不随 xb 放大;
                    // 已越界行余量地板为 0, 零比率阻挡促使该行被逐出钳回, 步长恒非负.
                    // 微观 α 行仅在最大步长下位移仍不可感知时才跳过, 否则 theta×α 的
                    // 隐形位移会把 xb 推出界外
                    double candThetaMax = upper[cand] - lower[cand];
                    double[] roomArr = new double[m];
                    double[] moveArr = new double[m];
                    double candTheta = candThetaMax;
                    for (int i = 0; i < m; i++) {
                        double a = candAlpha[i];
                        if (a == 0) {
                            continue;
                        }
                        double move = candDir * a; // 大于 0 时 x_B_i 降, 小于 0 时升
                        double room;
                        double boundAbs;
                        if (move > 0) {
                            double bound = lower[basic[i]];
                            if (bound <= -LpModel.INF / 2) {
                                continue;
                            }
                            room = xb[i] - bound;
                            boundAbs = Math.abs(bound);
                        } else {
                            double bound = upper[basic[i]];
                            if (bound >= LpModel.INF / 2) {
                                continue;
                            }
                            room = bound - xb[i];
                            boundAbs = Math.abs(bound);
                        }
                        if (Math.abs(a) < ZERO_TOL
                                && Math.abs(a) * candThetaMax < FEAS_TOL * (1 + boundAbs)) {
                            continue; // 微观 α, 位移不可感知, 跳过安全
                        }
                        roomArr[i] = room;
                        moveArr[i] = move;
                        // 可行性松弛按界标定; 已越界行余量地板为 0, 形成零比率阻挡,
                        // 下一步即被逐出钳回界上
                        double slack = FEAS_TOL * (1 + boundAbs);
                        double roomEff = room > 0 ? room : 0;
                        double relaxed = (roomEff + slack) / Math.abs(move);
                        if (relaxed < candTheta) {
                            candTheta = relaxed;
                        }
                    }
                    if (candTheta != candTheta) {
                        return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                "比率测试步长 NaN(基或 RHS 受污染)");
                    }
                    // 阶段二: 在精确比率不超过松弛步长的行中, 优先取满足主元健康阈
                    // 的 |α| 最大者; 无健康行时取 |α| 最大者并标记非健康, 交由拒绝与兜底决策
                    int candLeaving = -1;
                    boolean candLeaveAtLower = false;
                    boolean candHealthy = true;
                    if (candTheta < candThetaMax) {
                        double pivotFloor = Math.max(PIVOT_REL * candMaxAbs, PIVOT_ABS);
                        int healthyPos = -1;
                        double healthyAbs = 0;
                        int anyPos = -1;
                        double anyAbs = 0;
                        for (int i = 0; i < m; i++) {
                            double move = moveArr[i];
                            if (move == 0) {
                                continue;
                            }
                            double exact = roomArr[i] <= 0 ? 0 : roomArr[i] / Math.abs(move);
                            if (exact > candTheta) {
                                continue;
                            }
                            double absA = Math.abs(candAlpha[i]);
                            if (absA > anyAbs) {
                                anyAbs = absA;
                                anyPos = i;
                            }
                            if (absA >= pivotFloor && absA > healthyAbs) {
                                healthyAbs = absA;
                                healthyPos = i;
                            }
                        }
                        if (anyPos < 0) {
                            // 理论不可达, 松弛最小行的精确比率必不超过 theta, 防御性显式失败
                            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                    "比率测试无换基候选 theta=" + candTheta);
                        }
                        candHealthy = healthyPos >= 0;
                        candLeaving = candHealthy ? healthyPos : anyPos;
                        candLeaveAtLower = moveArr[candLeaving] > 0;
                    }
                    if (candHealthy || forced || bland || rejectedCount >= MAX_REJECT) {
                        entering = cand;
                        enterDir = candDir;
                        alpha = candAlpha;
                        theta = candTheta;
                        maxAbsAlpha = candMaxAbs;
                        leavingPos = candLeaving;
                        leaveAtLower = candLeaveAtLower;
                        break;
                    }
                    // 主元非健康: 拒绝该候选, 重定价取次优
                    if (rejected == null) {
                        rejected = new boolean[total];
                    }
                    rejected[cand] = true;
                    rejectedCount++;
                }
                if (entering < 0) {
                    // 最优性达成
                    if (phase1) {
                        double infeasibility = 0;
                        for (int i = 0; i < m; i++) {
                            if (basic[i] >= n) {
                                infeasibility += Math.abs(xb[i]);
                            }
                        }
                        double scale = 1 + maxAbs(b);
                        if (infeasibility > FEAS_TOL * scale) {
                            return LpResult.failure(LpResult.Status.INFEASIBLE, iterations,
                                    "phase1 人工变量和=" + infeasibility);
                        }
                        // 进入 Phase 2: 人工变量固定为 0, 基内残值在容差内钳到 0
                        phase1 = false;
                        phase1Residue = infeasibility;
                        for (int i = 0; i < m; i++) {
                            upper[n + i] = 0;
                            if (basic[i] >= n) {
                                x[basic[i]] = 0;
                                xb[i] = 0;
                            }
                        }
                        continue;
                    }
                    // 出口抛光: 重分解清掉 eta 链尾段漂移, 全量重算 xb. 可行性恢复
                    // 判定与自检必须基于新鲜值, 否则漂移造成的假越界会触发无效恢复
                    refactor(basis, model, artSign, basic, where, x, lower, upper, n, m,
                            repairBudget);
                    double[] xbPolished = computeBasicRhs(model, artSign, where, x, b, n, total, m);
                    basis.ftran(xbPolished);
                    for (int i = 0; i < m; i++) {
                        x[basic[i]] = xbPolished[i];
                    }
                    // pricing 最优不保证原始可行: Harris 松弛, 基修补与小主元漂移都会
                    // 留下越界基变量. 先查原始可行性, 越界超阈时做一步有界对偶单纯形,
                    // 用对偶比率测试选进列以保持对偶可行, 把最差越界基变量推回界内;
                    // 预算耗尽或无合格进列才判 NUMERIC_FAILURE
                    int worstPos = -1;
                    double worstViol = 0;
                    boolean worstAtLower = false;
                    double restoreScaleTol = EXIT_SCALE_TOL * maxAbs(xbPolished);
                    for (int i = 0; i < m; i++) {
                        double lb = lower[basic[i]];
                        double ub = upper[basic[i]];
                        // 触发口径与出口自检一致, 即绝对项加量级项: 容差内的微越界
                        // 属双精度正常噪声, 不进入恢复, 否则恢复会在噪声粒度上空转
                        if (lb > -LpModel.INF / 2) {
                            double v = lb - xbPolished[i];
                            if (v > EXIT_BOUND_TOL * (1 + Math.abs(lb)) + restoreScaleTol
                                    && v > worstViol) {
                                worstViol = v;
                                worstPos = i;
                                worstAtLower = true;
                            }
                        }
                        if (ub < LpModel.INF / 2) {
                            double v = xbPolished[i] - ub;
                            if (v > EXIT_BOUND_TOL * (1 + Math.abs(ub)) + restoreScaleTol
                                    && v > worstViol) {
                                worstViol = v;
                                worstPos = i;
                                worstAtLower = false;
                            }
                        }
                    }
                    if (worstPos >= 0) {
                        if (restoreBudget[0] <= 0) {
                            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                    "可行性恢复预算耗尽(最差越界=" + worstViol + " 行=" + worstPos
                                            + ")");
                        }
                        restoreBudget[0]--;
                        // 对偶在抛光基上重算, eta 链尾段漂移的 y 会误导对偶比率
                        for (int i = 0; i < m; i++) {
                            y[i] = cost[basic[i]];
                        }
                        basis.btran(y);
                        // 对偶比率测试: r = B⁻ᵀe_p 即 tableau 行, 在保持对偶可行且能
                        // 把 xb[p] 推向界内的非基列中选进列, 取最小的 |d_j/α_j|
                        double[] r = new double[m];
                        r[worstPos] = 1;
                        basis.btran(r);
                        int enterJ = -1;
                        int enterDirR = 0;
                        double bestRatio = LpModel.INF;
                        for (int j = 0; j < n; j++) {
                            if (where[j] >= 0) {
                                continue;
                            }
                            double aj = model.a.dotColumn(j, r);
                            int dirJ;
                            // xb[p] 对 x_j 的偏导是 −α_j, 来自 x_B = B⁻¹(b − A_N·x_N):
                            // xb[p] 需增大时, j 升且 α_j<0, 或 j 降且 α_j>0
                            if (worstAtLower) {
                                if (x[j] < upper[j] - ZERO_TOL && aj < -ZERO_TOL) {
                                    dirJ = 1;
                                } else if (x[j] > lower[j] + ZERO_TOL && aj > ZERO_TOL) {
                                    dirJ = -1;
                                } else {
                                    continue;
                                }
                            } else {
                                if (x[j] < upper[j] - ZERO_TOL && aj > ZERO_TOL) {
                                    dirJ = 1;
                                } else if (x[j] > lower[j] + ZERO_TOL && aj < -ZERO_TOL) {
                                    dirJ = -1;
                                } else {
                                    continue;
                                }
                            }
                            double dj = reducedCost(model, artSign, cost, y, j, n);
                            double ratio = Math.abs(dj / aj);
                            if (ratio < bestRatio) {
                                bestRatio = ratio;
                                enterJ = j;
                                enterDirR = dirJ;
                            }
                        }
                        if (enterJ < 0) {
                            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                    "可行性恢复无合格进列(最差越界=" + worstViol + " 行=" + worstPos
                                            + ",原始不可行嫌疑)");
                        }
                        double[] alphaR = columnOf(model, artSign, enterJ, n);
                        basis.ftran(alphaR);
                        double alphaP = alphaR[worstPos];
                        if (Math.abs(alphaP) < ZERO_TOL) {
                            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                    "可行性恢复主元退化(行=" + worstPos + " 列=" + enterJ + ")");
                        }
                        int oldVar = basic[worstPos];
                        double need = worstAtLower ? lower[oldVar] - xbPolished[worstPos]
                                : xbPolished[worstPos] - upper[oldVar];
                        double thetaR = need / Math.abs(alphaP);
                        if (upper[enterJ] - lower[enterJ] <= thetaR) {
                            // 进列先撞对侧界: 界翻转, xb[p] 部分恢复, 下轮继续
                            x[enterJ] = enterDirR > 0 ? upper[enterJ] : lower[enterJ];
                        } else {
                            // 换基: 进列替换 worstPos, 旧基变量坐到被违例的界上
                            x[enterJ] = enterDirR > 0 ? lower[enterJ] + thetaR
                                    : upper[enterJ] - thetaR;
                            x[oldVar] = worstAtLower ? lower[oldVar] : upper[oldVar];
                            int alphaNnz = 0;
                            for (double v : alphaR) {
                                if (v != 0) {
                                    alphaNnz++;
                                }
                            }
                            int[] aIdx = new int[alphaNnz];
                            double[] aVal = new double[alphaNnz];
                            int ap = 0;
                            double maxAbsR = 0;
                            for (int i = 0; i < m; i++) {
                                if (alphaR[i] != 0) {
                                    aIdx[ap] = i;
                                    aVal[ap] = alphaR[i];
                                    ap++;
                                }
                                maxAbsR = Math.max(maxAbsR, Math.abs(alphaR[i]));
                            }
                            basis.update(worstPos, aIdx, aVal);
                            where[oldVar] = -1;
                            basic[worstPos] = enterJ;
                            where[enterJ] = worstPos;
                            // 小主元立即重分解, 与主循环同口径
                            if (Math.abs(alphaP) < Math.max(IMMEDIATE_REFACTOR_REL * maxAbsR,
                                    IMMEDIATE_REFACTOR_ABS)
                                    || basis.etaCount() > Basis.MAX_ETA) {
                                refactor(basis, model, artSign, basic, where, x, lower, upper,
                                        n, m, repairBudget);
                            }
                        }
                        iterations++;
                        continue;
                    }
                    // 出口自检基于上方抛光后的新鲜 xb; 未过先做一轮残差精化再复核,
                    // 精化后仍超标才是真实失败
                    String violation = checkPrimalFeasible(model, basic, x, xbPolished, b,
                            lower, upper, n, m);
                    if (violation != null) {
                        refineBasic(model, artSign, basic, x, xbPolished, b, basis, n, m);
                        violation = checkPrimalFeasible(model, basic, x, xbPolished, b,
                                lower, upper, n, m);
                    }
                    if (violation != null) {
                        return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                "出口可行性自检未过: " + violation + " [phase1残留="
                                        + phase1Residue + " 相变跳变=" + transitionMaxViol
                                        + " 修补=" + (8 - repairBudget[0]) + "]");
                    }
                    double obj = 0;
                    double[] result = new double[n];
                    for (int j = 0; j < n; j++) {
                        result[j] = x[j];
                        obj += model.cost[j] * x[j];
                    }
                    return LpResult.optimal(result, obj, iterations);
                }
                if (iterations >= MAX_ITER) {
                    return LpResult.failure(LpResult.Status.ITERATION_LIMIT, iterations,
                            "迭代数超硬上限 " + MAX_ITER);
                }
                if (leavingPos < 0 && theta >= LpModel.INF / 2) {
                    // 无界方向: 本架构模型全部变量有界, 仅人工变量残留等异常可达
                    return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                            "无界方向(进入变量无阻挡且界宽无限)");
                }
                if (leavingPos < 0) {
                    // 进入变量撞对侧界: 界翻转, 不换基
                    x[entering] = enterDir > 0 ? upper[entering] : lower[entering];
                    stall = theta == 0 ? stall + 1 : 0;
                } else {
                    int leaving = basic[leavingPos];
                    x[entering] = enterDir > 0 ? lower[entering] + theta : upper[entering] - theta;
                    x[leaving] = leaveAtLower ? lower[leaving] : upper[leaving];
                    // 换基: 进入列替换 leavingPos 位置
                    int alphaNnz = 0;
                    for (double v : alpha) {
                        if (v != 0) {
                            alphaNnz++;
                        }
                    }
                    int[] aIdx = new int[alphaNnz];
                    double[] aVal = new double[alphaNnz];
                    int p = 0;
                    for (int i = 0; i < m; i++) {
                        if (alpha[i] != 0) {
                            aIdx[p] = i;
                            aVal[p] = alpha[i];
                            p++;
                        }
                    }
                    basis.update(leavingPos, aIdx, aVal);
                    where[leaving] = -1;
                    basic[leavingPos] = entering;
                    where[entering] = leavingPos;
                    stall = theta == 0 ? stall + 1 : 0;
                    // 重分解时机: eta 链累积超阈, 或本轮主元偏小, 因为小主元 eta 会
                    // 放大后续全部 ftran 误差, 立即重分解把影响限制在当轮
                    if (basis.etaCount() > Basis.MAX_ETA
                            || Math.abs(alpha[leavingPos]) < Math.max(
                                    IMMEDIATE_REFACTOR_REL * maxAbsAlpha,
                                    IMMEDIATE_REFACTOR_ABS)) {
                        refactor(basis, model, artSign, basic, where, x, lower, upper, n, m,
                                repairBudget);
                    }
                }
                // 停滞控制: 触发与解除 Bland. forceBland 重试必须锁定 Bland, 否则
                // 首轮非退化迭代即掉回最陡边, 重试轨迹与首轮相同
                if (!forceBland) {
                    if (!bland && stall > STALL_LIMIT) {
                        bland = true;
                    } else if (bland && stall == 0) {
                        bland = false;
                    }
                }
                iterations++;
            }
        } catch (Basis.NumericException e) {
            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                    "基分解数值失败: " + e.getMessage());
        }
    }

    /**
     * 重分解当前基, 结构列经 CSC 视图, 人工列经 ±e_i 视图.
     * 分解失败即基奇异或病态时做单位列修补: 失败位置的旧基变量退基坐到最近界上,
     * 该位置换入对应行的人工单位列, 顶点跳变由后续迭代恢复. 修补次数受预算限制,
     * 耗尽或无定位信息时抛出.
     */
    private static void refactor(Basis basis, LpModel model, double[] artSign, int[] basic,
            int[] where, double[] x, double[] lower, double[] upper, int n, int m,
            int[] repairBudget) throws Basis.NumericException {
        while (true) {
            List<Basis.SparseCol> columns = new ArrayList<>(m);
            for (int i = 0; i < m; i++) {
                int var = basic[i];
                if (var < n) {
                    columns.add(new CscCol(model.a, var));
                } else {
                    columns.add(new UnitCol(var - n, artSign[var - n]));
                }
            }
            try {
                basis.factorize(columns);
                return;
            } catch (Basis.NumericException e) {
                if (e.failPos < 0 || repairBudget[0] <= 0) {
                    throw new Basis.NumericException(
                            e.getMessage() + " [放弃修补:" + (repairBudget[0] <= 0 ? "预算耗尽" : "无定位信息") + "]");
                }
                // 候选人工单位列的选择: 只有剩余位置映射行的空闲人工列才有有效主元,
                // 其余行的单位列必被已消元位置吞没
                int row = -1;
                if (e.candRows != null) {
                    for (int r : e.candRows) {
                        if (where[n + r] < 0) {
                            row = r;
                            break;
                        }
                    }
                }
                if (row < 0 && e.bestRow >= 0 && where[n + e.bestRow] < 0) {
                    row = e.bestRow;
                }
                if (row < 0) {
                    throw new Basis.NumericException(e.getMessage() + " [放弃修补:无空闲人工列]");
                }
                if (Boolean.getBoolean("ae2e.basisDump")) {
                    StringBuilder sb = new StringBuilder("[BASIS-DUMP] failPos=" + e.failPos
                            + " bestRow=" + e.bestRow + " chosenRow=" + row + " basic=");
                    for (int i = 0; i < m; i++) {
                        sb.append(basic[i] < n ? basic[i] : ("art#" + (basic[i] - n)));
                        if (i + 1 < m) {
                            sb.append(',');
                        }
                    }
                    System.out.println(sb);
                }
                repairBudget[0]--;
                int pos = e.failPos;
                int oldVar = basic[pos];
                // 旧基变量退基: 坐到最近界上, 非基变量必须坐界
                where[oldVar] = -1;
                x[oldVar] = Math.abs(x[oldVar] - lower[oldVar]) <= Math
                        .abs(x[oldVar] - upper[oldVar]) ? lower[oldVar] : upper[oldVar];
                // 该位置换入人工单位列 e_row
                basic[pos] = n + row;
                where[n + row] = pos;
            }
        }
    }

    /**
     * 全量重算基右端, rhs = b − A_N·x_N.
     * 非基变量坐在非零界上的列贡献必须扣除, 否则界翻转后 RHS 即错.
     */
    private static double[] computeBasicRhs(LpModel model, double[] artSign, int[] where,
            double[] x, double[] b, int n, int total, int m) {
        double[] rhs = b.clone();
        for (int j = 0; j < total; j++) {
            if (where[j] >= 0 || x[j] == 0) {
                continue;
            }
            if (j < n) {
                for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                    rhs[model.a.rowIdx[p]] -= model.a.values[p] * x[j];
                }
            } else {
                rhs[j - n] -= artSign[j - n] * x[j];
            }
        }
        return rhs;
    }

    /**
     * 残差迭代精化: r = b − A·x 含人工列, δ = B⁻¹r 修正基变量取值.
     * 一轮精化可将 xb 的分解残差压到 ε² 量级, 用于出口自检前的复核.
     */
    private static void refineBasic(LpModel model, double[] artSign, int[] basic, double[] x,
            double[] xb, double[] b, Basis basis, int n, int m) {
        double[] r = b.clone();
        for (int j = 0; j < n; j++) {
            if (x[j] == 0) {
                continue;
            }
            for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                r[model.a.rowIdx[p]] -= model.a.values[p] * x[j];
            }
        }
        for (int i = 0; i < m; i++) {
            r[i] -= artSign[i] * x[n + i];
        }
        basis.ftran(r);
        for (int i = 0; i < m; i++) {
            xb[i] += r[i];
            x[basic[i]] = xb[i];
        }
    }

    /** 约简成本 d_j = c_j − y·A_j, 人工列为 c_j − y_i·sign_i. */
    private static double reducedCost(LpModel model, double[] artSign, double[] cost, double[] y,
            int j, int n) {
        if (j < n) {
            return cost[j] - model.a.dotColumn(j, y);
        }
        return cost[j] - y[j - n] * artSign[j - n];
    }

    /** 可进方向判定: +1 从下界升, d_j<0; -1 从上界降, d_j>0; 0 不可进. */
    private static int directionOf(double dj, double xj, double lj, double uj) {
        if (dj < -OPT_TOL && xj < uj - ZERO_TOL) {
            return 1;
        }
        if (dj > OPT_TOL && xj > lj + ZERO_TOL) {
            return -1;
        }
        return 0;
    }

    /** 变量 j 的约束列, 矩阵行语义稠密向量. */
    private static double[] columnOf(LpModel model, double[] artSign, int j, int n) {
        double[] col = new double[model.a.rows];
        if (j < n) {
            model.a.expandColumn(j, col);
        } else {
            col[j - n] = artSign[j - n];
        }
        return col;
    }

    /**
     * 出口可行性自检: 逐约束残差加界违例.
     * 残差容差 {@value #FEAS_TOL} 相对行活动量级, 为硬约束; 界容差
     * {@value #EXIT_BOUND_TOL} 相对被违例的界, 覆盖 Harris 松弛留量与 xb 漂移.
     * 返回 null 表示通过, 否则返回最差违例明细, 供诊断日志.
     */
    @Nullable
    private static String checkPrimalFeasible(LpModel model, int[] basic,
            double[] x, double[] xb, double[] b, double[] lower, double[] upper, int n, int m) {
        // 量级项: 混合量级模型 xb 分量的绝对漂移下限约为 ε·κ·max|xb|, 按此验收
        double scaleTol = EXIT_SCALE_TOL * maxAbs(xb);
        for (int i = 0; i < m; i++) {
            double lb = lower[basic[i]];
            double ub = upper[basic[i]];
            if (lb > -LpModel.INF / 2) {
                double tol = EXIT_BOUND_TOL * (1 + Math.abs(lb)) + scaleTol;
                if (xb[i] < lb - tol) {
                    return "界违例(下) 行=" + i + " 基变量=" + basic[i] + " xb=" + xb[i] + " lb="
                            + lb + " 超出=" + (lb - xb[i]) + " 容差=" + tol;
                }
            }
            if (ub < LpModel.INF / 2) {
                double tol = EXIT_BOUND_TOL * (1 + Math.abs(ub)) + scaleTol;
                if (xb[i] > ub + tol) {
                    return "界违例(上) 行=" + i + " 基变量=" + basic[i] + " xb=" + xb[i] + " ub="
                            + ub + " 超出=" + (xb[i] - ub) + " 容差=" + tol;
                }
            }
        }
        // 残差: Ax + Σ sign_i·x_art·e_i = b; 行尺度 = 1 + |b_i| + Σ|a_ij·x_j|
        double[] residual = new double[m];
        double[] activity = new double[m];
        for (int j = 0; j < n; j++) {
            if (x[j] != 0) {
                for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                    double term = model.a.values[p] * x[j];
                    residual[model.a.rowIdx[p]] += term;
                    activity[model.a.rowIdx[p]] += Math.abs(term);
                }
            }
        }
        int worstRow = -1;
        double worstOver = 0;
        double worstResidual = 0;
        double worstTol = 0;
        for (int i = 0; i < m; i++) {
            // 残差 = A·x_struct − b, 故意不含人工列: 基修补残留的人工变量若取值非零,
            // 直接表现为该行残差违例, 防止单位列修补掩盖真实不可行
            residual[i] -= b[i];
            double rowScale = 1 + Math.abs(b[i]) + activity[i];
            double over = Math.abs(residual[i]) - FEAS_TOL * rowScale;
            if (over > worstOver) {
                worstOver = over;
                worstRow = i;
                worstResidual = residual[i];
                worstTol = FEAS_TOL * rowScale;
            }
        }
        if (worstOver > 0) {
            return "残差违例 行=" + worstRow + " 残差=" + worstResidual + " b=" + b[worstRow]
                    + " 活动量=" + activity[worstRow] + " 容差=" + worstTol;
        }
        return null;
    }

    private static double maxAbs(double[] v) {
        double max = 0;
        for (double value : v) {
            max = Math.max(max, Math.abs(value));
        }
        return max;
    }

    /** CSC 结构列的稀疏视图. */
    private static final class CscCol implements Basis.SparseCol {
        private final SparseMatrix a;
        private final int j;

        CscCol(SparseMatrix a, int j) {
            this.a = a;
            this.j = j;
        }

        @Override
        public int nnz() {
            return this.a.nnzOf(this.j);
        }

        @Override
        public int indexAt(int k) {
            return this.a.rowIdx[this.a.colPtr[this.j] + k];
        }

        @Override
        public double valueAt(int k) {
            return this.a.values[this.a.colPtr[this.j] + k];
        }
    }

    /** 人工变量列 ±e_i 的稀疏视图. */
    private static final class UnitCol implements Basis.SparseCol {
        private final int row;
        private final double sign;

        UnitCol(int row, double sign) {
            this.row = row;
            this.sign = sign;
        }

        @Override
        public int nnz() {
            return 1;
        }

        @Override
        public int indexAt(int k) {
            return this.row;
        }

        @Override
        public double valueAt(int k) {
            return this.sign;
        }
    }
}
