package com.github.aeddddd.ae2enhanced.mixin.compat.neoecoae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * NeoECOAE 自带 {@code ExecutingCraftingJob$TaskProgress} 字段访问器.
 * <p>目标类为第三方可选依赖且包级私有,通过字符串 target 指定,
 * 由调用方以 {@link Object} 形式接收实例后强转使用.</p>
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob$TaskProgress", remap = false)
public interface EcoTaskProgressAccessor {
    @Accessor("value")
    long getValue();

    @Accessor("value")
    void setValue(long value);
}
