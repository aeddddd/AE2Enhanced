package com.github.aeddddd.ae2enhanced.mixin.late.mana;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.item.ItemManaDrop;
import com.github.aeddddd.ae2enhanced.storage.mana.AEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IAEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeMana;
import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;

/**
 * 在 AE2 物品网络的 NetworkMonitor 中注入 Mana 假物品.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkMonitor.class, remap = false)
public class MixinNetworkMonitorMana {

    @Shadow
    private GridStorageCache myGridCache;

    @Shadow
    private IStorageChannel myChannel;

    private boolean ae2enhanced$isItemChannel() {
        return this.myChannel == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @SuppressWarnings("unchecked")
    private IMEMonitor<IAEManaStack> ae2enhanced$getManaMonitor() {
        try {
            IManaStorageChannel manaChannel = AEApi.instance().storage().getStorageChannel(IManaStorageChannel.class);
            return this.myGridCache.getInventory(manaChannel);
        } catch (Exception e) {
            return null;
        }
    }

    @Inject(method = "getAvailableItems", at = @At("TAIL"))
    private void ae2enhanced$onGetAvailableItemsMana(IItemList out, CallbackInfoReturnable<IItemList> cir) {
        if (!ae2enhanced$isItemChannel()) return;

        IMEMonitor<IAEManaStack> manaMonitor = ae2enhanced$getManaMonitor();
        if (manaMonitor == null) return;

        IManaStorageChannel manaChannel = AEApi.instance().storage().getStorageChannel(IManaStorageChannel.class);
        IItemList<IAEManaStack> manaList = manaChannel.createList();
        manaMonitor.getAvailableItems(manaList);

        for (IAEManaStack mana : manaList) {
            if (mana == null || mana.getStackSize() <= 0) continue;
            IAEItemStack fakeItem = FakeMana.packMana(mana);
            if (fakeItem != null) {
                out.addStorage(fakeItem);
            }
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onExtractItemsMana(IAEStack request, Actionable mode, IActionSource source,
                                                 CallbackInfoReturnable<IAEStack> cir) {
        if (!ae2enhanced$isItemChannel()) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemManaDrop.isManaDrop(mcStack)) return;

        // E2a：Mana 假物品仅用于终端展示,禁止作为真实物品被取出.
        // 与 RF 假物品保持一致,玩家不能从 ME 终端将其拿到背包.
        cir.setReturnValue(request);
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onInjectItemsMana(IAEStack input, Actionable mode, IActionSource source,
                                                CallbackInfoReturnable<IAEStack> cir) {
        if (!ae2enhanced$isItemChannel()) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemManaDrop.isManaDrop(mcStack)) return;

        IMEMonitor<IAEManaStack> manaMonitor = ae2enhanced$getManaMonitor();
        if (manaMonitor == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEManaStack manaInput = AEManaStack.create(itemStack.getStackSize());
        IAEManaStack notInjected = manaMonitor.injectItems(manaInput, mode, source);
        long notInjectedSize = notInjected != null ? notInjected.getStackSize() : 0;
        long injectedSize = itemStack.getStackSize() - notInjectedSize;

        if (injectedSize > 0 && mode == Actionable.MODULATE) {
            IAEItemStack diff = itemStack.copy();
            diff.setStackSize(injectedSize);
            ae2enhanced$notifyListeners(Collections.singletonList(diff), source);
        }

        if (notInjected != null && notInjected.getStackSize() > 0) {
            IAEItemStack result = itemStack.copy();
            result.setStackSize(notInjectedSize);
            cir.setReturnValue(result);
        } else {
            cir.setReturnValue(null);
        }
    }

    private void ae2enhanced$notifyListeners(Iterable<IAEItemStack> diff, IActionSource source) {
        MixinReflectionHelper.notifyListenersOfChange((NetworkMonitor)(Object)this, diff, source);
    }
}
