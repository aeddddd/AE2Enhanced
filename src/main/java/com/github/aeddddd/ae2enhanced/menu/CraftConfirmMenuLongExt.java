package com.github.aeddddd.ae2enhanced.menu;

import appeng.api.stacks.AEKey;
import appeng.api.networking.crafting.CalculationStrategy;

/**
 * 由 MixinCraftConfirmMenu 添加到 CraftConfirmMenu 的 long 型计划提交入口.
 */
public interface CraftConfirmMenuLongExt {

    /**
     * 以 long 数量执行与 {@code CraftConfirmMenu.planJob(AEKey, int, CalculationStrategy)}
     * 相同的流程（取消旧任务 -> 调用 beginCraftingCalculation）.
     */
    boolean ae2e$planJobLong(AEKey what, long amount, CalculationStrategy strategy);
}
