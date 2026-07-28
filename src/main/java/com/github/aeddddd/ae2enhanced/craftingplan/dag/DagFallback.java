package com.github.aeddddd.ae2enhanced.craftingplan.dag;

/**
 * DAG 规划器主动回落信号:携带机器可读原因(诊断与测试断言用).
 * 任何不确定语义都通过本异常整单回落原生,宁可慢不可错.
 */
public class DagFallback extends Exception {

    public final String reason;

    public DagFallback(String reason) {
        super(reason, null, false, false);
        this.reason = reason;
    }
}
