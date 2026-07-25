package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.computation.cpu.IVirtualCraftingCPU;
import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;

/**
 * 特殊计划的 CPU 路由决策（纯函数,便于单元测试,mixin 只做转发）.
 */
public final class RoutingDecision {

    private RoutingDecision() {
    }

    /**
     * 目标 CPU 是否为本项目虚拟 CPU（测试 CPU / 超因果计算核心）.
     */
    public static boolean isOurVirtualCpu(ICraftingCPU cpu) {
        return cpu instanceof IVirtualCraftingCPU
                || cpu instanceof CraftingCPUCluster cluster && VirtualCraftingCPURegistry.getClusters()
                        .contains(cluster);
    }
}
