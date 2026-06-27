package com.github.aeddddd.ae2enhanced.mixin.late.thaumic;

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
import com.github.aeddddd.ae2enhanced.util.reflection.EssentiaBusHelper;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEssentiaSafe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;

/**
 * E2a：在 NetworkInventoryHandler 层面拦截源质假物品.
 * 本 mixin 位于 mixins.ae2enhanced.late.thaumic.json 中,条件加载.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkInventoryHandler.class, remap = false, priority = 1100)
public class MixinNetworkInventoryHandler {

    private IMEMonitor<IAEEssentiaStack> ae2enhanced$essentiaMonitor;
    private boolean ae2enhanced$isItemChannel;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(IStorageChannel chan, SecurityCache security, CallbackInfo ci) {
        this.ae2enhanced$isItemChannel = (chan == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        if (!this.ae2enhanced$isItemChannel) return;

        try {
            IEssentiaStorageChannel essentiaChannel = AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class);
            if (security.getGrid() == null) return;
            IStorageGrid storageGrid = security.getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return;
            this.ae2enhanced$essentiaMonitor = storageGrid.getInventory(essentiaChannel);
        } catch (Exception e) {
            // 源质通道不可用
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void onExtractItems(IAEStack request, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack> cir) {
        if (!this.ae2enhanced$isItemChannel || this.ae2enhanced$essentiaMonitor == null) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        if (!FakeEssentiaSafe.isEssentiaFakeItem(itemStack.createItemStack())) return;

        IAEEssentiaStack essentiaRequest = EssentiaBusHelper.unpackEssentia(itemStack);
        if (essentiaRequest == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEEssentiaStack notExtracted = this.ae2enhanced$essentiaMonitor.extractItems(essentiaRequest, mode, src);
        long notExtractedSize = notExtracted != null ? notExtracted.getStackSize() : 0;
        if (notExtractedSize == 0) {
            IAEItemStack emptyResult = itemStack.copy();
            emptyResult.setStackSize(0);
            cir.setReturnValue(emptyResult);
        } else {
            cir.setReturnValue(request);
        }
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void onInjectItems(IAEStack input, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack> cir) {
        if (!this.ae2enhanced$isItemChannel || this.ae2enhanced$essentiaMonitor == null) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        if (!FakeEssentiaSafe.isEssentiaFakeItem(itemStack.createItemStack())) return;

        IAEEssentiaStack essentiaInput = EssentiaBusHelper.unpackEssentia(itemStack);
        if (essentiaInput == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEEssentiaStack notInjected = this.ae2enhanced$essentiaMonitor.injectItems(essentiaInput, mode, src);
        if (notInjected != null && notInjected.getStackSize() > 0) {
            IAEItemStack fakeNotInjected = EssentiaBusHelper.packEssentia(notInjected);
            cir.setReturnValue(fakeNotInjected != null ? fakeNotInjected : input);
        } else {
            cir.setReturnValue(null);
        }
    }
}
