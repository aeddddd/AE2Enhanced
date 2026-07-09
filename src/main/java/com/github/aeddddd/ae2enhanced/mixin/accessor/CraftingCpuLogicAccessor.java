package com.github.aeddddd.ae2enhanced.mixin.accessor;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link CraftingCpuLogic} 字段访问器，替代运行时反射。
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public interface CraftingCpuLogicAccessor {
    @Accessor("job")
    ExecutingCraftingJob getJob();

    @Accessor("cluster")
    CraftingCPUCluster getCluster();
}
