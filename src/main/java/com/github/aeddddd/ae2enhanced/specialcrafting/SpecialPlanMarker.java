package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import appeng.api.networking.crafting.ICraftingPlan;

/**
 * 特殊计划标记.
 * <p>由 {@link SpecialCraftingCalculation} 在产出特殊计划时登记,路由点 B
 * （submitJob mixin）据此把计划独占路由到本项目虚拟 CPU.使用以计划对象身份为键的
 * 同步 WeakHashMap,不修改/包装原生 {@code CraftingPlan} record,计划被回收后自动清理.</p>
 */
public final class SpecialPlanMarker {

    private static final Map<ICraftingPlan, Boolean> SPECIAL_PLANS = Collections
            .synchronizedMap(new WeakHashMap<>());

    private SpecialPlanMarker() {
    }

    public static void mark(ICraftingPlan plan) {
        SPECIAL_PLANS.put(plan, Boolean.TRUE);
    }

    public static boolean isSpecial(ICraftingPlan plan) {
        return plan != null && SPECIAL_PLANS.containsKey(plan);
    }
}
