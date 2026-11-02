package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuState;
import com.github.aeddddd.ae2enhanced.craftingapi.SpecialClusterMarkAccess;

/**
 * 特殊配方功能运行时状态.
 * <p>功能开关由配置文件给出初始值,{@code /ae2e specialcrafting} 指令可运行时切换;
 * 关闭时路由层完全放行原生行为(计算/提交/执行零干预).特殊 job 提交到计算核心
 * 集群时登记集群标记,执行层门控(SelfRefOutputGate / RoundQuotaScheduler)
 * 仅对被标记的集群生效,job 完成/取消时解除标记.</p>
 */
public final class SpecialCraftingRuntime {

    private SpecialCraftingRuntime() {
    }

    /**
     * 特殊配方功能是否启用（配置 + 运行时开关,二者同一字段）.
     */
    public static boolean isEnabled() {
        return ServerConfig.current().crafting.specialCrafting;
    }

    public static void tagCluster(CraftingCPUCluster cluster) {
        if ((Object) cluster instanceof SpecialClusterMarkAccess) {
            ((SpecialClusterMarkAccess) (Object) cluster).ae2e$setSpecialMarked(true);
        }
    }

    public static void untagCluster(CraftingCPUCluster cluster) {
        if ((Object) cluster instanceof SpecialClusterMarkAccess) {
            ((SpecialClusterMarkAccess) (Object) cluster).ae2e$setSpecialMarked(false);
        }
    }

    public static boolean isSpecialCluster(CraftingCPUCluster cluster) {
        return cluster != null && (Object) cluster instanceof SpecialClusterMarkAccess
                && ((SpecialClusterMarkAccess) (Object) cluster).ae2e$isSpecialMarked();
    }

    public static boolean isSpecialCluster(CraftingCpuState context) {
        return context != null && context.specialMarked();
    }
}
