package com.github.aeddddd.ae2enhanced.craftingapi;

/** 原生回落计算预算的中性访问端口。 */
public interface CraftingJobBudgetAccess {

    long ae2enhanced$nativeCalcDeadlineNanos();
    void ae2enhanced$armNativeCalcBudget(long deadlineNanos);
    void ae2enhanced$markNativeCalcAborted();
    boolean ae2enhanced$nativeCalcAborted();
}
