package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 精确有理数字典单纯形(exact rational dictionary simplex, Chvátal 式).
 *
 * <p>求解 {@code min c·x, A x = b, lower ≤ x ≤ upper} 的<b>精确</b>实数(有理数)
 * 最优解:全部算术用 BigInteger 分子/分母、每步 GCD 约分,不存在浮点病态/秩亏/
 * 尾差——小模型的答案做到逐位可信。规模封顶({@link #MAX_ROWS}×{@link #MAX_COLS},
 * 有理数位数封顶 {@value #MAX_BITS} bits):超限返回 NUMERIC_FAILURE,由调用方
 * 回落浮点求解器(精确路径只服务小单元)。</p>
 *
 * <p>实现要点:等式行拆成一对 ≤ 行、有限上界各加一行,字典单纯形(表格直接
 * 存约分有理数),Bland 规则(进基/出基均取下标最小决胜)保证有限终止;
 * b 含负值时教科书辅助问题(人工变量 + 最小模行换入)求初始可行基。
 * 整数化不在此层:实数最优解交给 FlowReconciler 整数化重演,不做分支定界。</p>
 */
public final class ExactSimplex {

    /** 精确路径的行数/列数结构上限(超出自动回落浮点).
     * 实测:90 键单元的 BigInteger 字典求解会超出规划器单单元预算——
     * 精确路径只服务真正的小环(几十键以内),中型单元给浮点对偶单纯形. */
    private static final int MAX_ROWS = Integer.getInteger("ae2e.exactMaxRows", 64);
    private static final int MAX_COLS = Integer.getInteger("ae2e.exactMaxCols", 256);
    /** 有理数位数上限(分子/分母任一超限即 Limit → NUMERIC_FAILURE). */
    private static final int MAX_BITS = Integer.getInteger("ae2e.exactMaxBits", 4096);
    /** 换基主元上限(防御;Bland 规则下有限终止,上限仅防病态长尾). */
    private static final int MAX_PIVOTS = Integer.getInteger("ae2e.exactMaxPivots", 65536);
    /** 墙钟紧急熔断(ms,默认 5 分钟):P-D 起终止判据由主元数驱动,墙钟只在极端抢占下兜底. 0 = 关闭. */
    private static final long FUSE_MS = Long.getLong("ae2e.exactFuseMs", 300_000L);

    private ExactSimplex() {
    }

    /** 模型是否落入精确路径的结构上限. */
    public static boolean fits(LpModel model) {
        return model.a.rows <= MAX_ROWS && model.a.cols <= MAX_COLS;
    }

    /** 求解.返回 OPTIMAL(精确)/INFEASIBLE(精确证明)/NUMERIC_FAILURE(超限). */
    public static LpResult solve(LpModel model) {
        long fuseNanos = FUSE_MS > 0 ? System.nanoTime() + FUSE_MS * 1_000_000L : 0L;
        int n = model.a.cols;

        // 下界归零:x = x' + lo,b' = b − A·lo
        double[] lo = model.lower;
        boolean anyLower = false;
        for (double v : lo) {
            if (v != 0) {
                anyLower = true;
                break;
            }
        }
        Rational[] bShifted = new Rational[model.a.rows];
        for (int i = 0; i < model.a.rows; i++) {
            bShifted[i] = Rational.of(model.b[i]);
        }
        if (anyLower) {
            for (int j = 0; j < n; j++) {
                if (lo[j] == 0) {
                    continue;
                }
                Rational lj = Rational.of(lo[j]);
                for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                    int i = model.a.rowIdx[p];
                    bShifted[i] = bShifted[i].subtract(Rational.of(model.a.values[p]).multiply(lj));
                }
            }
        }

        // 约束装配:每等式行 → 一对 ≤ 行;每个有限上界 → 一行
        List<Rational[]> rows = new ArrayList<>();
        for (int i = 0; i < model.a.rows; i++) {
            Rational[] pos = new Rational[n];
            Arrays.fill(pos, Rational.ZERO);
            rows.add(pos);
            Rational[] neg = new Rational[n];
            Arrays.fill(neg, Rational.ZERO);
            rows.add(neg);
        }
        for (int j = 0; j < n; j++) {
            for (int p = model.a.colPtr[j]; p < model.a.colPtr[j + 1]; p++) {
                int i = model.a.rowIdx[p];
                Rational v = Rational.of(model.a.values[p]);
                rows.get(2 * i)[j] = v;
                rows.get(2 * i + 1)[j] = v.negate();
            }
        }
        Rational[] bounds = new Rational[rows.size()];
        for (int i = 0; i < model.a.rows; i++) {
            bounds[2 * i] = bShifted[i];
            bounds[2 * i + 1] = bShifted[i].negate();
        }
        for (int j = 0; j < n; j++) {
            if (model.upper[j] < LpModel.INF) {
                Rational[] row = new Rational[n];
                Arrays.fill(row, Rational.ZERO);
                row[j] = Rational.ONE;
                rows.add(row);
                Rational[] nb = new Rational[rows.size()];
                System.arraycopy(bounds, 0, nb, 0, bounds.length);
                bounds = nb;
                bounds[rows.size() - 1] = Rational.of(model.upper[j]);
            }
        }

        Rational[] cost = new Rational[n];
        for (int j = 0; j < n; j++) {
            cost[j] = Rational.of(model.cost[j]);
        }

        Tableau tableau;
        try {
            tableau = new Tableau(rows, bounds, cost, fuseNanos);
        } catch (Limit e) {
            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, 0, "精确求解装配超限: " + e.getMessage());
        }
        Rational[] vector;
        try {
            boolean feasible = tableau.solve();
            if (!feasible) {
                // 精确不可行证明(本模型按构造恒可行,出现即模型缺陷)
                return LpResult.failure(LpResult.Status.INFEASIBLE, tableau.pivots, "精确求解判定不可行");
            }
            vector = tableau.readVector();
        } catch (Limit e) {
            return LpResult.failure(LpResult.Status.NUMERIC_FAILURE, tableau.pivots,
                    "精确求解超限: " + e.getMessage());
        }
        double[] x = new double[n];
        for (int j = 0; j < n; j++) {
            x[j] = vector[j].toDouble();
        }
        // 下界平移还原
        if (anyLower) {
            for (int j = 0; j < n; j++) {
                x[j] += model.lower[j];
            }
        }
        double obj = 0;
        for (int j = 0; j < n; j++) {
            obj += model.cost[j] * x[j];
        }
        return LpResult.optimal(x, obj, tableau.pivots);
    }

    /** 资源/位数超限标记. */
    private static final class Limit extends RuntimeException {
        Limit(String message) {
            super(message, null, false, false);
        }
    }

    /** 精确有理数(BigInteger 分子/分母,每步 GCD 约分;位数超限抛 {@link Limit}). */
    private static final class Rational implements Comparable<Rational> {
        static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
        static final Rational ONE = new Rational(BigInteger.ONE, BigInteger.ONE);

        final BigInteger num;
        final BigInteger den;

        Rational(BigInteger num, BigInteger den) {
            if (den.signum() == 0) {
                throw new ArithmeticException("零分母");
            }
            if (den.signum() < 0) {
                num = num.negate();
                den = den.negate();
            }
            BigInteger g = num.gcd(den);
            num = num.divide(g);
            den = den.divide(g);
            if (num.bitLength() > MAX_BITS || den.bitLength() > MAX_BITS) {
                throw new Limit("有理数位数超 " + MAX_BITS + " bits");
            }
            this.num = num;
            this.den = den;
        }

        /** double 的精确有理数还原(double 本身是二进有理数). */
        static Rational of(double v) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                throw new Limit("非有限值进入精确路径: " + v);
            }
            long bits = Double.doubleToLongBits(v);
            long mantissa = bits & 0xFFFFFFFFFFFFFL;
            int exp = (int) ((bits >>> 52) & 0x7FF);
            boolean neg = bits < 0;
            BigInteger n;
            int shift;
            if (exp == 0) {
                n = BigInteger.valueOf(mantissa);
                shift = -1074;
            } else {
                n = BigInteger.valueOf(mantissa | (1L << 52));
                shift = exp - 1075;
            }
            if (neg) {
                n = n.negate();
            }
            if (shift >= 0) {
                return new Rational(n.shiftLeft(shift), BigInteger.ONE);
            }
            return new Rational(n, BigInteger.ONE.shiftLeft(-shift));
        }

        static Rational of(BigInteger v) {
            return new Rational(v, BigInteger.ONE);
        }

        int signum() {
            return num.signum();
        }

        Rational negate() {
            return new Rational(num.negate(), den);
        }

        Rational add(Rational o) {
            return new Rational(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den));
        }

        Rational subtract(Rational o) {
            return add(o.negate());
        }

        Rational multiply(Rational o) {
            return new Rational(num.multiply(o.num), den.multiply(o.den));
        }

        Rational divide(Rational o) {
            return new Rational(num.multiply(o.den), den.multiply(o.num));
        }

        double toDouble() {
            return num.doubleValue() / den.doubleValue();
        }

        @Override
        public int compareTo(Rational o) {
            return num.multiply(o.den).compareTo(o.num.multiply(den));
        }
    }

    /**
     * 字典单纯形表格(Chvátal dictionary;行 = ≤ 约束,列 = 变量 + 人工列 n + 右端列 n+1).
     * 字典语义:基变量 x_B = 右端 − Σ 系数·非基变量;目标行存字典约化成本的相反数
     * (进基取目标行负系数列 = min 方向的改善列).Bland 规则保证有限终止.
     */
    private static final class Tableau {
        final int m;
        final int n;
        final int[] basic;
        final int[] nonbasic;
        final Rational[][] d;
        final long fuseNanos;
        int pivots;

        Tableau(List<Rational[]> rows, Rational[] bounds, Rational[] cost, long fuseNanos) {
            this.m = rows.size();
            this.n = cost.length;
            this.fuseNanos = fuseNanos;
            this.basic = new int[m];
            this.nonbasic = new int[n + 1];
            this.d = new Rational[m + 2][n + 2];
            for (Rational[] row : d) {
                Arrays.fill(row, Rational.ZERO);
            }
            for (int i = 0; i < m; i++) {
                Rational[] coef = rows.get(i);
                for (int j = 0; j < n; j++) {
                    d[i][j] = coef[j];
                }
                basic[i] = n + i; // 行松弛为初始基(人工列索引 n 保留给辅助问题)
                d[i][n] = Rational.ONE.negate(); // 人工变量列(辅助问题用)
                d[i][n + 1] = bounds[i];
            }
            for (int j = 0; j < n; j++) {
                nonbasic[j] = j;
                d[m][j] = cost[j]; // 目标行 = min c·x 的字典约定
            }
            nonbasic[n] = -1; // 人工列标记
            d[m + 1][n] = Rational.ONE; // 辅助目标:max −x0(行 m+1)
        }

        /** Chvátal 字典换基:以 d[row][column] 为主元. */
        void pivot(int row, int column) {
            // P-D: 终止判据 = 主元数(确定性);墙钟只剩紧急熔断(正常负载下不触发)
            if (++pivots > MAX_PIVOTS) {
                throw new Limit("主元数超 " + MAX_PIVOTS);
            }
            if (fuseNanos != 0L && System.nanoTime() > fuseNanos) {
                throw new Limit("精确求解触发墙钟紧急熔断(" + FUSE_MS + "ms)");
            }
            Rational inverse = Rational.ONE.divide(d[row][column]);
            for (int i = 0; i < m + 2; i++) {
                if (i == row || d[i][column].signum() == 0) {
                    continue;
                }
                Rational factor = d[i][column].multiply(inverse);
                for (int j = 0; j < n + 2; j++) {
                    if (j != column) {
                        d[i][j] = d[i][j].subtract(d[row][j].multiply(factor));
                    }
                }
            }
            for (int j = 0; j < n + 2; j++) {
                if (j != column) {
                    d[row][j] = d[row][j].multiply(inverse);
                }
            }
            for (int i = 0; i < m + 2; i++) {
                if (i != row) {
                    d[i][column] = d[i][column].multiply(inverse).negate();
                }
            }
            d[row][column] = inverse;
            int previous = basic[row];
            basic[row] = nonbasic[column];
            nonbasic[column] = previous;
        }

        /** 单纯形:Bland 规则(进基/出基均取下标最小决胜,保证有限终止). */
        boolean simplex(int phase) {
            int objective = phase == 1 ? m + 1 : m;
            while (true) {
                int entering = -1;
                for (int j = 0; j <= n; j++) {
                    if (phase == 2 && nonbasic[j] == -1 || d[objective][j].signum() >= 0) {
                        continue;
                    }
                    if (entering < 0 || nonbasic[j] < nonbasic[entering]) {
                        entering = j;
                    }
                }
                if (entering < 0) {
                    return true;
                }
                int leaving = -1;
                for (int i = 0; i < m; i++) {
                    if (d[i][entering].signum() <= 0) {
                        continue;
                    }
                    int comparison = leaving < 0 ? -1
                            : d[i][n + 1].divide(d[i][entering])
                                    .compareTo(d[leaving][n + 1].divide(d[leaving][entering]));
                    if (comparison < 0 || comparison == 0 && basic[i] < basic[leaving]) {
                        leaving = i;
                    }
                }
                if (leaving < 0) {
                    return false; // 无界(本模型按构造有界,防御)
                }
                pivot(leaving, entering);
            }
        }

        /** 求解:必要时先跑辅助问题(人工变量)取得可行基,再解原目标.
         * 返回 false = 精确不可行(证明);true = 最优,解经 readVector 读取. */
        boolean solve() {
            int row = -1;
            for (int i = 0; i < m; i++) {
                if (row < 0 || d[i][n + 1].compareTo(d[row][n + 1]) < 0) {
                    row = i;
                }
            }
            if (row >= 0 && d[row][n + 1].signum() < 0) {
                pivot(row, n); // 最负右端行换入人工变量(教科书辅助问题启动)
                if (!simplex(1)) {
                    throw new Limit("辅助问题无界(按构造不会发生)");
                }
                if (d[m + 1][n + 1].signum() < 0) {
                    return false; // 精确不可行(证明)
                }
                if (d[m + 1][n + 1].signum() != 0) {
                    throw new Limit("辅助问题目标非零异常");
                }
                // 人工变量离基(仍在基时按行换出)
                for (int i = 0; i < m; i++) {
                    if (basic[i] != -1) {
                        continue;
                    }
                    int column = -1;
                    for (int j = 0; j <= n; j++) {
                        if (d[i][j].signum() != 0 && (column < 0 || nonbasic[j] < nonbasic[column])) {
                            column = j;
                        }
                    }
                    if (column >= 0) {
                        pivot(i, column);
                    }
                }
            }
            if (!simplex(2)) {
                throw new Limit("阶段二无界(按构造不会发生)");
            }
            return true;
        }

        /** 读取当前字典的解向量(null = 已在 solve 判精确不可行). */
        Rational[] readVector() {
            Rational[] vector = new Rational[n];
            Arrays.fill(vector, Rational.ZERO);
            for (int i = 0; i < m; i++) {
                if (basic[i] >= 0 && basic[i] < n) {
                    vector[basic[i]] = d[i][n + 1];
                }
            }
            return vector;
        }
    }
}
