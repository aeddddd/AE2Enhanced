package com.github.aeddddd.ae2enhanced.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link appeng.crafting.execution.ExecutingCraftingJob.TaskProgress} 字段访问器。
 * <p>目标类为包级私有，因此通过字符串 target 指定，并由调用方以 {@link Object}
 * 形式接收实例后强转使用。</p>
 */
@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface TaskProgressAccessor {
    @Accessor("value")
    long getValue();

    @Accessor("value")
    void setValue(long value);
}
