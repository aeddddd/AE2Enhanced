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
 * Mixin CELLS AbstractInterfaceTile，在任意 inventory 变化后尝试重建无线连接。
 * 由于 CELLS Interface 使用 AppEngInternalInventory 而非 UpgradeInventory，
 * MixinUpgradeInventory 无法捕获其升级槽变化，需要在此补充触发。
 */
@Mixin(targets = "com.cells.blocks.interfacebase.AbstractInterfaceTile", remap = false)
public class MixinAbstractInterfaceTile {

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
