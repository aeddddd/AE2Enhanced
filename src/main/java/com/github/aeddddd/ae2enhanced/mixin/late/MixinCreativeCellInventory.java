package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.storage.CreativeCellInventory;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 修改创造型ME存储元件的显示/可用数量上限。
 *
 * <p>AE2 原版硬编码为 {@link Integer#MAX_VALUE}，导致大规模虚拟合成时
 * crafting plan 阶段看到的可用量被限制在 2^31-1，无法支撑 Int.MAX_VALUE
 * 级别的八重压缩圆石等深层配方链。</p>
 *
 * <p>参考：GTNH Applied-Energistics-2-Unofficial PR#708</p>
 */
@Mixin(CreativeCellInventory.class)
public abstract class MixinCreativeCellInventory {

    @WrapOperation(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEItemStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;",
            remap = false
        )
    )
    private IAEStack<IAEItemStack> ae2e$setStackSize(
            IAEItemStack instance,
            long stackSize,
            Operation<IAEStack<IAEItemStack>> original
    ) {
        return original.call(instance, Long.MAX_VALUE);
    }
}
