package com.github.aeddddd.ae2enhanced.mixin.late.energy;

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
import com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEnergies;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import java.util.Collections;

/**
 * E2a：在 AE2 物品网络的 NetworkMonitor 中注入 RF 能量假物品.
 * priority = 1100,确保在其他 mixin 之后注入.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkMonitor.class, remap = false, priority = 1100)
public class MixinNetworkMonitorEnergy {

    @Shadow
    private GridStorageCache myGridCache;

    @Shadow
    private IStorageChannel myChannel;

    private boolean ae2enhanced$isItemChannel() {
        return this.myChannel == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @SuppressWarnings("unchecked")
    private IMEMonitor<IAEEnergyStack> ae2enhanced$getEnergyMonitor() {
        try {
            IEnergyStorageChannel energyChannel = AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class);
            return this.myGridCache.getInventory(energyChannel);
        } catch (Exception e) {
            return null;
        }
    }

    @Inject(method = "getAvailableItems", at = @At("TAIL"))
    private void ae2enhanced$onGetAvailableItemsEnergy(IItemList out, CallbackInfoReturnable<IItemList> cir) {
        if (!ae2enhanced$isItemChannel()) return;

        IMEMonitor<IAEEnergyStack> energyMonitor = ae2enhanced$getEnergyMonitor();
        if (energyMonitor == null) return;

        IEnergyStorageChannel energyChannel = AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class);
        IItemList<IAEEnergyStack> energyList = energyChannel.createList();
        energyMonitor.getAvailableItems(energyList);

        for (IAEEnergyStack energy : energyList) {
            if (energy == null || energy.getStackSize() <= 0) continue;
            IAEItemStack fakeItem = FakeEnergies.packEnergy(energy);
            if (fakeItem != null) {
                out.addStorage(fakeItem);
            }
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onExtractItemsEnergy(IAEStack request, Actionable mode, IActionSource source,
                                                   CallbackInfoReturnable<IAEStack> cir) {
        if (!ae2enhanced$isItemChannel()) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemEnergyDrop.isEnergyDrop(mcStack)) return;

        IMEMonitor<IAEEnergyStack> energyMonitor = ae2enhanced$getEnergyMonitor();
        if (energyMonitor == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEEnergyStack energyRequest = AEEnergyStack.create(itemStack.getStackSize());
        IAEEnergyStack notExtracted = energyMonitor.extractItems(energyRequest, mode, source);
        long notExtractedSize = notExtracted != null ? notExtracted.getStackSize() : 0;
        long extractedSize = itemStack.getStackSize() - notExtractedSize;

        if (extractedSize > 0 && mode == Actionable.MODULATE) {
            IAEItemStack diff = itemStack.copy();
            diff.setStackSize(-extractedSize);
            ae2enhanced$notifyListeners(Collections.singletonList(diff), source);
        }

        if (notExtractedSize == 0) {
            cir.setReturnValue(null);
        } else {
            IAEItemStack result = itemStack.copy();
            result.setStackSize(notExtractedSize);
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onInjectItemsEnergy(IAEStack input, Actionable mode, IActionSource source,
                                                  CallbackInfoReturnable<IAEStack> cir) {
        if (!ae2enhanced$isItemChannel()) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemEnergyDrop.isEnergyDrop(mcStack)) return;

        IMEMonitor<IAEEnergyStack> energyMonitor = ae2enhanced$getEnergyMonitor();
        if (energyMonitor == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEEnergyStack energyInput = AEEnergyStack.create(itemStack.getStackSize());
        IAEEnergyStack notInjected = energyMonitor.injectItems(energyInput, mode, source);
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
