package com.github.aeddddd.ae2enhanced.mixin.accessor;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link CraftingCPUCluster} 方法调用器，替代运行时反射调用包级私有方法。
 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public interface CraftingCPUClusterInvoker {
    @Invoker("addBlockEntity")
    void invokeAddBlockEntity(CraftingBlockEntity entity);
}
