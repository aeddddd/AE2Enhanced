package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 界变量对偶单纯形求解器, 参考 Bertsimas &amp; Tsitsiklis §6.4.
 * 求解 {@code min c·x, A x = b, lower ≤ x ≤ upper}, 约定成本向量非负、下界全为 0、每行都有松弛列.
 *
 * <p>数值处理上, 先对系数矩阵做 Ruiz 行列均衡, 在缩放后的空间里求解, 出口时再反缩放还原.
 * 迭代停滞时对右端做微扰, 微扰耗尽后改用 Bland 规则保证有限终止. 最终解在未扰动的右端上
 * 抛光并核对对偶可行性. 任何分解失败都返回 NUMERIC_FAILURE, 由调用方走降级路径.</p>
 */
public final class DualSimplex {

    /** 换基迭代的硬上限(防御性; 常规终止由 {@code iterBudget} 与 Bland 兜底决定). */
    private static final int MAX_ITER = Integer.getInteger("ae2e.dualMaxIter", 500_000);
    /** 约化成本的零阈值, 用于定价时的符号判定. */
    private static final double EPS_RC = 1e-11;
    /** 行系数的零阈值, 用于判定进入列资格, 作用于缩放空间. */
    private static final double EPS_ALPHA = 1e-9;
    /** 对偶可行容差带. 约化成本的绝对值低于该带宽时按 0 计入比率, 出口的对偶核对也用同一带宽. */
    private static final double DUAL_BAND =
            Double.parseDouble(System.getProperty("ae2e.dualFeasBand", "1e-3"));
    /** 界违反的基准相对容差, 相对 |x_B| 口径, 作用于缩放空间. */
    private static final double TOL_BOUND = 1e-9;
    /**
     * 尺度感知容差的系数(机器 ε 的约 45 倍), 参量为本轮模型/解的最大量级.
     * <p>B⁻¹b 在浮点下的绝对误差随右端量级线性增长: 巨单元的 b 含 4.6e18 库存分量,
     * 其 ULP 级"违反"(实测 ≤2.5e-16 相对)在旧口径下被 1e-9 绝对容差判为越界, 而
     * 该违反在比率测试里无任何可修补方向(纯舍入噪声), 于是误报 NUMERIC_FAILURE 并
     * 让整单元降级. 尺度项把这些噪声吸收为"该行已可行", 与 TOL_BOUND 的相对项相加,
     * 小量级模型(TOL_SCALE_REL·scaleRef ≪ TOL_BOUND)行为逐位不变.</p>
     */
    private static final double TOL_SCALE_REL =
            Double.parseDouble(System.getProperty("ae2e.dualScaleRel", "1e-14"));
    /**
     * 比率测试无候选时的"定点收敛"放宽系数: 无任何可修补方向且违反量 ≤ 该系数 × 行容差时,
     * 视为该行已可行(不再判 NUMERIC_FAILURE). 默认 1.0 = 只接受噪声容差内的违反;
     * 若实测存在略超全局尺度容差的无修补行, 可上调(代价: 该行解会带同量级的界违反).
     */
    private static final double TRAP_TOL_RELAX =
            Double.parseDouble(System.getProperty("ae2e.dualTrapRelax", "1.0"));
    /** 定点收敛容差写入时的乘性余量(吸收"容差恰等于违反量"时的加法舍入). */
    private static final double TOL_MARGIN_REL = 1e-6;
    /** 出口可行性自检的相对容差, 作用于原空间, 按行活动量级归一. */
    private static final double TOL_EXIT = 1e-7;
    /** 主元可接受的下限, 相对于进入列 FTRAN 结果的最大模. */
    private static final double PIVOT_REL =
            Double.parseDouble(System.getProperty("ae2e.dualPivotRel", "1e-10"));
    /** Ruiz 均衡的轮数. */
    private static final int RUIZ_ROUNDS = 10;
    /** 停滞检测窗口. 最大违反度连续这么多轮没有减半就触发微扰. */
    private static final int STALL_WINDOW = Integer.getInteger("ae2e.dualStallWindow", 24);
    /** 右端微扰的轮数上限, 耗尽后转 Bland 兜底. */
    private static final int MAX_PERTURB = Integer.getInteger("ae2e.dualMaxPerturb", 32);
    /**
     * 右端微扰的相对幅度: 越界行右端加 {@code ±该值×(1+|b_p|)} 的确定性扰动.
     * <p>必须与尺度噪声项同量级(F-1 起默认 1e-14): 旧值 1e-6 在 b≈4.6e18 的巨单元上
     * 等于 ±4.6e12 的绝对位移, 会把解推离最优顶点——表现为同一模型在剪枝档位不同
     * (轨迹不同)时给出不同的整数化执行记录. 取 ULP 量级既足以打破退化面的并列,
     * 又不改变可达最优解。</p>
     */
    private static final double PERTURB_REL =
            Double.parseDouble(System.getProperty("ae2e.dualPerturbRel", "1e-14"));
    /**
     * 迭代预算的系数(P-D 确定性): 预算 = {@code max(1024, DUAL_BUDGET_A*rows + DUAL_BUDGET_B*cols)}
     * 次换基迭代. 旧口径是墙钟预算(每行 10ms, 硬顶 30s), 负载不同则迭代轨迹不同,
     * 破坏任务 1 的跨 JVM 确定性; 改为只依赖模型的计数判据后同一逻辑输入恒走同一轨迹.
     */
    private static final int DUAL_BUDGET_A = Integer.getInteger("ae2e.dualBudgetPerRow", 4);
    private static final int DUAL_BUDGET_B = Integer.getInteger("ae2e.dualBudgetPerCol", 2);
    /**
     * 墙钟紧急熔断(ms, 默认 5 分钟): 只在 CPU 极端抢占等异常情况下兜底, 正常负载下
     * 永不触发(触发即返回 NUMERIC_FAILURE, 属确定性纪律的例外). 0 = 关闭熔断.
     */
    private static final long FUSE_MS = Long.getLong("ae2e.dualFuseMs", 300_000L);

    private DualSimplex() {
    }

    /**
     * 求解产物: 结果 + 供后续同基模型复用的热启动状态(P-A)。
     * {@code next} 为 null 表示失败(无可复用状态)。
     */
    static final class Outcome {
        final LpResult result;
        @javax.annotation.Nullable
        final LpHotStart next;
        /** 本次求解是否真正走了热启动(分类通过并复用旧基)。 */
        final boolean hot;

        private Outcome(LpResult result, LpHotStart next, boolean hot) {
            this.result = result;
            this.next = next;
            this.hot = hot;
        }
    }

    /**
     * Ruiz 行列均衡方案(P-C): 逐轮的行/列缩放因子被完整记录, 因此对<b>逐位相同</b>的
     * 系数矩阵可以"重放"出与首次计算逐位相同的缩放结果(顺序、结合律都不变),
     * 从而让热启动与冷启动在缩放空间里逐位一致——这是热启动等价性的前提。
     */
    static final class ScalingPlan {
        final int rows;
        final int cols;
        /** 未缩放系数矩阵的结构指纹(仅用于判定能否复用). */
        final long aHash;
        /** 逐轮行缩放因子(RUIZ_ROUNDS × rows); null = 本次求解未记录(链未启用). */
        final double[][] rowRounds;
        /** 逐轮列缩放因子(RUIZ_ROUNDS × cols); null = 本次求解未记录. */
        final double[][] colRounds;
        /** 累计列缩放因子(出口反缩放用). */
        final double[] colScale;

        private ScalingPlan(int rows, int cols, long aHash, double[][] rowRounds, double[][] colRounds,
                double[] colScale) {
            this.rows = rows;
            this.cols = cols;
            this.aHash = aHash;
            this.rowRounds = rowRounds;
            this.colRounds = colRounds;
            this.colScale = colScale;
        }

        /** 逐轮因子是否已记录(未记录 ⇒ 不可被热启动重放). */
        boolean replayable() {
            return this.rowRounds != null && this.colRounds != null;
        }
    }

    /**
     * LU 填元比埋点快照(需 {@code -Dae2e.basisStats=true}): 记 Σ(nnzL+nnzU)/ΣnnzB。
     * 只在诊断/测试里调用, 供 A4(Markowitz 列序)取舍评估用。
     */
    public static String basisStats() {
        return Basis.statsSnapshot();
    }

    /** 系数矩阵结构指纹(行列 + 结构 + 取值; 只作复用门控, 出口自检仍按真实模型核对). */
    static long fingerprint(SparseMatrix a) {
        long h = 1125899906842597L;
        h = h * 31 + a.rows;
        h = h * 31 + a.cols;
        h = h * 31 + java.util.Arrays.hashCode(a.colPtr);
        h = h * 31 + java.util.Arrays.hashCode(a.rowIdx);
        h = h * 31 + java.util.Arrays.hashCode(a.values);
        return h;
    }

    /**
     * 首次计算 Ruiz 缩放并就地应用到 {@code a/b/lower/upper}, 同时记录逐轮因子(P-C)。
     * 缩放只按 A 的行列范数计算, b 与 c 随动但不混入范数(理由见原注释)。
     */
    private static ScalingPlan computeScaling(SparseMatrix a, double[] b, double[] cost, double[] lower,
            double[] upper, long aHash, boolean record) {
        int m = a.rows;
        int n = a.cols;
        double[][] rowRounds = record ? new double[RUIZ_ROUNDS][] : null;
        double[][] colRounds = record ? new double[RUIZ_ROUNDS][] : null;
        double[] colScale = new double[n];
        Arrays.fill(colScale, 1.0);
        for (int round = 0; round < RUIZ_ROUNDS; round++) {
            double[] rn = a.rowInfNorms();
            double[] rs = new double[m];
            for (int i = 0; i < m; i++) {
                rs[i] = rn[i] > 1e-300 ? 1.0 / Math.sqrt(rn[i]) : 1.0;
            }
            a.scaleRowsInPlace(rs);
            for (int i = 0; i < m; i++) {
                b[i] *= rs[i];
            }
            double[] cn = a.colInfNorms();
            double[] cs = new double[n];
            for (int j = 0; j < n; j++) {
                cs[j] = cn[j] > 1e-300 ? 1.0 / Math.sqrt(cn[j]) : 1.0;
            }
            a.scaleColumnsInPlace(cs);
            for (int j = 0; j < n; j++) {
                cost[j] *= cs[j];
                colScale[j] *= cs[j];
                applyColScale(cs[j], j, lower, upper);
            }
            if (record) {
                rowRounds[round] = rs;
                colRounds[round] = cs;
            }
        }
        return new ScalingPlan(m, n, aHash, rowRounds, colRounds, colScale);
    }

    /**
     * 重放缩放方案(P-C 复用路径): 对逐位相同的 A 用记录的逐轮因子重新施加缩放,
     * 顺序与 {@link #computeScaling} 完全一致, 因此 a/b/c/lower/upper 的缩放结果逐位相同。
     */
    private static void applyScaling(ScalingPlan plan, SparseMatrix a, double[] b, double[] cost,
            double[] lower, double[] upper) {
        int n = a.cols;
        for (int round = 0; round < plan.rowRounds.length; round++) {
            double[] rs = plan.rowRounds[round];
            double[] cs = plan.colRounds[round];
            a.scaleRowsInPlace(rs);
            for (int i = 0; i < b.length; i++) {
                b[i] *= rs[i];
            }
            a.scaleColumnsInPlace(cs);
            for (int j = 0; j < n; j++) {
                cost[j] *= cs[j];
                applyColScale(cs[j], j, lower, upper);
            }
        }
    }

    /** 单个变量的界随列缩放反向缩放(缩放恒为正, 无穷界保持不变). */
    private static void applyColScale(double cs, int j, double[] lower, double[] upper) {
        lower[j] = lower[j] <= -LpModel.INF ? Double.NEGATIVE_INFINITY
                : lower[j] >= LpModel.INF ? Double.POSITIVE_INFINITY : lower[j] / cs;
        upper[j] = upper[j] >= LpModel.INF ? Double.POSITIVE_INFINITY
                : upper[j] <= -LpModel.INF ? Double.NEGATIVE_INFINITY : upper[j] / cs;
    }


    /** 确定性伪随机数, 范围 ±1, 只依赖行号与轮次, 跨运行可复现. 用于右端微扰. */
    private static double pseudoRandom(int i, int round) {
        double s = Math.sin(i * 12.9898 + round * 78.233) * 43758.5453;
        return (s - Math.floor(s)) * 2.0 - 1.0;
    }

    /**
     * 本轮尺度参照: {@code max(1, max|b_i|, max|xB_p|)}, 尺度感知容差的噪声基准.
     * 取整模型最大量级而非逐行量级: B⁻¹b 的误差由右端最大分量经逆矩阵行传播而来,
     * 与违反行自身的量级无关(这正是旧口径误判的根源).
     */
    private static double scaleRef(double[] b, double[] xB) {
        double max = 1;
        for (double v : b) {
            double a = Math.abs(v);
            if (a > max) {
                max = a;
            }
        }
        for (double v : xB) {
            double a = Math.abs(v);
            if (a > max) {
                max = a;
            }
        }
        return max;
    }

    /** 该行的界违反判定容差(缩放空间): 基准相对项 + 模型尺度项. */
    private static double rowTol(double xBp, double tolScale) {
        return TOL_BOUND * (1 + Math.abs(xBp)) + tolScale;
    }

    /** 非基变量右端贡献后的右端向量: {@code rhs = b − Σ_{state==2} A_j·x_j}(与主循环同口径). */
    private static double[] rhsOf(double[] b, SparseMatrix a, byte[] state, double[] x, int n, int m) {
        double[] rhs = b.clone();
        for (int j = 0; j < n; j++) {
            if (state[j] == 2 && x[j] != 0) {
                for (int p = a.colPtr[j]; p < a.colPtr[j + 1]; p++) {
                    rhs[a.rowIdx[p]] -= x[j] * a.values[p];
                }
            }
        }
        return rhs;
    }

    /**
     * 起步分类的"对偶可行"判定: 1 次 BTRAN + O(nnz) 定价, 逐非基列核对约化成本符号
     * (带宽用与主循环相同的相对口径 DUAL_BAND, 保证主循环接手后比率测试口径一致).
     */
    private static boolean dualFeasible(SparseMatrix a, double[] c, byte[] state, int[] basisCol,
            Basis basis, int n) {
        double[] y = new double[basisCol.length];
        for (int p = 0; p < basisCol.length; p++) {
            y[p] = c[basisCol[p]];
        }
        basis.btran(y);
        for (int j = 0; j < n; j++) {
            if (state[j] == 0) {
                continue;
            }
            double rc = c[j] - a.dotColumn(j, y);
            double rcTol = DUAL_BAND * (1 + Math.abs(c[j]));
            if (state[j] == 1 && rc < -rcTol) {
                return false;
            }
            if (state[j] == 2 && rc > rcTol) {
                return false;
            }
        }
        return true;
    }

    /**
     * 热启动的界应用步骤: 按变量下标升序扫描(不走 HashMap 遍历, 确定性纪律),
     * 把新模型(可能收紧)的界套到非基变量上. 基变量的值由 FTRAN 重新算出,
     * 其越界正是对偶主循环要修的对象.
     */
    private static void applyHotBounds(byte[] state, double[] x, double[] lower, double[] upper) {
        for (int j = 0; j < state.length; j++) {
            if (state[j] == 2) {
                if (upper[j] >= LpModel.INF) {
                    // 上界被放开(或本就无穷)而变量被钉在上界: 改钉下界, 失去对偶可行由分类兜底
                    state[j] = 1;
                    x[j] = lower[j];
                } else {
                    x[j] = upper[j];
                }
            } else if (state[j] == 1) {
                x[j] = lower[j];
            }
        }
    }

    /** 求解 {@code min c·x, A x = b, lower ≤ x ≤ upper}. */
    public static LpResult solve(LpModel model) {
        return solveCore(model, null, false, false, new LpHotStart[1], new int[1]);
    }

    /**
     * P-A 热启动求解: 若 {@code prior} 与 model 的系数矩阵逐位相同(结构指纹一致),
     * 则复用 prior 的基/LU/eta/缩放方案, 只重做"界与右端变化"引起的修正;
     * 否则退回冷启动. 返回的 {@code next} 可在同基模型上继续链式复用
     * (prior 的 {@link Basis} 所有权随本次调用转移, 不得再用).
     *
     * <p>起步分类(不靠调用方声明): 1 次 FTRAN + 1 次 BTRAN + O(nnz) 定价,
     * 判定旧解在新模型上是否保持对偶可行。是则直接进对偶主循环; 否则——由于
     * 本单元未实现 P-B(原单纯形再优化)——回退冷启动。出口自检(抛光/可行性/
     * 对偶符号核对)在热启动路径上<b>照常执行</b>, 不得绕过。</p>
     */
    static Outcome solveHot(LpModel model, LpHotStart prior, LpHotStart.Chain chain) {
        boolean allow = chain != null && chain.enabled();
        LpHotStart[] next = new LpHotStart[1];
        int[] hotPivots = new int[1];
        LpResult result = solveCore(model, prior, allow, allow, next, hotPivots);
        // verify 档: 热启动 pivot 数超预算即视为轨迹异常, 放弃热启动结果冷启动重解一次
        int budget = prior == null ? 0 : chain.pivotBudget(prior.iterations);
        if (allow && prior != null && budget > 0 && hotPivots[0] > budget
                && result.status == LpResult.Status.OPTIMAL) {
            LpHotStart[] next2 = new LpHotStart[1];
            int[] pivots2 = new int[1];
            result = solveCore(model, null, false, true, next2, pivots2);
            next = next2;
            hotPivots = pivots2;
        }
        boolean hot = hotPivots[0] >= 0;
        if (hot) {
            chain.noteHotReuse();
        }
        return new Outcome(result, next[0], hot);
    }

    /**
     * 求解主体: {@code allowHot} 为真时把 prior 的基作为起点(见 {@link #solveHot});
     * {@code recordScaling} 为真时记录逐轮 Ruiz 因子(热启动重放用; 链未启用时可省掉
     * 该内存/时间开销). 出口把可复用的链状态写入 {@code nextOut[0]}(仅 OPTIMAL),
     * 把热启动实际 pivot 数写入 {@code hotPivotsOut[0]}(0 = 冷启动轨迹)。
     */
    private static LpResult solveCore(LpModel model, LpHotStart prior, boolean allowHot,
            boolean recordScaling, LpHotStart[] nextOut, int[] hotPivotsOut) {
        int m = model.a.rows;
        int n = model.a.cols;
        if (n == 0) {
            return m == 0 ? LpResult.optimal(new double[0], 0, 0)
                    : LpResult.failure(LpResult.Status.NUMERIC_FAILURE, 0, "空列模型");
        }
        // P-D: 预算判据改迭代数驱动(确定性), 墙钟只作紧急熔断
        long iterBudget = Math.max(1024L, (long) DUAL_BUDGET_A * m + (long) DUAL_BUDGET_B * n);
        long fuseNanos = FUSE_MS > 0 ? System.nanoTime() + FUSE_MS * 1_000_000L : 0L;

        // Ruiz 均衡只按 A 的行列范数缩放, b 和 c 随动但不混入范数.
        // 如果把 b 混入行范数, 大需求行会把松弛列的系数压到数值噪声量级, 初始基 LU 会判奇异.
        long aHash = fingerprint(model.a);
        SparseMatrix a = copyOf(model.a);
        double[] b = model.b.clone();
        double[] c = model.cost.clone();
        double[] lower = model.lower.clone();
        double[] upper = model.upper.clone();
        ScalingPlan plan;
        boolean reuse = allowHot && prior != null && prior.rows == m && prior.cols == n
                && prior.aHash == aHash && prior.plan.replayable();
        if (reuse) {
            plan = prior.plan; // P-C: 逐轮因子重放, 与冷启动缩放结果逐位相同
            applyScaling(plan, a, b, c, lower, upper);
        } else {
            plan = computeScaling(a, b, c, lower, upper, aHash, recordScaling);
        }
        double[] colScale = plan.colScale;

        // 保存未扰动的右端. 停滞微扰只作用于求解轨迹, 出口一律回到未扰动模型结算.
        double[] bOrig = b.clone();

        int[] basisCol;
        byte[] state;
        double[] x;
        Basis basis;
        boolean hotStarted = false;
        if (reuse) {
            basisCol = prior.basisCol.clone();
            state = prior.state.clone();
            x = prior.x.clone();
            basis = prior.basis; // 所有权转移(见 solveHot 契约)
            applyHotBounds(state, x, lower, upper);
            hotStarted = true;
        } else {
            basisCol = new int[m];
            state = new byte[n];
            x = new double[n];
            basis = new Basis(m);
            hotStarted = false;
        }

        // 行到触及列的反排索引, 修补基时用它按候选行找空闲的单位松弛列
        List<int[]> rowCols = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            rowCols.add(new int[0]);
        }
        {
            int[] cnt = new int[m];
            for (int j = 0; j < n; j++) {
                for (int p = a.colPtr[j]; p < a.colPtr[j + 1]; p++) {
                    cnt[a.rowIdx[p]]++;
                }
            }
            for (int i = 0; i < m; i++) {
                rowCols.set(i, new int[cnt[i]]);
            }
            int[] fill = new int[m];
            for (int j = 0; j < n; j++) {
                for (int p = a.colPtr[j]; p < a.colPtr[j + 1]; p++) {
                    int i = a.rowIdx[p];
                    rowCols.get(i)[fill[i]++] = j;
                }
            }
        }

        if (hotStarted) {
            // 起步分类: 1 FTRAN(x_B) + 1 BTRAN(y) + O(nnz) 定价, 判定旧解在新模型上
            // 是否仍对偶可行. 对偶可行 ⇒ 主循环可直接接手(越界由它修);
            // 否则需要原单纯形再优化(P-B, 本单元未实现) ⇒ 冷启动。
            double[] rhs0 = rhsOf(b, a, state, x, n, m);
            double[] xB0 = rhs0.clone();
            basis.ftran(xB0);
            boolean dualOk = dualFeasible(a, c, state, basisCol, basis, n);
            if (!dualOk) {
                hotStarted = false;
            }
        }
        if (!hotStarted) {
            // 初始基取零成本 ±1 松弛列构成的阶梯三角基. c_B 为 0 意味着起步即对偶可行,
            // 不需要人工变量的 Phase-1. 基在原模型上选取, 再对缩放后的矩阵用同一列集做 LU.
            byte[] tmpState = new byte[n];
            Arrays.fill(tmpState, (byte) 1);
            if (!selectInitialBasis(model, basisCol, tmpState)) {
                return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, 0,
                        "初始基覆盖失败(每行需有零成本 ±1 松弛列:模型构造缺陷)");
            }
            // 状态编码: 0 表示在基, 1 表示钉在下界, 2 表示钉在上界. 非基变量的值钉在界上.
            for (int j = 0; j < n; j++) {
                if (c[j] < -EPS_RC) {
                    // 负成本列起步要贴上界才对偶可行. 本项目模型成本非负, 这段是纯防御.
                    if (upper[j] >= LpModel.INF) {
                        return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, 0,
                                "负成本列上界无穷,对偶不可行起步: 列 " + j);
                    }
                    state[j] = 2;
                    x[j] = upper[j];
                } else {
                    state[j] = 1;
                    x[j] = lower[j];
                }
            }
            for (int p = 0; p < m; p++) {
                state[basisCol[p]] = 0;
            }
            try {
                factorizeWithRepair(basis, a, basisCol, state, x, lower, rowCols, c);
            } catch (Basis.NumericException e) {
                return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, 0,
                        "初始基分解失败: " + e.getMessage());
            }
        }

        double[] work = new double[m];
        double[] y = new double[m]; // 对偶向量, BTRAN 的输出是矩阵行语义
        double[] cB = new double[m];
        double[] d = new double[m]; // 进入列的 FTRAN 结果, 位置语义
        double[] xB = new double[m];
        int iterations = 0;
        double lastViol = Double.MAX_VALUE; // 停滞检测用的上一轮最大违反度
        int stallCount = 0;
        int perturbRound = 0;
        boolean bland = false; // Bland 兜底通道, 微扰耗尽后进入
        int polishRestores = 0; // 出口抛光-恢复循环的计数, 封顶 8 轮
        // 定点收敛容差(按列号索引, 懒分配): 比率测试无候选且违反在噪声容差内的行,
        // 其出基变量的判定容差被抬到违反量, 从而不再被判为越界行(见比率测试分支)
        double[] colTolExtra = null;

        while (true) {
            // 原始值 x_B = B⁻¹(b − Σ_{非基} A_j·x_j), 下界为 0 的非基列无贡献
            double[] rhs = b.clone();
            for (int j = 0; j < n; j++) {
                if (state[j] == 2 && x[j] != 0) {
                    for (int p = a.colPtr[j]; p < a.colPtr[j + 1]; p++) {
                        rhs[a.rowIdx[p]] -= x[j] * a.values[p];
                    }
                }
            }
            System.arraycopy(rhs, 0, work, 0, m);
            basis.ftran(work);
            System.arraycopy(work, 0, xB, 0, m);
            // 尺度感知噪声项: 随本轮右端/解的最大量级增长, 覆盖 B⁻¹b 的累积舍入.
            // 它同时进入选行容差——否则退化噪声会被当作"待修违反"反复换基(实测
            // 1002 行模型会从 ~800 轮恶化到 2 万~23 万轮), 但幅度取机器 ε 的数十倍
            // 以内, 只吸收 ULP 级残余而不改变真实的可行域判定.
            double tolScale = TOL_SCALE_REL * scaleRef(b, xB);

            // 出基行取最越界的行. 微扰耗尽后转严格 Bland, 也就是取首个越界者, 数学上保证有限终止.
            int leave = -1;
            double worstViol = 0;
            boolean leaveToLower = true;
            for (int p = 0; p < m; p++) {
                int j = basisCol[p];
                double lo = lower[j];
                double up = upper[j];
                double tol = TOL_BOUND * (1 + Math.abs(xB[p])) + tolScale
                        + (colTolExtra == null ? 0 : colTolExtra[j]);
                if (up < LpModel.INF && xB[p] > up + tol) {
                    double viol = (xB[p] - up) / (1 + Math.abs(up));
                    if (viol > worstViol) {
                        worstViol = viol;
                        leave = p;
                        leaveToLower = false;
                        if (bland) {
                            break;
                        }
                    }
                } else if (xB[p] < lo - tol) {
                    double viol = (lo - xB[p]) / (1 + Math.abs(lo) + Math.abs(xB[p]));
                    if (viol > worstViol) {
                        worstViol = viol;
                        leave = p;
                        leaveToLower = true;
                        if (bland) {
                            break;
                        }
                    }
                }
            }
            // 停滞检测: 最大违反度连续多轮没有减半, 说明迭代在退化顶点簇里打转.
            // 这时对越界行的右端加上确定性的小扰动再继续求解, 最终解始终按未扰动模型结算.
            if (worstViol > 0) {
                if (worstViol > lastViol * 0.5) {
                    stallCount = 0;
                    lastViol = worstViol;
                } else if (++stallCount >= STALL_WINDOW) {
                    stallCount = 0;
                    perturbRound++;
                    if (perturbRound > MAX_PERTURB && !bland) {
                        // 微扰耗尽, 进入严格 Bland 兜底通道
                        bland = true;
                        try {
                            factorizeWithRepair(basis, a, basisCol, state, x, lower, rowCols, c);
                        } catch (Basis.NumericException e) {
                            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                    "Bland 通道重分解失败: " + e.getMessage());
                        }
                        continue;
                    }
                    for (int p = 0; p < m; p++) {
                        int j = basisCol[p];
                        double lo = lower[j];
                        double up = upper[j];
                        double tol = TOL_BOUND * (1 + Math.abs(xB[p])) + tolScale
                                + (colTolExtra == null ? 0 : colTolExtra[j]);
                        boolean vioUp = up < LpModel.INF && xB[p] > up + tol;
                        boolean vioLo = xB[p] < lo - tol;
                        if (vioUp || vioLo) {
                            b[p] += pseudoRandom(p, perturbRound) * PERTURB_REL * (1 + Math.abs(b[p]));
                        }
                    }
                    try {
                        factorizeWithRepair(basis, a, basisCol, state, x, lower, rowCols, c);
                    } catch (Basis.NumericException e) {
                        return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                "微扰后重分解失败: " + e.getMessage());
                    }
                    continue;
                }
            }
            if (leave < 0) {
                // 出口抛光按未扰动的右端结算. 如果暴露出越界, 就恢复 bOrig 回主循环继续求解.
                try {
                    factorizeWithRepair(basis, a, basisCol, state, x, lower, rowCols, c);
                } catch (Basis.NumericException e) {
                    return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                            "出口重分解失败: " + e.getMessage());
                }
                rhs = bOrig.clone();
                for (int j = 0; j < n; j++) {
                    if (state[j] == 2 && x[j] != 0) {
                        for (int p = a.colPtr[j]; p < a.colPtr[j + 1]; p++) {
                            rhs[a.rowIdx[p]] -= x[j] * a.values[p];
                        }
                    }
                }
                System.arraycopy(rhs, 0, work, 0, m);
                basis.ftran(work);
                System.arraycopy(work, 0, xB, 0, m);
                // 迭代精化: 对 LU 解做残差回代修正, 即 Wilkinson refinement
                for (int ref = 0; ref < 2; ref++) {
                    double[] r = rhs.clone();
                    for (int p = 0; p < m; p++) {
                        if (xB[p] == 0) {
                            continue;
                        }
                        int col = basisCol[p];
                        for (int q = a.colPtr[col]; q < a.colPtr[col + 1]; q++) {
                            r[a.rowIdx[q]] -= xB[p] * a.values[q];
                        }
                    }
                    double maxR = 0;
                    for (double v : r) {
                        maxR = Math.max(maxR, Math.abs(v));
                    }
                    if (maxR <= 1e-12) {
                        break;
                    }
                    System.arraycopy(r, 0, work, 0, m);
                    basis.ftran(work);
                    for (int p = 0; p < m; p++) {
                        xB[p] += work[p];
                    }
                }
                // 越界核对: 精化后的 xB 比主循环更准, 可能暴露主循环看不到的越界。
                // 尺度噪声项以内的越界(=浮点残余)不算越界; 真越界仍恢复 bOrig 回主循环修,
                // 循环封顶防止在"扰动收敛/未扰动暴露"之间乒乓.
                double exitTolScale = TOL_SCALE_REL * scaleRef(bOrig, xB);
                boolean stillViolated = false;
                for (int p = 0; p < m; p++) {
                    int j = basisCol[p];
                    double lo = lower[j];
                    double up = upper[j];
                    double tol = TOL_BOUND * (1 + Math.abs(xB[p])) + exitTolScale
                            + (colTolExtra == null ? 0 : colTolExtra[j]);
                    if ((up < LpModel.INF && xB[p] > up + tol) || xB[p] < lo - tol) {
                        stillViolated = true;
                        break;
                    }
                }
                if (stillViolated) {
                    // 抛光暴露出真越界, 恢复 bOrig 继续求解. 循环必须封顶,
                    // 否则会在扰动收敛和未扰动暴露之间乒乓打转.
                    if (++polishRestores > 8) {
                        return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                "出口抛光反复暴露越界(扰动-恢复循环 " + polishRestores + " 轮)");
                    }
                    b = bOrig.clone();
                    continue;
                }
                for (int p = 0; p < m; p++) {
                    x[basisCol[p]] = xB[p];
                }
                // 最优性核对: 非基约化成本的符号必须成立, 否则当前点可行但不是最优, 拒收.
                for (int p = 0; p < m; p++) {
                    cB[p] = c[basisCol[p]];
                }
                System.arraycopy(cB, 0, y, 0, m);
                basis.btran(y);
                String dualBad = null;
                for (int j = 0; j < n && dualBad == null; j++) {
                    if (state[j] == 0) {
                        continue;
                    }
                    double rc = c[j] - a.dotColumn(j, y);
                    double rcTol = DUAL_BAND * (1 + Math.abs(c[j]));
                    if (state[j] == 1 && rc < -rcTol) {
                        dualBad = "下界非基列 rc<0: 列 " + j + " rc=" + rc;
                    } else if (state[j] == 2 && rc > rcTol) {
                        dualBad = "上界非基列 rc>0: 列 " + j + " rc=" + rc;
                    }
                }
                if (dualBad != null) {
                    return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                            "出口对偶不可行(非最优,拒收): " + dualBad);
                }
                double[] xOrig = new double[n];
                for (int j = 0; j < n; j++) {
                    double xj = x[j] * colScale[j];
                    xOrig[j] = Math.abs(xj) < 1e-12 ? 0 : xj;
                }
                String bad = exitCheck(model, xOrig, colTolExtra, colScale, exitTolScale);
                if (bad != null) {
                    return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations, bad);
                }
                double obj = 0;
                for (int j = 0; j < n; j++) {
                    obj += model.cost[j] * xOrig[j];
                }
                // 链状态回传: 基/状态/解/因子化/缩放方案可被同基模型复用(P-A).
                // hotPivotsOut 用 -1 表示冷启动轨迹(不计入热启动预算).
                hotPivotsOut[0] = hotStarted ? iterations : -1;
                nextOut[0] = new LpHotStart(m, n, aHash, plan, basisCol, state, x, basis, iterations);
                return LpResult.optimal(xOrig, obj, iterations);
            }
            if (++iterations > MAX_ITER) {
                return LpResult.failure(LpResult.Status.ITERATION_LIMIT, iterations,
                        "对偶单纯形超迭代上限(m=" + m + ")");
            }
            // P-D: 预算判据 = 迭代数(确定性); 墙钟只剩紧急熔断(正常负载下永不触发)
            if ((iterations & 0x3F) == 0) {
                if (iterations > iterBudget) {
                    return LpResult.failure(LpResult.Status.ITERATION_LIMIT, iterations,
                            "对偶单纯形超迭代预算(" + iterBudget + ",m=" + m + ")");
                }
                if (fuseNanos != 0L && System.nanoTime() > fuseNanos) {
                    return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                            "对偶单纯形触发墙钟紧急熔断(" + FUSE_MS + "ms,m=" + m + ")");
                }
            }

            int leaveCol = basisCol[leave];
            double target = leaveToLower ? lower[leaveCol] : upper[leaveCol];
            double need = target - xB[leave]; // 出基变量所需的位移, leaveToLower 时为正

            // 定价行 u = e_leaveᵀ·B⁻¹. BTRAN 的输入是位置语义, 输出是矩阵行语义.
            Arrays.fill(work, 0);
            work[leave] = 1.0;
            basis.btran(work);
            double[] u = work.clone();

            // 对偶向量 y, 解 Bᵀy = c_B. c_B 用位置语义, 即各基列的成本.
            for (int p = 0; p < m; p++) {
                cB[p] = c[basisCol[p]];
            }
            System.arraycopy(cB, 0, y, 0, m);
            basis.btran(y);

            // 比率测试加界翻转循环: 同一出基行可以多次翻转界, 直到完成换基.
            boolean pivoted = false;
            boolean refactored = false;
            int fallbackEnter = -1; // 主元不达标但方向最优的兜底列, 跨重试保留, 供角点强制换基用
            boolean tolerated = false; // 本轮因"无候选且违反在噪声容差内"放弃修该行
            ratioTest:
            while (true) {
                // 比率测试分两遍. 第一遍求精确的最小比率, 漏掉真最小会破坏对偶可行性;
                // 第二遍在并列候选中取方向最陡的一个, 退化区里有大量 ratio 为 0 的并列.
                double minRatio = Double.POSITIVE_INFINITY;
                for (int j = 0; j < n; j++) {
                    if (state[j] == 0) {
                        continue;
                    }
                    double alpha = a.dotColumn(j, u);
                    boolean eligible = need > 0
                            ? (state[j] == 1 && alpha < -EPS_ALPHA) || (state[j] == 2 && alpha > EPS_ALPHA)
                            : (state[j] == 1 && alpha > EPS_ALPHA) || (state[j] == 2 && alpha < -EPS_ALPHA);
                    if (!eligible) {
                        continue;
                    }
                    double rc = c[j] - a.dotColumn(j, y);
                    double rcTol = DUAL_BAND * (1 + Math.abs(c[j]));
                    if (state[j] == 1 && rc < 0 && rc > -rcTol) {
                        rc = 0;
                    } else if (state[j] == 2 && rc > 0 && rc < rcTol) {
                        rc = 0;
                    }
                    double ratio = Math.abs(rc / alpha);
                    if (ratio < minRatio) {
                        minRatio = ratio;
                    }
                }
                if (minRatio == Double.POSITIVE_INFINITY) {
                    // 无任何可修补方向. 先判违反量是否已在尺度感知的噪声容差内: 成立即
                    // 视为该行已可行(定点收敛), 抬高该出基变量的判定容差后重选行;
                    // 不得把纯舍入噪声直接判成 NUMERIC_FAILURE(巨单元的 4.6e18 右端
                    // 会让 B⁻¹b 的解在零附近带上 ULP 级残余, 此处是唯一出口).
                    double ask = Math.abs(need);
                    if (ask <= rowTol(xB[leave], tolScale) * TRAP_TOL_RELAX) {
                        if (colTolExtra == null) {
                            colTolExtra = new double[n];
                        }
                        // 乘性余量: colTolExtra 与基准项相加后再与违反量比较, 加法舍入
                        // 可能让"恰好等于违反量"的容差仍判越界(白盒实测 1e-9 相对量级)
                        colTolExtra[basisCol[leave]] = Math.max(colTolExtra[basisCol[leave]],
                                ask * (1 + TOL_MARGIN_REL));
                        tolerated = true;
                        break ratioTest;
                    }
                    return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                            "对偶比率测试无候选(行 " + leave + ";本模型按构造恒可行,此为模型缺陷信号) "
                                    + trapDiagnostics(a, c, state, u, y, leave, xB, basisCol, need));
                }
                // 并列集取 ratio 不超过 minRatio·(1+1e-9)+1e-15 的候选, 按下标顺序做 FTRAN, 最多 64 个
                int enter = -1;
                double alphaEnter = 0;
                double maxAbsD = 0;
                double bestSteep = -1;
                double fallbackSteep = -1;
                int tieSeen = 0;
                for (int j = 0; j < n && tieSeen < 64; j++) {
                    if (state[j] == 0) {
                        continue;
                    }
                    double alpha = a.dotColumn(j, u);
                    boolean eligible = need > 0
                            ? (state[j] == 1 && alpha < -EPS_ALPHA) || (state[j] == 2 && alpha > EPS_ALPHA)
                            : (state[j] == 1 && alpha > EPS_ALPHA) || (state[j] == 2 && alpha < -EPS_ALPHA);
                    if (!eligible) {
                        continue;
                    }
                    double rc = c[j] - a.dotColumn(j, y);
                    double rcTol = DUAL_BAND * (1 + Math.abs(c[j]));
                    if (state[j] == 1 && rc < 0 && rc > -rcTol) {
                        rc = 0;
                    } else if (state[j] == 2 && rc > 0 && rc < rcTol) {
                        rc = 0;
                    }
                    double ratio = Math.abs(rc / alpha);
                    if (ratio > minRatio * (1 + 1e-9) + 1e-15) {
                        continue; // 不是并列候选, 跳过以保证对偶可行性
                    }
                    tieSeen++;
                    Arrays.fill(work, 0);
                    a.expandColumn(j, work);
                    System.arraycopy(work, 0, d, 0, m);
                    basis.ftran(d);
                    double mad = 0;
                    double norm2 = 0;
                    for (double v : d) {
                        mad = Math.max(mad, Math.abs(v));
                        norm2 += v * v;
                    }
                    double steep = Math.abs(d[leave]) / Math.sqrt(norm2);
                    if (Math.abs(d[leave]) < Math.max(Basis.PIVOT_MIN, mad * PIVOT_REL)) {
                        // 主元不达标的记入兜底并跨重试保留, 小主元产生的 eta 由紧随的强制重分解吸收
                        if (steep > fallbackSteep) {
                            fallbackSteep = steep;
                            fallbackEnter = j;
                        }
                        continue;
                    }
                    if (bland) {
                        // Bland 兜底通道取首个主元可接受的并列者
                        enter = j;
                        alphaEnter = d[leave];
                        maxAbsD = mad;
                        break;
                    }
                    if (steep > bestSteep) {
                        bestSteep = steep;
                        enter = j;
                        alphaEnter = d[leave];
                        maxAbsD = mad;
                    }
                }
                if (enter < 0) {
                    if (!refactored) {
                        try {
                            factorizeWithRepair(basis, a, basisCol, state, x, lower, rowCols, c);
                            refactored = true;
                            continue;
                        } catch (Basis.NumericException e) {
                            dumpBasisOnFailure(a, basisCol, "refactor");
                            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                    "重分解失败: " + e.getMessage());
                        }
                    }
                    // 没有任何主元达标的候选时说明处于退化角点, 强制取方向最优的兜底列换基
                    if (fallbackEnter >= 0) {
                        enter = fallbackEnter;
                        // maxAbsD 置为无穷, 让紧随其后的强制重分解吸收小主元 eta
                        maxAbsD = Double.MAX_VALUE;
                    } else {
                        // 诊断出基行的陷阱态: 候选行号、定价行量级、目标行各非基列的 α 与 rc 概览
                        String trap = trapDiagnostics(a, c, state, u, y, leave, xB, basisCol, need);
                        return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                "top-" + tieSeen + " 候选皆无主元可接受性(行 " + leave + ") " + trap);
                    }
                }
                // 选定 enter 后重算它的 FTRAN. 筛选循环结束时 d 里留的是最后一个候选的结果, 不能直接用.
                Arrays.fill(work, 0);
                a.expandColumn(enter, work);
                System.arraycopy(work, 0, d, 0, m);
                basis.ftran(d);
                alphaEnter = d[leave];
                double step = need / -alphaEnter;
                double span = upper[enter] - lower[enter];
                if (span >= LpModel.INF || Math.abs(step) <= span) {
                    // 全换基: eta 更新, 超阈值或主元过小时重分解
                    state[leaveCol] = leaveToLower ? (byte) 1 : (byte) 2;
                    x[leaveCol] = target;
                    x[enter] = (step > 0 ? lower[enter] : upper[enter]) + step;
                    basisCol[leave] = enter;
                    state[enter] = 0;
                    int nnz = 0;
                    for (double v : d) {
                        if (v != 0) {
                            nnz++;
                        }
                    }
                    int[] aIdx = new int[nnz];
                    double[] aVal = new double[nnz];
                    int q = 0;
                    for (int i = 0; i < m; i++) {
                        if (d[i] != 0) {
                            aIdx[q] = i;
                            aVal[q] = d[i];
                            q++;
                        }
                    }
                    basis.update(leave, aIdx, aVal);
                    if (basis.etaCount() > Basis.MAX_ETA
                            || Math.abs(alphaEnter) < maxAbsD * 1e-8) {
                        try {
                            factorizeWithRepair(basis, a, basisCol, state, x, lower, rowCols, c);
                        } catch (Basis.NumericException e) {
                            dumpBasisOnFailure(a, basisCol, "refactor-eta");
                            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                    "重分解失败: " + e.getMessage());
                        }
                    }
                    pivoted = true;
                    break;
                }
                // 界翻转: 进入变量翻到对侧的界, 不换基, 同一出基行继续
                double flip = Math.copySign(span, step);
                x[enter] = step > 0 ? upper[enter] : lower[enter];
                state[enter] = step > 0 ? (byte) 2 : (byte) 1;
                need += alphaEnter * flip; // 出基变量的剩余位移, 翻转贡献了 −α·flip
                for (int p = 0; p < m; p++) {
                    xB[p] -= d[p] * flip;
                }
                if (++iterations > MAX_ITER) {
                    return LpResult.failure(LpResult.Status.ITERATION_LIMIT, iterations, "界翻转循环超限");
                }
                if ((iterations & 0x3FF) == 0) {
                    if (iterations > iterBudget) {
                        return LpResult.failure(LpResult.Status.ITERATION_LIMIT, iterations,
                                "对偶单纯形超迭代预算(" + iterBudget + ",界翻转)");
                    }
                    if (fuseNanos != 0L && System.nanoTime() > fuseNanos) {
                        return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations,
                                "对偶单纯形触发墙钟紧急熔断(" + FUSE_MS + "ms,界翻转)");
                    }
                }
            }
            if (tolerated) {
                // 该出基变量的判定容差已抬高, 回到循环顶部重新 FTRAN/选行
                if (++iterations > MAX_ITER) {
                    return LpResult.failure(LpResult.Status.ITERATION_LIMIT, iterations, "定点收敛回退超迭代上限");
                }
                continue;
            }
            if (!pivoted) {
                return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, iterations, "内部逻辑异常(未换基退出)");
            }
        }
    }

    /**
     * 阶梯贪心初始基, 在原模型上调用. 反复认领"在未覆盖行中恰含一个 ±1 系数"的零成本列,
     * 按成本非零、非零元数的顺序升序扫描. 任何一行找不到零成本候选就失败.
     */
    private static boolean selectInitialBasis(LpModel model, int[] basisCol, byte[] state) {
        int m = model.a.rows;
        int n = model.a.cols;
        Integer[] order = new Integer[n];
        for (int j = 0; j < n; j++) {
            order[j] = j;
        }
        Arrays.sort(order, java.util.Comparator
                .<Integer>comparingInt(j -> Math.abs(model.cost[j]) > EPS_RC ? 1 : 0)
                .thenComparingInt(j -> model.a.nnzOf(j)));
        boolean[] covered = new boolean[m];
        int coveredCount = 0;
        boolean progress = true;
        while (coveredCount < m && progress) {
            progress = false;
            for (int jj = 0; jj < n && coveredCount < m; jj++) {
                int j = order[jj];
                if (state[j] == 0 || Math.abs(model.cost[j]) > EPS_RC) {
                    continue; // 只看零成本列, 保持起步对偶可行
                }
                int hit = -1;
                boolean ok = true;
                for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                    int i = model.a.rowIdx[p];
                    if (!covered[i]) {
                        if (hit >= 0 || Math.abs(Math.abs(model.a.values[p]) - 1.0) > 1e-9) {
                            ok = false;
                            break;
                        }
                        hit = i;
                    }
                }
                if (ok && hit >= 0) {
                    basisCol[hit] = j;
                    state[j] = 0;
                    covered[hit] = true;
                    coveredCount++;
                    progress = true;
                }
            }
        }
        return coveredCount == m;
    }

    /** 陷阱态诊断: 出基行、违反量、该行全部非基列的 α=u·A_j 与 rc 概览, 按 |α| 取前 6 大. */
    private static String trapDiagnostics(SparseMatrix a, double[] c, byte[] state, double[] u,
            double[] y, int leave, double[] xB, int[] basisCol, double need) {
        StringBuilder sb = new StringBuilder();
        sb.append("[陷阱诊断] leave=").append(leave).append(" 基列=").append(basisCol[leave])
                .append(" xB=").append(xB[leave]).append(" need=").append(need);
        double[][] top = new double[6][]; // {absAlpha, col, alpha, rc}
        int filled = 0;
        for (int j = 0; j < a.cols; j++) {
            if (state[j] == 0) {
                continue;
            }
            double alpha = a.dotColumn(j, u);
            double aa = Math.abs(alpha);
            if (filled < 6 || aa > top[5][0]) {
                double rc = c[j] - a.dotColumn(j, y);
                int pos = Math.min(filled, 5);
                while (pos > 0 && top[pos - 1][0] < aa) {
                    if (pos < 6) {
                        top[pos] = top[pos - 1];
                    }
                    pos--;
                }
                top[pos] = new double[] { aa, j, alpha, rc };
                if (filled < 6) {
                    filled++;
                }
            }
        }
        for (double[] t : top) {
            if (t != null) {
                sb.append(String.format(" 列%d α=%.3g rc=%.3g", (int) t[1], t[2], t[3]));
            }
        }
        return sb.toString();
    }

    /**
     * 重分解加奇异修补, 即教科书上的 shift on singularity. LU 在第 k 位找不到主元时,
     * 把该位的基列换成候选行上空闲的单位松弛列, 重试到分解成功为止; 候选耗尽就抛原异常.
     * 替换只改基的成员, 不影响轨迹的可行性和后续比率测试的对偶可行性.
     */
    private static void factorizeWithRepair(Basis basis, SparseMatrix a, int[] basisCol, byte[] state,
            double[] x, double[] lower, List<int[]> rowCols, double[] cost) throws Basis.NumericException {
        Basis.NumericException last = null;
        for (int attempt = 0; attempt <= basisCol.length; attempt++) {
            try {
                basis.factorize(columnsOf(a, basisCol));
                return;
            } catch (Basis.NumericException e) {
                last = e;
                int failPos = e.failPos;
                int[] cand = e.candRows;
                if (failPos < 0 || cand == null || cand.length == 0) {
                    throw e;
                }
                boolean repaired = false;
                for (int row : cand) {
                    int slack = findFreeSlack(a, state, rowCols.get(row), cost);
                    if (slack >= 0) {
                        int old = basisCol[failPos];
                        state[old] = 1;
                        x[old] = lower[old];
                        basisCol[failPos] = slack;
                        state[slack] = 0;
                        repaired = true;
                        break;
                    }
                }
                if (!repaired) {
                    throw e;
                }
            }
        }
        throw last == null ? new Basis.NumericException("修补循环异常") : last;
    }

    /** 在某一行找一个不在基中、零成本、单非零元的单位列作为松弛列, 找不到返回 -1. */
    private static int findFreeSlack(SparseMatrix a, byte[] state, int[] cols, double[] cost) {
        int fallback = -1;
        for (int j : cols) {
            if (state[j] != 0 && a.nnzOf(j) == 1) {
                if (cost[j] == 0.0) {
                    return j;
                }
                if (fallback < 0) {
                    fallback = j;
                }
            }
        }
        return fallback;
    }

    /** 基列的 SparseCol 视图列表, 按位置序. */
    private static List<Basis.SparseCol> columnsOf(SparseMatrix a, int[] basisCol) {
        List<Basis.SparseCol> cols = new ArrayList<>(basisCol.length);
        for (int j : basisCol) {
            cols.add(new CscCol(a, j));
        }
        return cols;
    }

    /**
     * 出口自检, 在原空间验证 Ax=b 且各变量在界内, 容差按行活动量级归一. 失败时返回原因.
     *
     * @param colTolExtra 定点收敛的逐列额外容差(缩放空间, 可为 null); 原空间按
     *                    {@code colTolExtra[j]*colScale[j]} 换算, 与选行口径逐位一致
     * @param colScale 出口反缩放因子, 长度与 x 相同
     * @param tolScale 求解循环的尺度噪声项(缩放空间); 原空间同样按列缩放换算,
     *                 使"循环内视为已可行"的解不会被出口以更严的口径拒收
     */
    private static String exitCheck(LpModel model, double[] x, @javax.annotation.Nullable double[] colTolExtra,
            double[] colScale, double tolScale) {
        int m = model.a.rows;
        double[] ax = new double[m];
        double[] act = new double[m];
        for (int j = 0; j < model.a.cols; j++) {
            if (x[j] == 0) {
                continue;
            }
            for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                double term = model.a.values[p] * x[j];
                ax[model.a.rowIdx[p]] += term;
                act[model.a.rowIdx[p]] += Math.abs(term);
            }
            double scaleTol = (tolScale + (colTolExtra == null ? 0 : colTolExtra[j])) * colScale[j];
            double loTol = TOL_BOUND * (1 + Math.abs(model.lower[j])) + scaleTol;
            double upTol = TOL_BOUND * (1 + Math.abs(model.upper[j])) + scaleTol;
            if (x[j] < model.lower[j] - loTol
                    || (model.upper[j] < LpModel.INF && x[j] > model.upper[j] + upTol)) {
                return "出口解越界: 列 " + j + " x=" + x[j] + " 界=[" + model.lower[j] + "," + model.upper[j]
                        + "] 容差=" + loTol + " (定点收敛项=" + scaleTol + " 列缩放=" + colScale[j] + ")";
            }
        }
        for (int i = 0; i < m; i++) {
            double scale = 1 + Math.abs(model.b[i]) + act[i];
            if (Math.abs(ax[i] - model.b[i]) > TOL_EXIT * scale) {
                StringBuilder sb = new StringBuilder("出口残差超标: 行 " + i + " |Ax-b|="
                        + Math.abs(ax[i] - model.b[i]) + " b=" + model.b[i] + " ax=" + ax[i] + " act=" + act[i]);
                // 诊断信息里列出该行非零列的解值, 最多 8 个
                int shown = 0;
                for (int j = 0; j < model.a.cols && shown < 8; j++) {
                    for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                        if (model.a.rowIdx[p] == i && x[j] != 0) {
                            sb.append(" [列").append(j).append(" a=").append(model.a.values[p])
                                    .append(" x=").append(x[j]).append(']');
                            shown++;
                            break;
                        }
                    }
                }
                return sb.toString();
            }
        }
        return null;
    }

    private static SparseMatrix copyOf(SparseMatrix a) {
        return SparseMatrix.of(a.rows, a.cols, a.colPtr.clone(), a.rowIdx.clone(), a.values.clone());
    }

    /** 基奇异时把当前基列号转储到 lp-dumps/basis-failure-<tag>.txt, 供秩分析用. */
    private static void dumpBasisOnFailure(SparseMatrix a, int[] basisCol, String tag) {
        try {
            java.io.File dir = new java.io.File("lp-dumps");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            java.io.File f = new java.io.File(dir, "basis-failure-" + tag + ".txt");
            try (java.io.PrintWriter w = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(new java.io.FileOutputStream(f), "UTF-8"))) {
                for (int j : basisCol) {
                    w.println(j);
                }
            }
        } catch (Throwable t) {
            // 诊断写盘失败就静默忽略
        }
    }

    /** CSC 列的 SparseCol 视图. */
    private static final class CscCol implements Basis.SparseCol {
        private final SparseMatrix a;
        private final int col;

        CscCol(SparseMatrix a, int col) {
            this.a = a;
            this.col = col;
        }

        @Override
        public int nnz() {
            return this.a.nnzOf(this.col);
        }

        @Override
        public int indexAt(int k) {
            return this.a.rowIdx[this.a.colPtr[this.col] + k];
        }

        @Override
        public double valueAt(int k) {
            return this.a.values[this.a.colPtr[this.col] + k];
        }
    }
}
