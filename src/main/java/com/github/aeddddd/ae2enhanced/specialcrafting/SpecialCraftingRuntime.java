package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;

/**
 * 特殊配方功能运行时状态.
 * <p>持有两类状态:</p>
 * <ul>
 * <li>功能开关:由配置文件给出初始值,{@code /ae2e specialcrafting} 指令运行时切换.
 * 关闭时路由层完全放行原生行为（计算/提交/执行零干预）.</li>
 * <li>特殊 job 集群标记:特殊计划提交到计算核心集群时登记,执行层门控
 * （SelfRefOutputGate / RoundQuotaScheduler）仅对被标记的集群生效,
 * 普通 job 与普通 CPU 零影响.job 完成/取消时解除标记.</li>
 * </ul>
 */
public final class SpecialCraftingRuntime {

    /** 正在执行特殊 job 的 CPU 集群（弱键,集群回收自动清理）. */
    private static final Map<CraftingCPUCluster, Boolean> SPECIAL_CLUSTERS = Collections
            .synchronizedMap(new WeakHashMap<>());

    private SpecialCraftingRuntime() {
    }

    /**
     * 特殊配方功能是否启用（配置 + 运行时开关,二者同一字段）.
     */
    public static boolean isEnabled() {
        return AE2EnhancedConfig.crafting.specialCrafting;
    }

    public static void setEnabled(boolean enabled) {
        AE2EnhancedConfig.crafting.specialCrafting = enabled;
    }

    public static void tagCluster(CraftingCPUCluster cluster) {
        SPECIAL_CLUSTERS.put(cluster, Boolean.TRUE);
        // CraftingCPUCluster 是 final:instanceof/强转必须经 Object 绕开编译期不可能性判定
        // (mixin 运行时织入接口,测试环境未织入则跳过字段写入)
        if ((Object) cluster instanceof com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialClusterMarkAccess) {
            ((com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialClusterMarkAccess) (Object) cluster)
                    .ae2e$setSpecialMarked(true);
        }
    }

    public static void untagCluster(CraftingCPUCluster cluster) {
        SPECIAL_CLUSTERS.remove(cluster);
        if ((Object) cluster instanceof com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialClusterMarkAccess) {
            ((com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialClusterMarkAccess) (Object) cluster)
                    .ae2e$setSpecialMarked(false);
        }
    }

    public static boolean isSpecialCluster(CraftingCPUCluster cluster) {
        if (cluster == null) {
            return false;
        }
        // 字段级快路径(mixin 生效时零锁零哈希);测试环境回落 WeakHashMap
        if ((Object) cluster instanceof com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialClusterMarkAccess) {
            return ((com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialClusterMarkAccess) (Object) cluster)
                    .ae2e$isSpecialMarked();
        }
        return SPECIAL_CLUSTERS.containsKey(cluster);
    }
}
