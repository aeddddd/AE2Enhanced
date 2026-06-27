package com.github.aeddddd.ae2enhanced.mixin.late.fluid;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.SecurityCache;
import appeng.me.storage.NetworkInventoryHandler;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * E2a：在 NetworkInventoryHandler 层面拦截流体假物品.
 * 本 mixin 位于 mixins.ae2enhanced.late.json 中,无条件加载.
 *
 * NetworkMonitor 在更外层拦截了大部分操作,但某些内部逻辑可能直接调用
 * NetworkInventoryHandler.此处的 HEAD 拦截确保假物品不会被当作真实物品处理.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkInventoryHandler.class, remap = false, priority = 1100)
public class MixinNetworkInventoryHandlerFluid {

    private IMEMonitor<IAEFluidStack> ae2enhanced$fluidMonitor;
    private boolean ae2enhanced$isItemChannel;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(IStorageChannel chan, SecurityCache security, CallbackInfo ci) {
        this.ae2enhanced$isItemChannel = (chan == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        if (!this.ae2enhanced$isItemChannel) return;

        try {
            IFluidStorageChannel fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            if (security.getGrid() == null) return;
            IStorageGrid storageGrid = security.getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return;
            this.ae2enhanced$fluidMonitor = storageGrid.getInventory(fluidChannel);
        } catch (Exception e) {
            // 流体通道不可用
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void onExtractItems(IAEStack request, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack> cir) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (!this.ae2enhanced$isItemChannel || this.ae2enhanced$fluidMonitor == null) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        if (!ItemFluidDrop.isFluidDrop(itemStack.createItemStack())) return;

        IAEFluidStack fluidRequest = FakeFluids.unpackFluid(itemStack);
        if (fluidRequest == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEFluidStack notExtracted = this.ae2enhanced$fluidMonitor.extractItems(fluidRequest, mode, src);
        long notExtractedSize = notExtracted != null ? notExtracted.getStackSize() : 0;
        if (notExtractedSize == 0) {
            IAEItemStack emptyResult = itemStack.copy();
            emptyResult.setStackSize(0);
            cir.setReturnValue(emptyResult);
        } else {
            IAEItemStack result = FakeFluids.packFluid(notExtracted);
            cir.setReturnValue(result != null ? result : request);
        }
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void onInjectItems(IAEStack input, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack> cir) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (!this.ae2enhanced$isItemChannel || this.ae2enhanced$fluidMonitor == null) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        if (!ItemFluidDrop.isFluidDrop(itemStack.createItemStack())) return;

        IAEFluidStack fluidInput = FakeFluids.unpackFluid(itemStack);
        if (fluidInput == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEFluidStack notInjected = this.ae2enhanced$fluidMonitor.injectItems(fluidInput, mode, src);
        if (notInjected != null && notInjected.getStackSize() > 0) {
            IAEItemStack fakeNotInjected = FakeFluids.packFluid(notInjected);
            cir.setReturnValue(fakeNotInjected != null ? fakeNotInjected : input);
        } else {
            cir.setReturnValue(null);
        }
    }
}
