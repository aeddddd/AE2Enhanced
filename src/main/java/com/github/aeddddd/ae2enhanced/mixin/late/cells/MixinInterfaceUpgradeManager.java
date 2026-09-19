package com.github.aeddddd.ae2enhanced.mixin.late.cells;

import appeng.tile.inventory.AppEngInternalInventory;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin CELLS 的 InterfaceUpgradeManager, 让频道接收卡能够放入其升级槽.
 * 它用 AppEngInternalInventory 并覆盖 isItemValid, MixinAppEngInternalInventory 的
 * filter 包装无法生效, 需要单独处理.
 */
@Mixin(targets = "com.cells.blocks.interfacebase.managers.InterfaceUpgradeManager", remap = false)
public class MixinInterfaceUpgradeManager {

    @Shadow
    private AppEngInternalInventory upgradeInventory;

    @Inject(method = "isValidUpgrade", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$allowChannelReceiverCard(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemChannelReceiverCard)) {
            return;
        }
        try {
            IItemHandler inv = this.upgradeInventory;
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack existing = inv.getStackInSlot(i);
                if (!existing.isEmpty() && existing.getItem() instanceof ItemChannelReceiverCard) {
                    cir.setReturnValue(false);
                    return;
                }
            }
            cir.setReturnValue(true);
        } catch (Exception e) {
            cir.setReturnValue(true);
        }
    }
}
