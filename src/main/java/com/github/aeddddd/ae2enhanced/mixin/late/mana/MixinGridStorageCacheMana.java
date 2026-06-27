package com.github.aeddddd.ae2enhanced.mixin.late.mana;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.storage.mana.IAEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeMana;
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
 * GridStorageCache 实时联动——Mana 通道变化通知物品通道.
 *
 * <p>通道集合改为懒加载,避免在 {@link GridStorageCache} 的静态初始化阶段调用
 * {@code AEApi.instance().storage().getStorageChannel(...)} 导致初始化失败。</p>
 */
@SuppressWarnings("rawtypes")
@Mixin(value = GridStorageCache.class, remap = false)
public class MixinGridStorageCacheMana {

    private static Set<IStorageChannel<?>> ae2enhanced$manaChannels;

    private static boolean ae2enhanced$isManaChannel(IStorageChannel<?> channel) {
        if (ae2enhanced$manaChannels == null) {
            ae2enhanced$manaChannels = new HashSet<>();
            try {
                IStorageChannel<?> manaChannel = AEApi.instance().storage().getStorageChannel(IManaStorageChannel.class);
                if (manaChannel != null) {
                    ae2enhanced$manaChannels.add(manaChannel);
                }
            } catch (Exception e) {
                // Mana 通道不可用
            }
        }
        return ae2enhanced$manaChannels.contains(channel);
    }

    @Inject(method = "postAlterationOfStoredItems", at = @At("TAIL"))
    private void onPostAlteration(IStorageChannel<?> chan, Iterable<? extends IAEStack<?>> input, IActionSource src, CallbackInfo ci) {
        if (!ae2enhanced$isManaChannel(chan)) return;
        postManaChanges(input, src, true);
    }

    @Inject(method = "postChangesToNetwork", at = @At("TAIL"))
    private <T extends IAEStack<T>, C extends IStorageChannel<T>> void onPostChanges(C chan, int upOrDown, IItemList<T> availableItems, IActionSource src, CallbackInfo ci) {
        if (!ae2enhanced$isManaChannel(chan)) return;
        postManaChanges(availableItems, src, upOrDown > 0);
    }

    @SuppressWarnings("unchecked")
    private void postManaChanges(Iterable<? extends IAEStack<?>> changes, IActionSource src, boolean add) {
        if (changes == null) return;

        try {
            GridStorageCache cache = (GridStorageCache) (Object) this;
            NetworkMonitor itemMonitor = (NetworkMonitor) cache.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (itemMonitor == null) return;

            List<IAEItemStack> fakeChanges = new ArrayList<>();
            for (IAEStack<?> stack : changes) {
                if (!(stack instanceof IAEManaStack)) continue;
                IAEManaStack mana = (IAEManaStack) stack;
                long size = mana.getStackSize();
                if (size == 0) continue;
                IAEItemStack fake = FakeMana.packMana(mana);
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
