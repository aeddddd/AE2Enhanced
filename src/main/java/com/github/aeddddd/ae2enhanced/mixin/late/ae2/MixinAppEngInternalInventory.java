package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.github.aeddddd.ae2enhanced.util.inv.ChannelReceiverCardFilterWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 {@link AppEngInternalInventory#setFilter}，自动包装原 filter，使频道接收卡能够放入。
 * 覆盖所有使用 {@link AppEngInternalInventory} 并设置自定义 filter 的升级槽
 * （如 CELLS 的 PartSubnetProxyFront、CustomCellUpgrades 等）。
 */
@Mixin(value = AppEngInternalInventory.class, remap = false)
public class MixinAppEngInternalInventory {

    @Shadow
    protected IAEItemFilter filter;

    @Inject(method = "setFilter", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$wrapFilter(IAEItemFilter filter, CallbackInfo ci) {
        if (filter != null && !(filter instanceof ChannelReceiverCardFilterWrapper)) {
            this.filter = new ChannelReceiverCardFilterWrapper(filter);
        } else {
            this.filter = filter;
        }
        ci.cancel();
    }
}
