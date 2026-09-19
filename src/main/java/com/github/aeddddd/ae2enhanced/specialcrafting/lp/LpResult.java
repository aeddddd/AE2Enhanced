package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

/** LP 求解结果. */
public final class LpResult {

    /** 求解状态. */
    public enum Status {
        /** 最优解找到, 出口已通过可行性与最优性残差自检. */
        OPTIMAL,
        /** Phase 1 判定不可行: 人工变量和在容差之上. */
        INFEASIBLE,
        /** 数值失败(基奇异/残差超标/主元过小), 调用方按降级语义处理. */
        NUMERIC_FAILURE,
        /** 迭代数超硬上限(防御性), 理论上 Bland 规则保证终止. */
        ITERATION_LIMIT
    }

    public final Status status;
    /** 结构变量取值(仅 OPTIMAL 时非 null). */
    public final double[] x;
    /** 目标值(仅 OPTIMAL 有效). */
    public final double objective;
    /** 总迭代数(诊断埋点). */
    public final int iterations;
    /** 失败原因(诊断;OPTIMAL 时为 null). */
    @javax.annotation.Nullable
    public final String reason;

    private LpResult(Status status, double[] x, double objective, int iterations, String reason) {
        this.status = status;
        this.x = x;
        this.objective = objective;
        this.iterations = iterations;
        this.reason = reason;
    }

    public static LpResult optimal(double[] x, double objective, int iterations) {
        return new LpResult(Status.OPTIMAL, x, objective, iterations, null);
    }

    public static LpResult failure(Status status, int iterations, String reason) {
        if (status == Status.OPTIMAL) {
            throw new IllegalArgumentException("失败结果不得使用 OPTIMAL 状态");
        }
        return new LpResult(status, null, Double.NaN, iterations, reason);
    }
}
