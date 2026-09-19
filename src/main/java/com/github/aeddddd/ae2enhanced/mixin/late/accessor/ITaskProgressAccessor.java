package com.github.aeddddd.ae2enhanced.mixin.late.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * CraftingCPUCluster.TaskProgress (私有静态内部类) 的 value 字段访问接口.
 * 调用方持有 Object 引用, 经 (ITaskProgressAccessor) 强转后读写.
 */
@Mixin(targets = "appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress", remap = false)
public interface ITaskProgressAccessor {

    @Accessor("value")
    long ae2e$getValue();

    @Accessor("value")
    void ae2e$setValue(long value);
}
