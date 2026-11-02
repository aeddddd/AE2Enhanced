package com.github.aeddddd.ae2enhanced.crafting.access.mixin;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * CraftingCPUCluster 私有字段写入接口.
 * 供 TileComputationCore 创建虚拟集群时初始化 machineSrc / availableStorage / accelerator / myName.
 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public interface ICraftingCPUClusterAccessor {

    @Accessor("machineSrc")
    void ae2e$setMachineSrc(MachineSource src);

    @Accessor("availableStorage")
    void ae2e$setAvailableStorage(long value);

    @Accessor("accelerator")
    void ae2e$setAccelerator(int value);

    @Accessor("myName")
    void ae2e$setMyName(String name);
}
