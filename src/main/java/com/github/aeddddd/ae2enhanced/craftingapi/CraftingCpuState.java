package com.github.aeddddd.ae2enhanced.craftingapi;

import appeng.api.networking.crafting.ICraftingPatternDetails;

import java.util.Map;

/** 特殊计划与 CPU 执行状态之间的中性视图。 */
public interface CraftingCpuState {

    boolean specialMarked();
    void specialMarked(boolean value);
    Map<ICraftingPatternDetails, Long> specialTotals();
    void specialTotals(Map<ICraftingPatternDetails, Long> value);
    Object specialQuota();
    void specialQuota(Object value);
    void clearSpecialPlan();
    long lastStuckWarning();
    void lastStuckWarning(long value);
}
