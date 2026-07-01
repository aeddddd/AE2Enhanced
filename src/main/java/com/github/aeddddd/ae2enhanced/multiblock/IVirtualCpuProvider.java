package com.github.aeddddd.ae2enhanced.multiblock;

import appeng.api.networking.IGridNodeService;

/**
 * 标记一个网格节点可向 AE2 提供虚拟 Crafting CPU 的服务。
 * <p>由 {@link MultiblockMeInterfaceBlockEntity} 暴露，供 Phase 3 的
 * {@code CraftingService} Mixin 发现并注入虚拟 CPU 池。</p>
 */
public interface IVirtualCpuProvider extends IGridNodeService {

    /**
     * @return 当前节点是否已绑定到计算核心控制器并可提供虚拟 CPU。
     */
    default boolean isVirtualCpuAvailable() {
        return false;
    }

    /**
     * @return 当前控制器提供的并行上限；若未绑定计算核心则返回 0。
     */
    default int getVirtualCpuParallelLimit() {
        return 0;
    }
}
