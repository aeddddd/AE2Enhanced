package com.github.aeddddd.ae2enhanced.mixin.late.gas;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.SecurityCache;
import appeng.me.storage.NetworkInventoryHandler;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeGases;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * E2a：在 NetworkInventoryHandler 层面拦截气体假物品.
 * 本 mixin 位于 mixins.ae2enhanced.late.gas.json 中,条件加载.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkInventoryHandler.class, remap = false, priority = 1099)
public class MixinNetworkInventoryHandlerGas {

    private IMEMonitor<IAEGasStack> ae2enhanced$gasMonitor;
    private boolean ae2enhanced$isItemChannel;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(IStorageChannel chan, SecurityCache security, CallbackInfo ci) {
        this.ae2enhanced$isItemChannel = (chan == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        if (!this.ae2enhanced$isItemChannel) return;

        try {
            IGasStorageChannel gasChannel = AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class);
            if (security.getGrid() == null) return;
            IStorageGrid storageGrid = security.getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return;
            this.ae2enhanced$gasMonitor = storageGrid.getInventory(gasChannel);
        } catch (Exception e) {
            // 气体通道不可用
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void onExtractItems(IAEStack request, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack> cir) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (!this.ae2enhanced$isItemChannel || this.ae2enhanced$gasMonitor == null) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        if (!ItemGasDrop.isGasDrop(itemStack.createItemStack())) return;

        IAEGasStack gasRequest = FakeGases.unpackGas(itemStack);
        if (gasRequest == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEGasStack notExtracted = this.ae2enhanced$gasMonitor.extractItems(gasRequest, mode, src);
        long notExtractedSize = notExtracted != null ? notExtracted.getStackSize() : 0;
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
    private void onInjectItems(IAEStack input, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack> cir) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (!this.ae2enhanced$isItemChannel || this.ae2enhanced$gasMonitor == null) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        if (!ItemGasDrop.isGasDrop(itemStack.createItemStack())) return;

        IAEGasStack gasInput = FakeGases.unpackGas(itemStack);
        if (gasInput == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEGasStack notInjected = this.ae2enhanced$gasMonitor.injectItems(gasInput, mode, src);
        if (notInjected != null && notInjected.getStackSize() > 0) {
            IAEItemStack fakeNotInjected = FakeGases.packGas(notInjected);
            cir.setReturnValue(fakeNotInjected != null ? fakeNotInjected : input);
        } else {
            cir.setReturnValue(null);
        }
    }
}
