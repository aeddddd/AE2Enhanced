package com.github.aeddddd.ae2enhanced.mixin.late.tii;

import appeng.api.networking.IGridCache;
import appeng.api.networking.storage.IStorageGrid;
import appeng.me.Grid;
import appeng.me.cache.GridStorageCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TII 1.0 错将实现类当作缓存接口键；映射到 AE2 实际注册的 IStorageGrid 键。 */
@Mixin(value = Grid.class, remap = false)
public class MixinGridTiiCompat {
    @Inject(method = "getCache", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$mapTiiStorageCacheKey(Class<? extends IGridCache> iface,
            CallbackInfoReturnable<IGridCache> cir) {
        if (iface == GridStorageCache.class) {
            IStorageGrid storage = ((Grid) (Object) this).getCache(IStorageGrid.class);
            cir.setReturnValue(storage);
        }
    }
}
