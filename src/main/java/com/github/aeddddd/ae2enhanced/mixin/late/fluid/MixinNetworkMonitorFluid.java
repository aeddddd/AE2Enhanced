package com.github.aeddddd.ae2enhanced.mixin.late.fluid;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import java.util.Collections;

/**
 * E2a：在 AE2 物品网络的 NetworkMonitor 中注入流体假物品.
 * 与 ae2fc 的兼容策略：priority=1100(高于 ae2fc 默认 1000),
 * 在 ae2fc 完成流体注入后,再注入我们的流体假物品(如果 ae2fc 不存在则直接注入).
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkMonitor.class, remap = false, priority = 1100)
public class MixinNetworkMonitorFluid {

    @Shadow
    private GridStorageCache myGridCache;

    @Shadow
    private IStorageChannel myChannel;

    private boolean ae2enhanced$isItemChannel() {
        return this.myChannel == AEApi.instance().storage().getStorageChannel(
                appeng.api.storage.channels.IItemStorageChannel.class);
    }

    @SuppressWarnings("unchecked")
    private IMEMonitor<IAEFluidStack> ae2enhanced$getFluidMonitor() {
        try {
            IFluidStorageChannel fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            return this.myGridCache.getInventory(fluidChannel);
        } catch (Exception e) {
            return null;
        }
    }

    @Inject(method = "getAvailableItems", at = @At("TAIL"))
    private void ae2enhanced$onGetAvailableItemsFluid(IItemList out, CallbackInfoReturnable<IItemList> cir) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (!ae2enhanced$isItemChannel()) return;

        IMEMonitor<IAEFluidStack> fluidMonitor = ae2enhanced$getFluidMonitor();
        if (fluidMonitor == null) return;

        IFluidStorageChannel fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        IItemList<IAEFluidStack> fluidList = fluidChannel.createList();
        fluidMonitor.getAvailableItems(fluidList);

        for (IAEFluidStack fluid : fluidList) {
            if (fluid == null || fluid.getStackSize() <= 0) continue;
            IAEItemStack fakeItem = FakeFluids.packFluid(fluid);
            if (fakeItem != null) {
                out.addStorage(fakeItem);
            }
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onExtractItemsFluid(IAEStack request, Actionable mode, IActionSource source,
                                                  CallbackInfoReturnable<IAEStack> cir) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (!ae2enhanced$isItemChannel()) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemFluidDrop.isFluidDrop(mcStack)) return;

        IMEMonitor<IAEFluidStack> fluidMonitor = ae2enhanced$getFluidMonitor();
        if (fluidMonitor == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEFluidStack fluidRequest = FakeFluids.unpackFluid(itemStack);
        if (fluidRequest == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEFluidStack notExtracted = fluidMonitor.extractItems(fluidRequest, mode, source);
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
            IAEItemStack result = FakeFluids.packFluid(notExtracted);
            cir.setReturnValue(result != null ? result : request);
        }
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onInjectItemsFluid(IAEStack input, Actionable mode, IActionSource source,
                                                 CallbackInfoReturnable<IAEStack> cir) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (!ae2enhanced$isItemChannel()) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemFluidDrop.isFluidDrop(mcStack)) return;

        IMEMonitor<IAEFluidStack> fluidMonitor = ae2enhanced$getFluidMonitor();
        if (fluidMonitor == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEFluidStack fluidInput = FakeFluids.unpackFluid(itemStack);
        if (fluidInput == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEFluidStack notInjected = fluidMonitor.injectItems(fluidInput, mode, source);
        long notInjectedSize = notInjected != null ? notInjected.getStackSize() : 0;
        long injectedSize = fluidInput.getStackSize() - notInjectedSize;
        if (injectedSize > 0 && mode == Actionable.MODULATE) {
            IAEItemStack diff = itemStack.copy();
            diff.setStackSize(injectedSize);
            ae2enhanced$notifyListeners(Collections.singletonList(diff), source);
        }
        if (notInjected != null && notInjected.getStackSize() > 0) {
            IAEItemStack fakeNotInjected = FakeFluids.packFluid(notInjected);
            cir.setReturnValue(fakeNotInjected != null ? fakeNotInjected : input);
        } else {
            cir.setReturnValue(null);
        }
    }

    private void ae2enhanced$notifyListeners(Iterable<IAEItemStack> diff, IActionSource source) {
        MixinReflectionHelper.notifyListenersOfChange((NetworkMonitor)(Object)this, diff, source);
    }
}
