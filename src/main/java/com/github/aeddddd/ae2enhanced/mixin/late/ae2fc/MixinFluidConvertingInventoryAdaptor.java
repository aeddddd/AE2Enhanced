package com.github.aeddddd.ae2enhanced.mixin.late.ae2fc;

import appeng.util.InventoryAdaptor;
import com.glodblock.github.inventory.FluidConvertingInventoryAdaptor;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 缓存 FluidConvertingInventoryAdaptor.wrap 的结果.
 *
 * <p>ae2fc 的 MixinDualityInterface 将 AE2 接口 pushPattern/isBusy/pushItemsOut
 * 中的适配器获取全部重定向到 wrap,而 wrap 每次新建实例（spark 采样 ~5.8%）。
 * 适配器无运行时状态,按 (world, 目标位置, 朝向) 缓存并逐项校验派生输入,
 * 详见 {@link FluidAdaptorCache}。同时覆盖 PartFluidExportBus.getHandler 路径。</p>
 */
@Mixin(value = FluidConvertingInventoryAdaptor.class, remap = false)
public abstract class MixinFluidConvertingInventoryAdaptor {

    @Inject(method = "wrap", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ae2e$cachedWrap(ICapabilityProvider capProvider, EnumFacing face,
            CallbackInfoReturnable<InventoryAdaptor> cir) {
        InventoryAdaptor cached = FluidAdaptorCache.get(capProvider, face);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "wrap", at = @At("RETURN"), require = 0)
    private static void ae2e$cacheWrapResult(ICapabilityProvider capProvider, EnumFacing face,
            CallbackInfoReturnable<InventoryAdaptor> cir) {
        FluidAdaptorCache.put(capProvider, face, cir.getReturnValue());
    }
}
