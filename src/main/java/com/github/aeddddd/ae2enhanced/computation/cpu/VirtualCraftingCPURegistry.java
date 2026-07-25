package com.github.aeddddd.ae2enhanced.computation.cpu;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * 虚拟 Crafting CPU 注册表.
 * <p>由 {@link com.github.aeddddd.ae2enhanced.mixin.MixinCraftingService} 读取,
 * 将虚拟集群注入到 AE2 的 CraftingService 中.</p>
 * <p>使用强引用集合,避免虚拟 CPU 池被垃圾回收.</p>
 */
public final class VirtualCraftingCPURegistry {

    private static final Set<CraftingCPUCluster> CLUSTERS = Collections
            .newSetFromMap(new ConcurrentHashMap<>());

    private VirtualCraftingCPURegistry() {
    }

    public static void register(CraftingCPUCluster cluster) {
        CLUSTERS.add(cluster);
    }

    public static void unregister(CraftingCPUCluster cluster) {
        CLUSTERS.remove(cluster);
    }

    public static Set<CraftingCPUCluster> getClusters() {
        return Collections.unmodifiableSet(CLUSTERS);
    }

    /**
     * 集群是否为本项目注册的虚拟 CPU（测试 CPU / 计算核心）.
     * 普通 CPU 与第三方 CPU（Adv/NeoECOAE）集群必然为 false.
     */
    public static boolean isOurVirtualCpu(CraftingCPUCluster cluster) {
        return cluster != null && CLUSTERS.contains(cluster);
    }
}
