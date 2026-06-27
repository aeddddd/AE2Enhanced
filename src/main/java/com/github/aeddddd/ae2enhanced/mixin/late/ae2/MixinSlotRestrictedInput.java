package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotRestrictedInput;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import com.github.aeddddd.ae2enhanced.item.ItemVirtualParallelCard;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * F1b：让频道接收卡能够通过 AE2 升级槽的 Slot 级别验证.
 *
 * <p>AE2 的 {@link SlotRestrictedInput} 在 UPGRADES 类型下会检查
 * {@code instanceof IUpgradeModule},而我们的接收卡已不再实现该接口.
 * 此 Mixin 在 {@code func_75214_a} / {@code isItemValid} 的 HEAD 拦截,
 * 对频道接收卡单独放行,并复刻原方法中的基本校验(allowEdit、isSlotEnabled).</p>
 */
@Mixin(value = SlotRestrictedInput.class, remap = false)
public class MixinSlotRestrictedInput {

    @Shadow
    @Final
    private SlotRestrictedInput.PlacableItemType which;

    @Shadow
    private boolean allowEdit;

    @Inject(method = "func_75214_a(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void ae2e$allowChannelReceiverCard(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        boolean isVirtualCard = stack.getItem() instanceof ItemVirtualParallelCard;
        if (!isVirtualCard && !(stack.getItem() instanceof ItemChannelReceiverCard)) {
            return;
        }

        // 非 UPGRADES 槽位直接拒绝
        if (this.which != SlotRestrictedInput.PlacableItemType.UPGRADES) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // 复刻原方法的基本校验
        if (!this.allowEdit) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        AppEngSlot as = (AppEngSlot) (Object) this;
        if (!as.isSlotEnabled()) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // 委托给底层 handler 做最终验证(MixinUpgradeInvFilter 会处理数量限制)
        IItemHandler handler = as.getItemHandler();
        int idx = ((net.minecraft.inventory.Slot) (Object) this).getSlotIndex();
        boolean ok = handler != null && handler.isItemValid(idx, stack);
        cir.setReturnValue(ok);
        cir.cancel();
    }
}
