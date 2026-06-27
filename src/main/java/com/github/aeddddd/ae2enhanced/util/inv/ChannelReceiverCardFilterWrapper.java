package com.github.aeddddd.ae2enhanced.util.inv;

import appeng.util.inv.filter.IAEItemFilter;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * 包装任意 {@link IAEItemFilter}，使其额外允许 {@link ItemChannelReceiverCard} 插入。
 * 同时限制每个 inventory 最多只能放入 1 张频道接收卡。
 */
public class ChannelReceiverCardFilterWrapper implements IAEItemFilter {
    private final IAEItemFilter original;

    public ChannelReceiverCardFilterWrapper(IAEItemFilter original) {
        this.original = original;
    }

    @Override
    public boolean allowExtract(IItemHandler inv, int slot, int amount) {
        return original == null || original.allowExtract(inv, slot, amount);
    }

    @Override
    public boolean allowInsert(IItemHandler inv, int slot, ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemChannelReceiverCard) {
            for (int i = 0; i < inv.getSlots(); i++) {
                if (i == slot) continue;
                ItemStack existing = inv.getStackInSlot(i);
                if (!existing.isEmpty() && existing.getItem() instanceof ItemChannelReceiverCard) {
                    return false;
                }
            }
            return true;
        }
        return original == null || original.allowInsert(inv, slot, stack);
    }
}
