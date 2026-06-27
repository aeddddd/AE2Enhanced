package com.github.aeddddd.ae2enhanced.mixin.late.gas;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeGases;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * E2a：GridStorageCache 实时联动——气体通道变化通知物品通道.
 * 本 mixin 位于 mixins.ae2enhanced.late.gas.json 中,条件加载.
 *
 * <p>通道集合改为懒加载,避免在 {@link GridStorageCache} 的静态初始化阶段调用
 * {@code AEApi.instance().storage().getStorageChannel(...)} 导致初始化失败。</p>
 */
@SuppressWarnings("rawtypes")
@Mixin(value = GridStorageCache.class, remap = false)
public class MixinGridStorageCacheGas {

    private static Set<IStorageChannel<?>> ae2enhanced$gasChannels;

    private static boolean ae2enhanced$isGasChannel(IStorageChannel<?> channel) {
        if (ae2enhanced$gasChannels == null) {
            ae2enhanced$gasChannels = new HashSet<>();
            try {
                IStorageChannel<?> gasChannel = AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class);
                if (gasChannel != null) {
                    ae2enhanced$gasChannels.add(gasChannel);
                }
            } catch (Exception e) {
                // 气体通道不可用
            }
        }
        return ae2enhanced$gasChannels.contains(channel);
    }

    @Inject(method = "postAlterationOfStoredItems", at = @At("TAIL"))
    private void onPostAlterationGas(IStorageChannel<?> chan, Iterable<? extends IAEStack<?>> input, IActionSource src, CallbackInfo ci) {
        if (!ae2enhanced$isGasChannel(chan)) return;
        postGasChanges(input, src);
    }

    @Inject(method = "postChangesToNetwork", at = @At("TAIL"))
    private <T extends IAEStack<T>, C extends IStorageChannel<T>> void onPostChangesGas(C chan, int upOrDown, IItemList<T> availableItems, IActionSource src, CallbackInfo ci) {
        if (!ae2enhanced$isGasChannel(chan)) return;
        postGasChanges(availableItems, src);
    }

    @SuppressWarnings("unchecked")
    private void postGasChanges(Iterable<? extends IAEStack<?>> changes, IActionSource src) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (changes == null) return;

        try {
            GridStorageCache cache = (GridStorageCache) (Object) this;
            NetworkMonitor itemMonitor = (NetworkMonitor) cache.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (itemMonitor == null) return;

            List<IAEItemStack> fakeChanges = new ArrayList<>();
            for (IAEStack<?> stack : changes) {
                if (!(stack instanceof IAEGasStack)) continue;
                IAEGasStack gas = (IAEGasStack) stack;
                long size = gas.getStackSize();
                if (size == 0) continue;
                IAEItemStack fake = FakeGases.packGas(gas);
                if (fake != null) {
                    fakeChanges.add(fake);
                }
            }

            if (fakeChanges.isEmpty()) return;

            MixinReflectionHelper.postChange(itemMonitor, true, fakeChanges, src);
        } catch (Exception e) {
            // 反射调用失败,静默处理
        }
    }
}
