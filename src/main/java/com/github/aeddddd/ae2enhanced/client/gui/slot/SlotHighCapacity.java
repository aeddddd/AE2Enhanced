package com.github.aeddddd.ae2enhanced.client.gui.slot;

import appeng.container.slot.AppEngSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * 高容量槽位 —— 绕过 ItemStack.getMaxStackSize() 的 64 限制,
 * 支持手动拖放超过 64 个物品.
 */
public class SlotHighCapacity extends AppEngSlot {

    public SlotHighCapacity(IItemHandler inv, int idx, int x, int y) {
        super(inv, idx, x, y);
    }

    @Override
    public int func_178170_b(@Nonnull ItemStack stack) {
        return this.func_75219_a();
    }
}
