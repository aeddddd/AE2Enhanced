package com.github.aeddddd.ae2enhanced.mixin.late.thaumic;

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
import com.github.aeddddd.ae2enhanced.util.reflection.EssentiaBusHelper;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEssentiaSafe;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics.api.storage.IAEEssentiaStack;

import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import java.util.Collections;

/**
 * E2a：在 AE2 物品网络的 NetworkMonitor 中注入源质假物品.
 * 与 ae2fc 的兼容策略：priority=1100(高于 ae2fc 默认 1000),
 * 在 ae2fc 完成流体/气体注入后,再注入源质假物品.
 * extractItems / injectItems 使用 HEAD + cancellable 优先拦截我们的假物品,
 * 非我们的假物品则放行给 ae2fc 或原始逻辑处理.
 *
 * 注意：本 mixin 位于 mixins.ae2enhanced.late.thaumic.json 中,
 * 由 ThaumicMixinPlugin 条件加载.流体/气体逻辑在独立的 Mixin 中.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkMonitor.class, remap = false, priority = 1100)
public class MixinNetworkMonitor {

    @Shadow
    private GridStorageCache myGridCache;

    @Shadow
    private IStorageChannel myChannel;

    private boolean ae2enhanced$isItemChannel() {
        return this.myChannel == AEApi.instance().storage().getStorageChannel(
                appeng.api.storage.channels.IItemStorageChannel.class);
    }

    @SuppressWarnings("unchecked")
    private IMEMonitor<IAEEssentiaStack> ae2enhanced$getEssentiaMonitor() {
        try {
            IStorageChannel<IAEEssentiaStack> essentiaChannel = AEApi.instance().storage()
                    .getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
            return this.myGridCache.getInventory(essentiaChannel);
        } catch (Exception e) {
            // Thaumic Energistics 通道不可用
        }
        return null;
    }

    @Inject(method = "getAvailableItems", at = @At("TAIL"))
    private void ae2enhanced$onGetAvailableItems(IItemList out, CallbackInfoReturnable<IItemList> cir) {
        if (!ae2enhanced$isItemChannel()) return;

        IMEMonitor<IAEEssentiaStack> essentiaMonitor = ae2enhanced$getEssentiaMonitor();
        if (essentiaMonitor == null) {
            return;
        }

        IStorageChannel<IAEEssentiaStack> essentiaChannel = AEApi.instance().storage()
                .getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
        IItemList<IAEEssentiaStack> essentiaList = essentiaChannel.createList();
        essentiaMonitor.getAvailableItems(essentiaList);

        int added = 0;
        for (IAEEssentiaStack essentia : essentiaList) {
            if (essentia == null || essentia.getStackSize() <= 0) continue;
            IAEItemStack fakeItem = EssentiaBusHelper.packEssentia(essentia);
            if (fakeItem != null) {
                out.addStorage(fakeItem);
                added++;
            }
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onExtractItems(IAEStack request, Actionable mode, IActionSource source,
                                            CallbackInfoReturnable<IAEStack> cir) {
        if (!ae2enhanced$isItemChannel()) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        ItemStack mcStack = itemStack.createItemStack();
        if (!FakeEssentiaSafe.isEssentiaFakeItem(mcStack)) return;

        IMEMonitor<IAEEssentiaStack> essentiaMonitor = ae2enhanced$getEssentiaMonitor();
        if (essentiaMonitor == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEEssentiaStack essentiaRequest = EssentiaBusHelper.unpackEssentia(itemStack);
        if (essentiaRequest == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEEssentiaStack notExtracted = essentiaMonitor.extractItems(essentiaRequest, mode, source);
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
            cir.setReturnValue(request);
        }
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onInjectItems(IAEStack input, Actionable mode, IActionSource source,
                                           CallbackInfoReturnable<IAEStack> cir) {
        if (!ae2enhanced$isItemChannel()) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        if (!FakeEssentiaSafe.isEssentiaFakeItem(itemStack.createItemStack())) return;

        IMEMonitor<IAEEssentiaStack> essentiaMonitor = ae2enhanced$getEssentiaMonitor();
        if (essentiaMonitor == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEEssentiaStack essentiaInput = EssentiaBusHelper.unpackEssentia(itemStack);
        if (essentiaInput == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEEssentiaStack notInjected = essentiaMonitor.injectItems(essentiaInput, mode, source);
        long notInjectedSize = notInjected != null ? notInjected.getStackSize() : 0;
        long injectedSize = essentiaInput.getStackSize() - notInjectedSize;
        if (injectedSize > 0 && mode == Actionable.MODULATE) {
            IAEItemStack diff = itemStack.copy();
            diff.setStackSize(injectedSize);
            ae2enhanced$notifyListeners(Collections.singletonList(diff), source);
        }
        if (notInjected != null && notInjected.getStackSize() > 0) {
            IAEItemStack fakeNotInjected = EssentiaBusHelper.packEssentia(notInjected);
            cir.setReturnValue(fakeNotInjected);
        } else {
            cir.setReturnValue(null);
        }
    }

    private void ae2enhanced$notifyListeners(Iterable<IAEItemStack> diff, IActionSource source) {
        MixinReflectionHelper.notifyListenersOfChange((NetworkMonitor)(Object)this, diff, source);
    }
}
