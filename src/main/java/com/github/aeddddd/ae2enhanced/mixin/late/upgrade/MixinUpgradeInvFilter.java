package com.github.aeddddd.ae2enhanced.mixin.late.upgrade;

import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import com.github.aeddddd.ae2enhanced.item.ItemVirtualParallelCard;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * F1b：Mixin AE2 UpgradeInventory 的内部过滤器,让频道接收卡能够绕过
 * {@link appeng.api.config.Upgrades} 枚举限制,直接插入升级槽.
 *
 * <p>参考实现：AE2 Better Magnet Card<br>
 * https://github.com/NuanKi/AE2-Better-Magnet-Card</p>
 */
@Mixin(targets = "appeng.parts.automation.UpgradeInventory$UpgradeInvFilter", remap = false)
public class MixinUpgradeInvFilter {

    @Inject(method = "allowInsert", at = @At("HEAD"), cancellable = true)
    private void ae2e$allowChannelReceiverCard(IItemHandler inv, int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof ItemVirtualParallelCard) {
            cir.setReturnValue(true);
            return;
        }
        if (!(stack.getItem() instanceof ItemChannelReceiverCard)) {
            return;
        }

        // 每个设备最多允许 1 张频道接收卡
        for (int i = 0; i < inv.getSlots(); i++) {
            if (i == slot) {
                continue;
            }
            ItemStack existing = inv.getStackInSlot(i);
            if (!existing.isEmpty() && existing.getItem() instanceof ItemChannelReceiverCard) {
                cir.setReturnValue(false);
                return;
            }
        }
        cir.setReturnValue(true);
    }
}
