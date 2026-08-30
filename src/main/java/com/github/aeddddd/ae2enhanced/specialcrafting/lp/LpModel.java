package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

/**
 * LP 模型（修正单纯形标准形）:
 * <pre>min c·x  s.t. A x = b, lower ≤ x ≤ upper</pre>
 * <p>不等式约束由模型构建方（SccLpModelBuilder）经松弛/赤字变量预先化为等式;
 * 无限界以 {@link #INF} 表示.</p>
 */
public final class LpModel {

    /** 无限界的表示值(远大于任何需求闭包上界). */
    public static final double INF = 1e30;

    /** 等式约束矩阵(m × n). */
    public final SparseMatrix a;
    /** RHS(长度 m;构建方负责符号规整,b ≥ 0 非必需,求解器内部预处理). */
    public final double[] b;
    /** 目标系数(长度 n). */
    public final double[] cost;
    /** 下界(长度 n,可为 -INF). */
    public final double[] lower;
    /** 上界(长度 n,可为 +INF). */
    public final double[] upper;

    public LpModel(SparseMatrix a, double[] b, double[] cost, double[] lower, double[] upper) {
        if (a.cols != cost.length || cost.length != lower.length || lower.length != upper.length
                || a.rows != b.length) {
            throw new IllegalArgumentException("LP 模型维度不一致");
        }
        this.a = a;
        this.b = b;
        this.cost = cost;
        this.lower = lower;
        this.upper = upper;
    }
}
