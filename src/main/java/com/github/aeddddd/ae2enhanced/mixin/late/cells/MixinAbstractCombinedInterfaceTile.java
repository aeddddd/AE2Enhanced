package com.github.aeddddd.ae2enhanced.mixin.late.cells;

import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import com.github.aeddddd.ae2enhanced.util.network.WirelessChannelConnectionHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin CELLS AbstractCombinedInterfaceTile，在任意 inventory 变化后尝试重建无线连接。
 * AbstractCombinedInterfaceTile 覆盖了 onChangeInventory，因此需要单独 Mixin。
 */
@Mixin(targets = "com.cells.blocks.combinedinterface.AbstractCombinedInterfaceTile", remap = false)
public class MixinAbstractCombinedInterfaceTile {

    @Inject(method = "onChangeInventory", at = @At("TAIL"), remap = false)
    private void ae2e$onUpgradeChanged(IItemHandler inv, int slot, InvOperation mc,
                                       ItemStack removed, ItemStack added, CallbackInfo ci) {
        if (!Platform.isServer()) return;
        if (this instanceof IAEAppEngInventory) {
            WirelessChannelConnectionHelper.destroyConnection((IAEAppEngInventory) this);
            WirelessChannelConnectionHelper.tryConnect((IAEAppEngInventory) this);
        }
    }
}
