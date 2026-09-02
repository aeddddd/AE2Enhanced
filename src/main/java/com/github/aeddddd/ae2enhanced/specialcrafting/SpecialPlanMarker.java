package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import appeng.api.networking.crafting.ICraftingJob;

/**
 * 特殊计划标记（移植自 1.20.1,ICraftingPlan 适配为 ICraftingJob）.
 * <p>由 LP 计划 job（{@link LpCraftingJob}）在产出特殊计划时登记,路由点 B
 * （submitJob mixin）据此把计划独占路由到超因果计算核心的虚拟 CPU 集群.
 * 使用以 job 对象身份为键的同步 WeakHashMap,job 被回收后自动清理.</p>
 */
public final class SpecialPlanMarker {

    private static final Map<ICraftingJob, Boolean> SPECIAL_JOBS = Collections
            .synchronizedMap(new WeakHashMap<>());

    private SpecialPlanMarker() {
    }

    public static void mark(ICraftingJob job) {
        SPECIAL_JOBS.put(job, Boolean.TRUE);
    }

    public static boolean isSpecial(ICraftingJob job) {
        return job != null && SPECIAL_JOBS.containsKey(job);
    }
}
