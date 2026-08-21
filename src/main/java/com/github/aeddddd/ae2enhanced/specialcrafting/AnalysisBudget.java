package com.github.aeddddd.ae2enhanced.specialcrafting;

/**
 * 环分析时间预算：防止大 SCC 网络的零空间求解（O(n³) 大整数）失控.
 * <p>两个场景:
 * <ul>
 * <li><b>detector</b>（服务器线程,{@code beginCraftingJob} 同步判定）:预算取配置项
 * {@code crafting.specialDetectorBudgetMs}（默认 1s),超时保守漏判（该请求不路由
 * 特殊求解器,回落 DAG/原生，行为与现状一致）——服务器看门狗的硬保护;</li>
 * <li><b>求解侧</b>({@code solveCycle}/{@code solveInto},计算线程）:固定
 * {@link #SOLVE_BUDGET_MS};DAG 单趟执行的所有循环边界共享同一预算（总开销封顶）,
 * 超时按不可解回落（整单回落原生，与既有 FALLBACK 语义一致）.</li>
 * </ul></p>
 */
public final class AnalysisBudget {

    /** 求解侧（计算线程）总分析预算：10s（用户确认的固定值）. */
    public static final long SOLVE_BUDGET_MS = 10_000L;

    private final long deadlineNanos;

    private AnalysisBudget(long millis) {
        this.deadlineNanos = System.nanoTime() + millis * 1_000_000L;
    }

    public static AnalysisBudget ofMillis(long millis) {
        return new AnalysisBudget(Math.max(1L, millis));
    }

    /** 求解侧预算（{@link #SOLVE_BUDGET_MS}). */
    public static AnalysisBudget solve() {
        return new AnalysisBudget(SOLVE_BUDGET_MS);
    }

    public boolean expired() {
        return System.nanoTime() - this.deadlineNanos >= 0;
    }
}
