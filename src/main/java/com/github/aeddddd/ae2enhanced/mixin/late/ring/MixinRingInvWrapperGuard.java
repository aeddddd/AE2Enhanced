package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.item.ItemNetworkLinkCredential;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 飞升凭证防自动化抽取：拦截 Forge IItemHandler 能力(InvWrapper)的 extractItem,
 * 使任何走能力系统的自动化手段(集成动力学玩家接口、管道等)都无法从玩家背包抽出飞升凭证.
 * 源头阻断,物品永远只有一个实例,杜绝复制.
 * GUI 手动操作(槽位点击)不经过 IItemHandler,不受影响.
 * Forge 原生类(不参与混淆),remap=false.
 */
@Mixin(value = InvWrapper.class, remap = false)
public abstract class MixinRingInvWrapperGuard {

    @Final
    @Shadow
    private IInventory inv;

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true)
    private void ae2e$blockCredentialExtraction(int slot, int amount, boolean simulate,
                                                CallbackInfoReturnable<ItemStack> cir) {
        if (!(this.inv instanceof InventoryPlayer)) return;
        if (slot < 0 || slot >= this.inv.getSizeInventory()) return;
        ItemStack stack = this.inv.getStackInSlot(slot);
        if (!stack.isEmpty() && stack.getItem() instanceof ItemNetworkLinkCredential && RingNBT.isAscended(stack)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
