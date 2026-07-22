package com.github.aeddddd.ae2enhanced.mixin;

import appeng.core.localization.Tooltips;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 AE2 15.3.4 {@code Tooltips.BYTE_NUMS} 的上游 bug：
 * 数组第 4 项误写为 1024^3（重复）,本应为 1024^4,
 * 导致 {@code getByteAmount} 对任何 >= 1000 GiB 的数值必然数组越界崩溃
 * （CPU 选择列表 tooltip 的 ArrayIndexOutOfBoundsException: Index 4）.
 * <p>此处修正该错误并扩展至 2^60,配合已有的 6 档单位（k/M/G/T/P/E）,
 * 使任意 long 值（包括 Long.MAX_VALUE 级别的虚拟 CPU 存储）都能安全格式化.</p>
 */
@Mixin(value = Tooltips.class, remap = false)
public class MixinTooltips {

    @Shadow
    @Final
    @Mutable
    public static long[] BYTE_NUMS;

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void ae2e$fixByteNums(CallbackInfo ci) {
        BYTE_NUMS = new long[] { 1L << 10, 1L << 20, 1L << 30, 1L << 40, 1L << 50, 1L << 60 };
    }
}
