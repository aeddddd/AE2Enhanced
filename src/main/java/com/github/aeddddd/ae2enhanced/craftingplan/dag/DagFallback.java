package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import appeng.api.storage.data.IAEItemStack;

/**
 * DAG 规划器主动回落信号:携带机器可读原因(诊断与测试断言用).
 * 任何不确定语义都通过本异常整单回落原生,宁可慢不可错.
 */
public class DagFallback extends Exception {

    public final String reason;
    /** 不可解边界键集合(供"环盲重编译"批量降级;非边界回落为空). */
    public final java.util.Set<IAEItemStack> blindKeys;

    public DagFallback(String reason) {
        this(reason, java.util.Collections.emptySet());
    }

    public DagFallback(String reason, java.util.Set<IAEItemStack> blindKeys) {
        super(reason, null, false, false);
        this.reason = reason;
        this.blindKeys = blindKeys;
    }
}
