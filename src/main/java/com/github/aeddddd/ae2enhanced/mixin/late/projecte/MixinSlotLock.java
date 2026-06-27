package com.github.aeddddd.ae2enhanced.mixin.late.projecte;

import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotLock;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 移除锁定槽对 Long.MAX_VALUE 的上限判断，使放入 Klein 星等物品时可以把全部 EMC 转入账户。
 */
@Mixin(value = SlotLock.class, remap = false)
public class MixinSlotLock {

    @ModifyVariable(
            method = "func_75215_d(Lnet/minecraft/item/ItemStack;)V",
            at = @At(value = "STORE", ordinal = 0),
            name = "remainEmc",
            remap = false
    )
    private long ae2e$unlimitRemainEmc(long remainEmc, ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof IItemEmc) {
            return ((IItemEmc) stack.getItem()).getStoredEmc(stack);
        }
        return remainEmc;
    }
}
