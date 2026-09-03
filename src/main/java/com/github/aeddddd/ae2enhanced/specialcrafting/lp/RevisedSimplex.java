package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

/**
 * 有界变量两阶段修正单纯形（1.12.2 合成计划 LP 核心,自研无依赖）.
 * <p>求解标准形 {@code min c·x, A x = b, l ≤ x ≤ u}:</p>
 * <ul>
 * <li><b>Phase 1</b>:每行一个人工变量（列 ±e_i,符号随 b 规整）,最小化人工变量和;
 * 和 &gt; 容差 → {@link LpResult.Status#INFEASIBLE};</li>
 * <li><b>Phase 2</b>:人工变量固定为 0,优化原目标;</li>
 * <li><b>定价</b>:全量定价取候选 + 候选集精确最陡边（|d_j|/‖α‖,优于 DEVEX
 * 近似,成本有界）;停滞时降级 Bland 规则（最小指标进出,保证反循环终止）;
 * 候选主元 &lt; max(PIVOT_REL×max‖α‖, PIVOT_ABS) 时拒绝该候选重定价
 * （小主元换基会毒化 eta 链,导航数轮内即漂移失控）,预算耗尽兜底接受
 * 并立即重分解清毒;</li>
 * <li><b>比率测试</b>:Harris 两阶段——第一阶段用松弛余量求步长(slack 按界标定,
 * 已越界行余量地板为 0 促使其被逐出,步长恒 ≥ 0;微观 α 行仅在最大步长下
 * 位移仍不可感知时才跳过,否则隐形位移可推 xb 出界);第二阶段在精确比率
 * ≤ 松弛步长的行中优先取健康主元的 |α| 最大者换基;</li>
 * <li><b>基修补</b>:重分解遇基奇异(基列集近线性相关)时,以人工单位列替换
 * 病态基列恢复分解(小预算,防整体近相关时的连锁失败),残留人工变量由
 * 比率测试/可行性恢复逐出,出口残差(不含人工项)兜底;</li>
 * <li><b>可行性恢复</b>:对偶最优(pricing 无候选)但基变量越界超出口容差时,
 * 做有界对偶单纯形步(对偶比率 min |d_j/α_j| 选进列,保持对偶可行,
 * 把最差越界基变量推回界内)——pricing 最优性与原始可行性无关,
 * 缺少本机制时"对偶最优+原始不可行"的顶点只能误判 NUMERIC_FAILURE;</li>
 * <li><b>残差精化</b>:出口自检未过先做一轮 δ = B⁻¹(b − A·x) 迭代精化,
 * 清除 LU/eta 累积漂移;精化后仍超标才判 NUMERIC_FAILURE,
 * 杜绝"解本身可行但漂移误判"的假失败;</li>
 * <li><b>出口自检</b>:残差容差 {@value #FEAS_TOL}(相对行活动量级,硬约束);
 * 界容差 {@value #EXIT_BOUND_TOL}(相对被违例的界,覆盖 Harris 松弛留量与
 * 混合量级模型的 xb 漂移)——禁止静默错解,也禁止漂移误判.</li>
 * </ul>
 * 数值体系:零判定 {@value #ZERO_TOL};约束矩阵小整数良态,RHS 大数值不直接影响
 * 条件数,但 xb 分量的绝对漂移下限 ∝ ε·(行耦合的大数值),故界验收不使用绝对微容差。
 */
public final class RevisedSimplex {

    private static final double FEAS_TOL = 1e-7;
    private static final double OPT_TOL = 1e-9;
    private static final double ZERO_TOL = 1e-10;
    /** 出口界容差·绝对项(相对被违例的界;≥ Harris 可行性松弛量级一个数量级). */
    private static final double EXIT_BOUND_TOL = 1e-6;
    /** 出口界容差·量级项(相对 max|xb|;双精度下 xb 分量的绝对漂移下限
     * ∝ ε·κ·(耦合的大数值),混合量级模型的导航漂移只能按此口径验收——
     * 与残差检查的"相对行活动量级"哲学一致;真实结构性越界(O(0.01+))仍必被捕获). */
    private static final double EXIT_SCALE_TOL = 1e-12;
    /** 迭代硬上限(防御;Bland 规则下理论有限终止). */
    private static final int MAX_ITER = 200_000;
    /** 连续退化(零步长)迭代阈值,超限切换 Bland 规则直至出现非零步长. */
    private static final int STALL_LIMIT = 500;
    /** 最陡边候选集大小(全量定价后按 |d_j| 取前 K 个计算精确最陡边). */
    private static final int EDGE_CANDIDATES = 32;
    /** 主元健康阈值·相对项(相对 max|α|;低于则拒绝该进入候选——小主元换基会毒化 eta 链). */
    private static final double PIVOT_REL = 1e-6;
    /** 主元健康阈值·绝对项(eta 主元模 p 的误差放大率 ≈ 1/p,p < 1e-3 时单 eta
     * 噪声即可达 2e-7/p ≈ 1e-3 量级(大尺度模型),必须拒绝或立即重分解). */
    private static final double PIVOT_ABS = 1e-3;
    /** 换基后立即重分解的主元阈值·相对项(相对 max|α|). */
    private static final double IMMEDIATE_REFACTOR_REL = 1e-4;
    /** 换基后立即重分解的主元阈值·绝对项(介于健康阈与常规主元之间,小主元 eta
     * 不留给后续迭代,把毒化限制在当轮). */
    private static final double IMMEDIATE_REFACTOR_ABS = 1e-2;
    /** 单迭代进入候选拒绝预算(超限接受非健康主元兜底). */
    private static final int MAX_REJECT = 8;

    private RevisedSimplex() {
    }

    /**
     * 求解 LP.纯函数(内部状态一次性),线程安全.
     * <p>求解链(四层兜底):</p>
     * <ol>
     * <li><b>行列均衡</b>:R·A·C 两轮 Ruiz 均衡,因子取 2 的幂(2 的幂乘法
     * 在双精度下无舍入,不引入新误差)——1:1000 级比率展布在进求解器前削平,
     * 显著降低小主元/基奇异/可行性恢复的触发率(工业求解器的第一道防线);</li>
     * <li>缩放模型默认轨迹求解 → 反缩放 + <b>原模型口径校验</b>(残差/界);</li>
     * <li>缩放模型全程 Bland 轨迹(完全不同主元序列,绕开病态角点);</li>
     * <li>未缩放原模型双轨迹兜底——任何返回的 OPTIMAL 都通过原模型校验.</li>
     * </ol>
     */
    public static LpResult solve(LpModel model) {
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
            return r; // INFEASIBLE/ITERATION_LIMIT:精确缩放不改变可行性本质,原样返回
        }
        // 缩放路径未收敛或反缩放校验未过:未缩放原模型兜底(两档轨迹)
        LpResult orig = solveInternal(model, false);
        if (orig.status == LpResult.Status.OPTIMAL
                || orig.status != LpResult.Status.NUMERIC_FAILURE) {
            return orig;
        }
        LpResult origBland = solveInternal(model, true);
        return origBland.status == LpResult.Status.OPTIMAL ? origBland : orig;
    }

    /** 缩放后的模型与反缩放信息:x = C·x′(C = diag(colScale)). */
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
     * 两轮 Ruiz 行列均衡:R·A·C,行/列 max|系数| 轮流压到 ≈1.
     * <p>因子一律取 2 的幂——2 的幂乘法在双精度下<b>无舍入</b>,缩放与反缩放
     * 全程精确,不向模型引入任何新误差;可行性/最优性与原模型严格等价.
     * 无限界(±INF)原样保留不缩放.</p>
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
            // 行均衡:每行 max|a_ij| → 2 的幂因子
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
            // 列均衡:每列 max|a_ij| → 2 的幂因子
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
        // b′ = R·b,c′ = C·c,l′ = l/C,u′ = u/C(x = C·x′;无限界原样保留)
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

    /** 使 max 缩放到 ≈1 的 2 的幂因子(0/空行/空列不缩放). */
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
     * 反缩放(x = C·x′)并用<b>原模型</b>口径校验界与残差(与出口自检同容差).
     *
     * @return 校验通过的 OPTIMAL 结果(目标值按原模型重算);不通过返回 null,
     *         由调用方回退未缩放路径
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
     * @param forceBland true = 全程 Bland 规则(反循环保证,速度慢但轨迹稳健),
     *                   供 NUMERIC_FAILURE 后的重试使用
     */
    private static LpResult solveInternal(LpModel model, boolean forceBland) {
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
        boolean bland = forceBland;
        /** 基修补预算(单位列替换病态基列的次数上限).
         * 取小值:修补只能救"偶发单列病态";基列集整体近相关时修补必然
         * 连锁失败(死亡螺旋),小预算快速 bail,交由 Bland 重试换轨迹. */
        int[] repairBudget = { 8 };
        /** 可行性恢复预算(对偶单纯形步数上限,防恢复死循环). */
        int[] restoreBudget = { 4 * m };
        /** 诊断:phase1 出口人工变量残留和(出口失败时写入 reason 供日志定位). */
        double phase1Residue = Double.NaN;
        /** 诊断:phase1→2 钳制后基变量最大越界量(顶点跳变幅度证据). */
        double transitionMaxViol = 0;

        try {
            refactor(basis, model, artSign, basic, where, x, lower, upper, n, m, repairBudget);
            while (true) {
                // 原始解:x_B = B⁻¹(b − A_N·x_N)——有界单纯形必须扣除非基变量
                // 坐在非零界(上界/非零下界)上的列贡献,否则界翻转后 RHS 即错
                double[] xb = computeBasicRhs(model, artSign, where, x, b, n, total, m);
                basis.ftran(xb);
                for (int i = 0; i < m; i++) {
                    x[basic[i]] = xb[i];
                }
                if (!phase1 && !Double.isNaN(phase1Residue) && transitionMaxViol == 0) {
                    // 相变后首个顶点:量测钳制导致的基变量最大越界(诊断)
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
                // 对偶:y = c_B·B⁻¹
                double[] y = new double[m];
                for (int i = 0; i < m; i++) {
                    y[i] = (phase1 ? costPhase1 : costPhase2)[basic[i]];
                }
                basis.btran(y);
                // 进入变量选择(定价 → 候选比率测试 → 主元健康检查):
                // 主元 < PIVOT_REL×max|α| 的候选被拒绝——小主元换基会毒化 eta 链,
                // 导航数轮内即漂移失控(实测 |α_p|~1e-7 换基后 5 轮内顶点漂移 >1);
                // 拒绝后重新定价取次优候选;拒绝预算耗尽/Bland 模式/全集重定价时
                // 接受非健康主元兜底,兜底换基后立即重分解清毒
                double[] cost = phase1 ? costPhase1 : costPhase2;
                int entering = -1;
                int enterDir = 0; // +1 从下界升,-1 从上界降
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
                    // 定价:非基变量约简成本(跳过已被拒绝的候选)
                    int cand = -1;
                    int candDir = 0;
                    if (!bland) {
                        // 候选集精确最陡边:先按 |d_j| 取前 K 个
                        int[] candSet = new int[EDGE_CANDIDATES];
                        int[] candSetDir = new int[EDGE_CANDIDATES];
                        double[] candAbs = new double[EDGE_CANDIDATES];
                        int candCount = 0;
                        for (int j = 0; j < total; j++) {
                            if (where[j] >= 0 || j >= n || (rejected != null && rejected[j])) {
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
                        // 候选集上精确最陡边
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
                        // Bland 规则:最小指标的可进变量(人工变量同样禁止再进基)
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
                            // 剩余候选均被拒绝过:清空拒绝集强制重定价,
                            // 本次接受任意主元(保证最优性判定不漏候选)
                            rejected = null;
                            rejectedCount = 0;
                            forced = true;
                            continue;
                        }
                        break; // entering = -1:最优性达成
                    }
                    // 候选进入列 α = B⁻¹A_j
                    double[] candAlpha = columnOf(model, artSign, cand, n);
                    basis.ftran(candAlpha);
                    double candMaxAbs = 0;
                    for (double v : candAlpha) {
                        candMaxAbs = Math.max(candMaxAbs, Math.abs(v));
                    }
                    // Harris 阶段一:松弛余量求步长(slack 按界标定,不随 xb 放大;
                    // 已越界行余量地板为 0,零比率阻挡促使逐出钳回,步长恒 ≥ 0);
                    // 微观 α 行仅在最大步长下位移仍不可感知时才允许跳过——
                    // 否则 theta×α 的"隐形"位移可将 xb 推出界外而不受保护
                    double candThetaMax = upper[cand] - lower[cand];
                    double[] roomArr = new double[m];
                    double[] moveArr = new double[m];
                    double candTheta = candThetaMax;
                    for (int i = 0; i < m; i++) {
                        double a = candAlpha[i];
                        if (a == 0) {
                            continue;
                        }
                        double move = candDir * a; // >0:x_B_i 降;<0:升
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
                            continue; // 微观 α:位移不可感知,跳过安全
                        }
                        roomArr[i] = room;
                        moveArr[i] = move;
                        // 可行性松弛:按界标定(1e-7 量级),容差内越界按可接受计;
                        // 已越界行余量地板为 0——零比率阻挡,下一步即被逐出钳回界上
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
                    // 阶段二:精确比率 ≤ 松弛步长的行中,优先取满足主元健康阈的
                    // |α| 最大者;无健康行时取最大者但标记非健康(交由拒绝/兜底决策)
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
                            // 理论不可达(松弛最小行的精确比率必 ≤ theta);防御显式失败
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
                    // 主元非健康:拒绝该候选,重定价取次优
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
                        // 进入 Phase 2:人工变量固定为 0(基内残值容差内钳到 0)
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
                    // 出口抛光:重分解清 eta 链尾段漂移 → 全量重算 xb——
                    // 可行性恢复判定与自检都必须基于新鲜值(漂移假越界会触发
                    // 无效恢复,而恢复在"实际可行的顶点"上必然报"无合格进列")
                    refactor(basis, model, artSign, basic, where, x, lower, upper, n, m,
                            repairBudget);
                    double[] xbPolished = computeBasicRhs(model, artSign, where, x, b, n, total, m);
                    basis.ftran(xbPolished);
                    for (int i = 0; i < m; i++) {
                        x[basic[i]] = xbPolished[i];
                    }
                    // 对偶最优但原始可能越界(Harris 松弛/修补/小主元漂移均可留下
                    // 越界基变量;比率测试只挡"会被进一步推动的行", pricing 判定
                    // 最优与原始可行性无关)——先查原始可行性,越界超阈时做一步
                    // 对偶单纯形(对偶比率测试选进列,保持对偶可行,把最差越界
                    // 基变量推回界内)再继续迭代;预算耗尽或无合格进列才诚实失败
                    int worstPos = -1;
                    double worstViol = 0;
                    boolean worstAtLower = false;
                    double restoreScaleTol = EXIT_SCALE_TOL * maxAbs(xbPolished);
                    for (int i = 0; i < m; i++) {
                        double lb = lower[basic[i]];
                        double ub = upper[basic[i]];
                        // 触发口径与出口自检一致(绝对项 + 量级项):容差内的
                        // 微越界属于双精度正常噪声,不进入恢复(否则恢复步在
                        // 噪声粒度上永动机式空转)
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
                        // 对偶在抛光基上重算(eta 链尾段漂移的 y 会误导对偶比率)
                        for (int i = 0; i < m; i++) {
                            y[i] = cost[basic[i]];
                        }
                        basis.btran(y);
                        // 对偶比率测试:r = B⁻ᵀe_p( tableau 行),在保持对偶可行
                        // (min |d_j/α_j|)且能把 xb[p] 推向界内的非基列中选进列
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
                            // xb[p] 对 x_j 的偏导是 −α_j(x_B = B⁻¹(b − A_N·x_N)):
                            // xb[p] 需增大 ⟺ j 升(dirJ=+1)且 α_j<0,或 j 降且 α_j>0
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
                            // 进列先撞对侧界:界翻转,xb[p] 部分恢复,下轮继续
                            x[enterJ] = enterDirR > 0 ? upper[enterJ] : lower[enterJ];
                        } else {
                            // 换基:进列替换 worstPos,旧基变量坐到被违例的界上
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
                            // 小主元 eta 毒化控制:与主循环同口径立即重分解
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
                    // 出口自检(基于上方抛光后的新鲜 xb);
                    // 自检未过先做一轮残差精化再复核——精化后仍超标才是真实失败
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
                    // 重分解时机:eta 链累积超阈,或本轮主元偏小(小主元 eta 会放大
                    // 后续全部 ftran 误差,立即重分解把毒化限制在当轮)
                    if (basis.etaCount() > Basis.MAX_ETA
                            || Math.abs(alpha[leavingPos]) < Math.max(
                                    IMMEDIATE_REFACTOR_REL * maxAbsAlpha,
                                    IMMEDIATE_REFACTOR_ABS)) {
                        refactor(basis, model, artSign, basic, where, x, lower, upper, n, m,
                                repairBudget);
                    }
                }
                // 停滞控制:触发/解除 Bland(forceBland 重试锁定 Bland,不得解除——
                // 否则首轮非退化迭代即掉回最陡边,重试轨迹与首轮完全相同,形同虚设)
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
     * 重分解当前基(结构列经 CSC 视图,人工列经 ±e_i 视图).
     * <p>分解失败(基奇异/病态,常见于小主元换基后基列集近线性相关)时做
     * <b>单位列修补</b>:失败位置的旧基变量退基坐到最近界上,该位置换入
     * 剩余模最大行的人工单位列 e_row——单位列条件数极佳,修补后分解即可成功;
     * 顶点由此发生的跳变由后续迭代自动恢复(残留人工变量一旦阻挡即以零比率
     * 被逐出基,出口残差自检中人工变量非零直接表现为残差违例,保证不错收).
     * 修补次数受预算限制,耗尽或无定位信息时诚实抛出.</p>
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
                if (e.failPos < 0 || e.bestRow < 0 || repairBudget[0] <= 0
                        || where[n + e.bestRow] >= 0) {
                    // 放弃修补:附原因(预算耗尽/候选人工列已在基/无定位)供诊断
                    String why = repairBudget[0] <= 0 ? "预算耗尽"
                            : e.bestRow >= 0 && where[n + e.bestRow] >= 0 ? "候选人工列已在基"
                                    : "无定位信息";
                    throw new Basis.NumericException(e.getMessage() + " [放弃修补:" + why + "]");
                }
                repairBudget[0]--;
                int pos = e.failPos;
                int oldVar = basic[pos];
                // 旧基变量退基:坐到最近界上(非基变量必须坐界)
                where[oldVar] = -1;
                x[oldVar] = Math.abs(x[oldVar] - lower[oldVar]) <= Math
                        .abs(x[oldVar] - upper[oldVar]) ? lower[oldVar] : upper[oldVar];
                // 该位置换入人工单位列 e_bestRow
                basic[pos] = n + e.bestRow;
                where[n + e.bestRow] = pos;
            }
        }
    }

    /**
     * 全量重算基右端:rhs = b − A_N·x_N.
     * 非基变量坐在非零界(上界/非零下界)上的列贡献必须扣除,否则界翻转后 RHS 即错.
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
     * 残差迭代精化:r = b − A·x(含人工列),δ = B⁻¹r 修正基变量取值.
     * 精化一轮可将 xb 的分解残差压到 ε² 量级,用于负步长重试与出口自检前复核.
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

    /**
     * 出口可行性自检:逐约束残差 + 界违例.
     * <p>残差容差 {@value #FEAS_TOL}(相对行活动量级,硬约束);界容差
     * {@value #EXIT_BOUND_TOL}(相对被违例的界,覆盖 Harris 松弛留量与 xb 漂移).</p>
     *
     * @return null = 通过;否则最差违例的明细(行/变量/量级/容差,供诊断日志)
     */
    @Nullable
    private static String checkPrimalFeasible(LpModel model, int[] basic,
            double[] x, double[] xb, double[] b, double[] lower, double[] upper, int n, int m) {
        // 量级项:混合量级模型 xb 分量的绝对漂移下限 ∝ ε·κ·max|xb|,按此验收
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
        // 残差:Ax + Σ sign_i·x_art·e_i = b;行尺度 = 1 + |b_i| + Σ|a_ij·x_j|
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
            // 残差 = A·x_struct − b,故意不含人工列:基修补残留的人工变量若取值非零,
            // 直接表现为该行残差违例(防止单位列修补掩盖真实不可行)
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
