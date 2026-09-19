package com.github.aeddddd.ae2enhanced.mixin.bridge;

import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;

/**
 * CraftingCPUCluster 与 TileComputationCore 的关联接口.
 * 由 MixinCraftingCPUCluster 实现, 替代原先对 mixin 注入字段的反射写入.
 */
public interface IComputationCoreAccess {

    void ae2enhanced$setComputationCore(TileComputationCore core);

    TileComputationCore ae2enhanced$getComputationCore();
}
