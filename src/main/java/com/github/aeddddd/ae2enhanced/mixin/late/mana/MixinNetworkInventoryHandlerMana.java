package com.github.aeddddd.ae2enhanced.mixin.late.mana;

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
import com.github.aeddddd.ae2enhanced.item.ItemManaDrop;
import com.github.aeddddd.ae2enhanced.storage.mana.AEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IAEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * E2a：在 NetworkInventoryHandler 层面拦截 Mana 假物品.
 * 本 mixin 位于 mixins.ae2enhanced.late.json 中,无条件加载.
 *
 * NetworkMonitor 在更外层拦截了大部分操作,但某些内部逻辑可能直接调用
 * NetworkInventoryHandler.此处的 HEAD 拦截确保 Mana 假物品不会被当作真实物品取出.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkInventoryHandler.class, remap = false, priority = 1100)
public class MixinNetworkInventoryHandlerMana {

    private IMEMonitor<IAEManaStack> ae2enhanced$manaMonitor;
    private boolean ae2enhanced$isItemChannel;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(IStorageChannel chan, SecurityCache security, CallbackInfo ci) {
        this.ae2enhanced$isItemChannel = (chan == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        if (!this.ae2enhanced$isItemChannel) return;

        try {
            IManaStorageChannel manaChannel = AEApi.instance().storage().getStorageChannel(IManaStorageChannel.class);
            if (security.getGrid() == null) return;
            IStorageGrid storageGrid = security.getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return;
            this.ae2enhanced$manaMonitor = storageGrid.getInventory(manaChannel);
        } catch (Exception e) {
            // Mana 通道不可用
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void onExtractItems(IAEStack request, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack> cir) {
        if (!this.ae2enhanced$isItemChannel) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemManaDrop.isManaDrop(mcStack)) return;

        // E2a：Mana 假物品禁止作为真实物品被取出.
        cir.setReturnValue(request);
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void onInjectItems(IAEStack input, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack> cir) {
        if (!this.ae2enhanced$isItemChannel || this.ae2enhanced$manaMonitor == null) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemManaDrop.isManaDrop(mcStack)) return;

        IAEManaStack manaInput = AEManaStack.create(itemStack.getStackSize());
        IAEManaStack notInjected = this.ae2enhanced$manaMonitor.injectItems(manaInput, mode, src);

        if (notInjected != null && notInjected.getStackSize() > 0) {
            IAEItemStack result = itemStack.copy();
            result.setStackSize(notInjected.getStackSize());
            cir.setReturnValue(result);
        } else {
            cir.setReturnValue(null);
        }
    }
}
