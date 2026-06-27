package com.github.aeddddd.ae2enhanced.mixin.late.starlight;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.storage.starlight.IAEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeStarlight;
import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GridStorageCache 实时联动——Starlight 通道变化通知物品通道.
 *
 * <p>通道集合改为懒加载,避免在 {@link GridStorageCache} 的静态初始化阶段调用
 * {@code AEApi.instance().storage().getStorageChannel(...)} 导致初始化失败。</p>
 */
@SuppressWarnings("rawtypes")
@Mixin(value = GridStorageCache.class, remap = false)
public class MixinGridStorageCacheStarlight {

    private static Set<IStorageChannel<?>> ae2enhanced$starlightChannels;

    private static boolean ae2enhanced$isStarlightChannel(IStorageChannel<?> channel) {
        if (ae2enhanced$starlightChannels == null) {
            ae2enhanced$starlightChannels = new HashSet<>();
            try {
                IStorageChannel<?> starlightChannel = AEApi.instance().storage().getStorageChannel(IStarlightStorageChannel.class);
                if (starlightChannel != null) {
                    ae2enhanced$starlightChannels.add(starlightChannel);
                }
            } catch (Exception e) {
                // Starlight 通道不可用
            }
        }
        return ae2enhanced$starlightChannels.contains(channel);
    }

    @Inject(method = "postAlterationOfStoredItems", at = @At("TAIL"))
    private void onPostAlteration(IStorageChannel<?> chan, Iterable<? extends IAEStack<?>> input, IActionSource src, CallbackInfo ci) {
        if (!ae2enhanced$isStarlightChannel(chan)) return;
        postStarlightChanges(input, src, true);
    }

    @Inject(method = "postChangesToNetwork", at = @At("TAIL"))
    private <T extends IAEStack<T>, C extends IStorageChannel<T>> void onPostChanges(C chan, int upOrDown, IItemList<T> availableItems, IActionSource src, CallbackInfo ci) {
        if (!ae2enhanced$isStarlightChannel(chan)) return;
        postStarlightChanges(availableItems, src, upOrDown > 0);
    }

    @SuppressWarnings("unchecked")
    private void postStarlightChanges(Iterable<? extends IAEStack<?>> changes, IActionSource src, boolean add) {
        if (changes == null) return;

        try {
            GridStorageCache cache = (GridStorageCache) (Object) this;
            NetworkMonitor itemMonitor = (NetworkMonitor) cache.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (itemMonitor == null) return;

            List<IAEItemStack> fakeChanges = new ArrayList<>();
            for (IAEStack<?> stack : changes) {
                if (!(stack instanceof IAEStarlightStack)) continue;
                IAEStarlightStack starlight = (IAEStarlightStack) stack;
                long size = starlight.getStackSize();
                if (size == 0) continue;
                IAEItemStack fake = FakeStarlight.packStarlight(starlight);
                if (fake != null) {
                    fakeChanges.add(fake);
                }
            }

            if (fakeChanges.isEmpty()) return;

            MixinReflectionHelper.postChange(itemMonitor, add, fakeChanges, src);
        } catch (Exception e) {
            // 反射调用失败,静默处理
        }
    }
}
