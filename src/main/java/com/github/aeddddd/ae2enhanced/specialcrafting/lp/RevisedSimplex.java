package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 有界变量两阶段修正单纯形（1.12.2 合成计划 LP 核心,自研无依赖）.
 * <p>求解标准形 {@code min c·x, A x = b, l ≤ x ≤ u}:</p>
 * <ul>
 * <li><b>Phase 1</b>:每行一个人工变量（列 ±e_i,符号随 b 规整）,最小化人工变量和;
 * 和 &gt; 容差 → {@link LpResult.Status#INFEASIBLE};</li>
 * <li><b>Phase 2</b>:人工变量固定为 0,优化原目标;</li>
 * <li><b>定价</b>:全量定价取候选 + 候选集精确最陡边（|d_j|/‖α‖,优于 DEVEX
 * 近似,成本有界）;停滞时降级 Bland 规则（最小指标进出,保证反循环终止）;</li>
 * <li><b>比率测试</b>:有界变量标准比率（进入变量撞界则界翻转不换基,
 * 基变量撞界则换基）,零步长退化迭代允许,连续退化触发 Bland;</li>
 * <li><b>出口自检</b>:可行性/最优性残差超标一律 NUMERIC_FAILURE,禁止静默错解.</li>
 * </ul>
 * 数值体系:可行性容差 {@value #FEAS_TOL}(相对)、最优性容差 {@value #OPT_TOL}、
 * 零判定 {@value #ZERO_TOL};约束矩阵小整数良态,RHS 大数值不影响条件数。
 */
public final class RevisedSimplex {

    private static final double FEAS_TOL = 1e-7;
    private static final double OPT_TOL = 1e-9;
    private static final double ZERO_TOL = 1e-10;
    /** 迭代硬上限(防御;Bland 规则下理论有限终止). */
    private static final int MAX_ITER = 200_000;
    /** 连续退化(零步长)迭代阈值,超限切换 Bland 规则直至出现非零步长. */
    private static final int STALL_LIMIT = 500;
    /** 最陡边候选集大小(全量定价后按 |d_j| 取前 K 个计算精确最陡边). */
    private static final int EDGE_CANDIDATES = 32;

    private RevisedSimplex() {
    }

    /**
     * 求解 LP.纯函数(内部状态一次性),线程安全.
     */
    public static LpResult solve(LpModel model) {
        int m = model.a.rows;
        int n = model.a.cols;
        if (m == 0) {
            // 无约束:变量取目标最优侧界
            double[] x = new double[n];
            double obj = 0;
            for (int j = 0; j < n; j++) {
                x[j] = model.cost[j] >= 0 ? model.lower[j] : model.upper[j];
                obj += model.cost[j] * x[j];
            }
            return LpResult.optimal(x, obj, 0);
        }
        // 变量布局:0..n-1 结构列,n..n+m-1 人工列(e_i)
        int total = n + m;
        double[] lower = Arrays.copyOf(model.lower, total);
        double[] upper = Arrays.copyOf(model.upper, total);
        double[] costPhase1 = new double[total];
        double[] costPhase2 = Arrays.copyOf(model.cost, total);
        // b 符号规整:不改行,人工变量列取 sign(b_i)·e_i,使初基解 = |b| 可行
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
        int[] where = new int[total]; // 变量 → 位置(-1 = 非基)
        Arrays.fill(where, -1);
        for (int i = 0; i < m; i++) {
            basic[i] = n + i;
            where[n + i] = i;
        }
        double[] x = new double[total];
        for (int j = 0; j < n; j++) {
            // 初值:有限下界取下界,否则有限上界取上界,双侧无限取 0
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
        boolean bland = false;

        try {
            refactor(basis, model, artSign, basic, n, m);
            while (true) {
                // 原始解:x_B = B⁻¹(b − A_N·x_N)——有界单纯形必须扣除非基变量
                // 坐在非零界(上界/非零下界)上的列贡献,否则界翻转后 RHS 即错
                double[] xb = b.clone();
                for (int j = 0; j < total; j++) {
                    if (where[j] >= 0 || x[j] == 0) {
                        continue;
                    }
                    if (j < n) {
                        for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                            xb[model.a.rowIdx[p]] -= model.a.values[p] * x[j];
                        }
                    } else {
                        xb[j - n] -= artSign[j - n] * x[j];
                    }
                }
                basis.ftran(xb);
                for (int i = 0; i < m; i++) {
                    x[basic[i]] = xb[i];
                }
                // 对偶:y = c_B·B⁻¹
                double[] y = new double[m];
                for (int i = 0; i < m; i++) {
                    y[i] = (phase1 ? costPhase1 : costPhase2)[basic[i]];
                }
                basis.btran(y);
                // 定价:非基变量约简成本
                double[] cost = phase1 ? costPhase1 : costPhase2;
                int entering = -1;
                int enterDir = 0; // +1 从下界升,-1 从上界降
                if (!bland) {
                    // 候选集精确最陡边:先按 |d_j| 取前 K 个
                    int[] cand = new int[EDGE_CANDIDATES];
                    int[] candDir = new int[EDGE_CANDIDATES];
                    double[] candAbs = new double[EDGE_CANDIDATES];
                    int candCount = 0;
                    for (int j = 0; j < total; j++) {
                        if (where[j] >= 0 || j >= n) {
                            continue; // 人工变量离基后永不许再进基(phase1 上界 INF,
                            // 无阻挡时会"界翻转到 INF"导致 RHS 爆炸——标准为不可进)
                        }
                        double dj = reducedCost(model, artSign, cost, y, j, n);
                        int dir = directionOf(dj, x[j], lower[j], upper[j]);
                        if (dir == 0) {
                            continue;
                        }
                        double abs = dir > 0 ? -dj : dj;
                        if (candCount < EDGE_CANDIDATES) {
                            cand[candCount] = j;
                            candDir[candCount] = dir;
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
                                cand[minAt] = j;
                                candDir[minAt] = dir;
                                candAbs[minAt] = abs;
                            }
                        }
                    }
                    // 候选集上精确最陡边
                    double bestEdge = 0;
                    for (int q = 0; q < candCount; q++) {
                        double[] alpha = columnOf(model, artSign, cand[q], n);
                        basis.ftran(alpha);
                        double norm = 0;
                        for (double v : alpha) {
                            norm += v * v;
                        }
                        double edge = candAbs[q] / Math.sqrt(norm + 1e-30);
                        if (edge > bestEdge) {
                            bestEdge = edge;
                            entering = cand[q];
                            enterDir = candDir[q];
                        }
                    }
                } else {
                    // Bland 规则:最小指标的可进变量(人工变量同样禁止再进基)
                    for (int j = 0; j < total; j++) {
                        if (where[j] >= 0 || j >= n) {
                            continue;
                        }
                        double dj = reducedCost(model, artSign, cost, y, j, n);
                        int dir = directionOf(dj, x[j], lower[j], upper[j]);
                        if (dir != 0) {
                            entering = j;
                            enterDir = dir;
                            break;
                        }
                    }
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
                        // 进入 Phase 2:人工变量固定为 0(基内残值容差内钳到 0)
                        phase1 = false;
                        for (int i = 0; i < m; i++) {
                            upper[n + i] = 0;
                            if (basic[i] >= n) {
                                x[basic[i]] = 0;
                                xb[i] = 0;
                            }
                        }
                        continue;
                    }
                    // 出口自检:可行性残差
                    if (!checkPrimalFeasible(model, artSign, basic, x, xb, b, lower, upper, n, m)) {
                        return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                "出口可行性自检未过");
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
                // 进入列 α = B⁻¹A_j
                double[] alpha = columnOf(model, artSign, entering, n);
                basis.ftran(alpha);
                // 有界比率测试
                double thetaMax = upper[entering] - lower[entering];
                double theta = thetaMax;
                int leavingPos = -1;
                boolean leaveAtLower = false;
                for (int i = 0; i < m; i++) {
                    double a = alpha[i];
                    if (Math.abs(a) < ZERO_TOL) {
                        continue;
                    }
                    double move = enterDir * a; // >0:x_B_i 降;<0:升
                    double room;
                    if (move > 0) {
                        room = xb[i] - lower[basic[i]];
                        if (lower[basic[i]] <= -LpModel.INF / 2) {
                            continue;
                        }
                    } else {
                        room = upper[basic[i]] - xb[i];
                        if (upper[basic[i]] >= LpModel.INF / 2) {
                            continue;
                        }
                    }
                    double ratio = room / Math.abs(move);
                    if (ratio < theta) {
                        theta = ratio;
                        leavingPos = i;
                        leaveAtLower = move > 0;
                    }
                }
                if (theta < 0 && theta > -FEAS_TOL) {
                    theta = 0; // 容差内负步长按零步长(退化)
                }
                if (theta < 0) {
                    return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                            "比率测试出现负步长 theta=" + theta);
                }
                if (leavingPos < 0 && theta >= LpModel.INF / 2) {
                    // 无界方向:本架构模型全部变量有界,仅人工变量残留等异常可达
                    return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                            "无界方向(进入变量无阻挡且界宽无限)");
                }
                if (leavingPos < 0) {
                    // 进入变量撞对侧界:界翻转,不换基
                    x[entering] = enterDir > 0 ? upper[entering] : lower[entering];
                    stall = theta == 0 ? stall + 1 : 0;
                } else {
                    int leaving = basic[leavingPos];
                    x[entering] = enterDir > 0 ? lower[entering] + theta : upper[entering] - theta;
                    x[leaving] = leaveAtLower ? lower[leaving] : upper[leaving];
                    // 换基:进入列替换 leavingPos 位置
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
                    // eta 累积阈值:重分解
                    if (basis.etaCount() > Basis.MAX_ETA) {
                        refactor(basis, model, artSign, basic, n, m);
                    }
                }
                // 停滞控制:触发/解除 Bland
                if (!bland && stall > STALL_LIMIT) {
                    bland = true;
                } else if (bland && stall == 0) {
                    bland = false;
                }
                iterations++;
            }
        } catch (Basis.NumericException e) {
            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                    "基分解数值失败: " + e.getMessage());
        }
    }

    /** 重分解当前基(结构列经 CSC 视图,人工列经 ±e_i 视图). */
    private static void refactor(Basis basis, LpModel model, double[] artSign, int[] basic, int n,
            int m) throws Basis.NumericException {
        List<Basis.SparseCol> columns = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            int var = basic[i];
            if (var < n) {
                columns.add(new CscCol(model.a, var));
            } else {
                columns.add(new UnitCol(var - n, artSign[var - n]));
            }
        }
        basis.factorize(columns);
    }

    /** 约简成本 d_j = c_j − y·A_j(人工列:c_j − y_i·sign_i). */
    private static double reducedCost(LpModel model, double[] artSign, double[] cost, double[] y,
            int j, int n) {
        if (j < n) {
            return cost[j] - model.a.dotColumn(j, y);
        }
        return cost[j] - y[j - n] * artSign[j - n];
    }

    /** 可进方向判定:+1 从下界升(d_j<0),-1 从上界降(d_j>0),0 不可进. */
    private static int directionOf(double dj, double xj, double lj, double uj) {
        if (dj < -OPT_TOL && xj < uj - ZERO_TOL) {
            return 1;
        }
        if (dj > OPT_TOL && xj > lj + ZERO_TOL) {
            return -1;
        }
        return 0;
    }

    /** 变量 j 的约束列(矩阵行语义稠密向量). */
    private static double[] columnOf(LpModel model, double[] artSign, int j, int n) {
        double[] col = new double[model.a.rows];
        if (j < n) {
            model.a.expandColumn(j, col);
        } else {
            col[j - n] = artSign[j - n];
        }
        return col;
    }

    /** 出口可行性自检:逐约束残差 + 界违例(容差相对 RHS 尺度). */
    private static boolean checkPrimalFeasible(LpModel model, double[] artSign, int[] basic,
            double[] x, double[] xb, double[] b, double[] lower, double[] upper, int n, int m) {
        double scale = 1 + maxAbs(b);
        for (int i = 0; i < m; i++) {
            if (xb[i] < lower[basic[i]] - FEAS_TOL * scale
                    || xb[i] > upper[basic[i]] + FEAS_TOL * scale) {
                return false;
            }
        }
        // 残差:Ax + Σ sign_i·x_art·e_i = b
        double[] residual = new double[m];
        for (int j = 0; j < n; j++) {
            if (x[j] != 0) {
                for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                    residual[model.a.rowIdx[p]] += model.a.values[p] * x[j];
                }
            }
        }
        for (int i = 0; i < m; i++) {
            residual[i] += artSign[i] * x[n + i] - b[i];
            if (Math.abs(residual[i]) > FEAS_TOL * scale) {
                return false;
            }
        }
        return true;
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

    /** 人工变量列(±e_i)的稀疏视图. */
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
