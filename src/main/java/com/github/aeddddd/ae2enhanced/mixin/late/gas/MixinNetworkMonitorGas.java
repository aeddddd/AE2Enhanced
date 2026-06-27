package com.github.aeddddd.ae2enhanced.mixin.late.gas;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeGases;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import java.util.Collections;

/**
 * E2a：在 AE2 物品网络的 NetworkMonitor 中注入气体假物品.
 * 本 mixin 位于 mixins.ae2enhanced.late.gas.json 中,由 AssemblyMixinPlugin 条件加载.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkMonitor.class, remap = false, priority = 1099)
public class MixinNetworkMonitorGas {

    @Shadow
    private GridStorageCache myGridCache;

    @Shadow
    private IStorageChannel myChannel;

    private boolean ae2enhanced$isItemChannel() {
        return this.myChannel == AEApi.instance().storage().getStorageChannel(
                appeng.api.storage.channels.IItemStorageChannel.class);
    }

    @SuppressWarnings("unchecked")
    private IMEMonitor<IAEGasStack> ae2enhanced$getGasMonitor() {
        try {
            IGasStorageChannel gasChannel = AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class);
            return this.myGridCache.getInventory(gasChannel);
        } catch (Exception e) {
            return null;
        }
    }

    @Inject(method = "getAvailableItems", at = @At("TAIL"))
    private void ae2enhanced$onGetAvailableItemsGas(IItemList out, CallbackInfoReturnable<IItemList> cir) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (!ae2enhanced$isItemChannel()) return;

        IMEMonitor<IAEGasStack> gasMonitor = ae2enhanced$getGasMonitor();
        if (gasMonitor == null) return;

        IGasStorageChannel gasChannel = AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class);
        IItemList<IAEGasStack> gasList = gasChannel.createList();
        gasMonitor.getAvailableItems(gasList);

        for (IAEGasStack gas : gasList) {
            if (gas == null || gas.getStackSize() <= 0) continue;
            IAEItemStack fakeItem = FakeGases.packGas(gas);
            if (fakeItem != null) {
                out.addStorage(fakeItem);
            }
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onExtractItemsGas(IAEStack request, Actionable mode, IActionSource source,
                                                CallbackInfoReturnable<IAEStack> cir) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (!ae2enhanced$isItemChannel()) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemGasDrop.isGasDrop(mcStack)) return;

        IMEMonitor<IAEGasStack> gasMonitor = ae2enhanced$getGasMonitor();
        if (gasMonitor == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEGasStack gasRequest = FakeGases.unpackGas(itemStack);
        if (gasRequest == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEGasStack notExtracted = gasMonitor.extractItems(gasRequest, mode, source);
        long notExtractedSize = notExtracted != null ? notExtracted.getStackSize() : 0;
        long extractedSize = itemStack.getStackSize() - notExtractedSize;
        if (extractedSize > 0 && mode == Actionable.MODULATE) {
            IAEItemStack diff = itemStack.copy();
            diff.setStackSize(-extractedSize);
            ae2enhanced$notifyListeners(Collections.singletonList(diff), source);
        }
        if (notExtractedSize == 0) {
            IAEItemStack emptyResult = itemStack.copy();
            emptyResult.setStackSize(0);
            cir.setReturnValue(emptyResult);
        } else {
            IAEItemStack result = FakeGases.packGas(notExtracted);
            cir.setReturnValue(result != null ? result : request);
        }
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onInjectItemsGas(IAEStack input, Actionable mode, IActionSource source,
                                               CallbackInfoReturnable<IAEStack> cir) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (!ae2enhanced$isItemChannel()) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemGasDrop.isGasDrop(mcStack)) return;

        IMEMonitor<IAEGasStack> gasMonitor = ae2enhanced$getGasMonitor();
        if (gasMonitor == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEGasStack gasInput = FakeGases.unpackGas(itemStack);
        if (gasInput == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEGasStack notInjected = gasMonitor.injectItems(gasInput, mode, source);
        long notInjectedSize = notInjected != null ? notInjected.getStackSize() : 0;
        long injectedSize = gasInput.getStackSize() - notInjectedSize;
        if (injectedSize > 0 && mode == Actionable.MODULATE) {
            IAEItemStack diff = itemStack.copy();
            diff.setStackSize(injectedSize);
            ae2enhanced$notifyListeners(Collections.singletonList(diff), source);
        }
        if (notInjected != null && notInjected.getStackSize() > 0) {
            IAEItemStack fakeNotInjected = FakeGases.packGas(notInjected);
            cir.setReturnValue(fakeNotInjected != null ? fakeNotInjected : input);
        } else {
            cir.setReturnValue(null);
        }
    }

    private void ae2enhanced$notifyListeners(Iterable<IAEItemStack> diff, IActionSource source) {
        MixinReflectionHelper.notifyListenersOfChange((NetworkMonitor)(Object)this, diff, source);
    }
}
