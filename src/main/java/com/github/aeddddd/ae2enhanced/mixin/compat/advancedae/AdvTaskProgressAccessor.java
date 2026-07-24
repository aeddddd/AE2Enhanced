package com.github.aeddddd.ae2enhanced.mixin.compat.advancedae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * AdvancedAE 自带 {@code ExecutingCraftingJob$TaskProgress} 的 value 字段访问器.
 * <p>目标类为第三方可选依赖且为包私有嵌套类,通过字符串 target 指定.</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress", remap = false)
public interface AdvTaskProgressAccessor {
    @Accessor("value")
    long getValue();

    @Accessor("value")
    void setValue(long value);
}
