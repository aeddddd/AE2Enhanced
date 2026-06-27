package com.github.aeddddd.ae2enhanced.mixin.late.energy;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEnergies;
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
 * E2a：GridStorageCache 实时联动——RF 能量通道变化通知物品通道.
 * 当 RF 能量网络中的存储发生实时变化时,将变化转换为假物品并 postChange 到物品 monitor,
 * 使终端自动刷新能量显示.
 *
 * <p>通道集合改为懒加载,避免在 {@link GridStorageCache} 的静态初始化阶段调用
 * {@code AEApi.instance().storage().getStorageChannel(...)} 导致初始化失败。</p>
 */
@SuppressWarnings("rawtypes")
@Mixin(value = GridStorageCache.class, remap = false)
public class MixinGridStorageCacheEnergy {

    private static Set<IStorageChannel<?>> ae2enhanced$energyChannels;

    private static boolean ae2enhanced$isEnergyChannel(IStorageChannel<?> channel) {
        if (ae2enhanced$energyChannels == null) {
            ae2enhanced$energyChannels = new HashSet<>();
            try {
                IStorageChannel<?> energyChannel = AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class);
                if (energyChannel != null) {
                    ae2enhanced$energyChannels.add(energyChannel);
                }
            } catch (Exception e) {
                // RF 能量通道不可用
            }
        }
        return ae2enhanced$energyChannels.contains(channel);
    }

    @Inject(method = "postAlterationOfStoredItems", at = @At("TAIL"))
    private void onPostAlteration(IStorageChannel<?> chan, Iterable<? extends IAEStack<?>> input, IActionSource src, CallbackInfo ci) {
        if (!ae2enhanced$isEnergyChannel(chan)) return;
        postEnergyChanges(input, src, true);
    }

    @Inject(method = "postChangesToNetwork", at = @At("TAIL"))
    private <T extends IAEStack<T>, C extends IStorageChannel<T>> void onPostChanges(C chan, int upOrDown, IItemList<T> availableItems, IActionSource src, CallbackInfo ci) {
        if (!ae2enhanced$isEnergyChannel(chan)) return;
        postEnergyChanges(availableItems, src, upOrDown > 0);
    }

    @SuppressWarnings("unchecked")
    private void postEnergyChanges(Iterable<? extends IAEStack<?>> changes, IActionSource src, boolean add) {
        if (changes == null) return;

        try {
            GridStorageCache cache = (GridStorageCache) (Object) this;
            NetworkMonitor itemMonitor = (NetworkMonitor) cache.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (itemMonitor == null) return;

            List<IAEItemStack> fakeChanges = new ArrayList<>();
            for (IAEStack<?> stack : changes) {
                if (!(stack instanceof IAEEnergyStack)) continue;
                IAEEnergyStack energy = (IAEEnergyStack) stack;
                long size = energy.getStackSize();
                if (size == 0) continue;
                IAEItemStack fake = FakeEnergies.packEnergy(energy);
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
