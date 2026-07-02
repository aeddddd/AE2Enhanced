package com.github.aeddddd.ae2enhanced.computation.cpu;

import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;

/**
 * 标记被超因果计算核心托管的虚拟 AE2 Crafting CPU 集群。
 */
public interface IVirtualCraftingCPU {

    /**
     * 设置该虚拟集群的宿主控制器。
     */
    void ae2enhanced$setHost(ComputationCoreBlockEntity host);

    /**
     * @return 宿主控制器，若不存在则返回 null。
     */
    ComputationCoreBlockEntity ae2enhanced$getHost();

    /**
     * @return 是否为虚拟集群（由超因果计算核心托管）。
     */
    boolean ae2enhanced$isVirtual();
}
