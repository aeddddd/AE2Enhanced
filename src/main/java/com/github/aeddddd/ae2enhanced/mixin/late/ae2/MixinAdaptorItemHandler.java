package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.util.inv.AdaptorItemHandler;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISlotIndexProvider;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AdaptorItemHandler.addItems 的槽位索引快路径. 当底层 handler 提供 ISlotIndexProvider
 * 索引时, 只对候选槽位(同 key 可合并槽与空槽, 升序)逐一 insertItem. 候选集是原逻辑可能
 * 接受的精确超集, 每个候选仍走原 insertItem 复验, 被跳过的槽位必然拒绝, 语义与全槽扫描一致.
 * simulate 语义逐行复刻原生: 真实模式每次迭代前 copy 输入栈, 模拟模式共享同一引用.
 */
@Mixin(value = AdaptorItemHandler.class, remap = false)
public abstract class MixinAdaptorItemHandler {

    @Shadow
    @Final
    protected IItemHandler itemHandler;

    @Inject(
        method = "addItems(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/item/ItemStack;",
        at = @At("HEAD"), cancellable = true, require = 0
    )
    private void ae2enhanced$indexedAddItems(ItemStack itemsToAdd, boolean simulate,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!(this.itemHandler instanceof ISlotIndexProvider)) {
            return;
        }
        int[] candidates = ((ISlotIndexProvider) this.itemHandler).ae2e$getInsertCandidates(itemsToAdd);
        if (candidates == null) {
            return; // 索引不适用(allowAnySlots 等), 回退原生全槽扫描
        }
        if (itemsToAdd.isEmpty()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        for (int slot : candidates) {
            if (!simulate) {
                itemsToAdd = itemsToAdd.copy();
            }
            itemsToAdd = this.itemHandler.insertItem(slot, itemsToAdd, simulate);
            if (itemsToAdd.isEmpty()) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
        }
        cir.setReturnValue(itemsToAdd);
    }
}
