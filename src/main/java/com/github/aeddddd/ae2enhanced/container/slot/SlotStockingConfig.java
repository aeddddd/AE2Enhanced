package com.github.aeddddd.ae2enhanced.container.slot;

import appeng.container.slot.SlotFake;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Stocking Bus 的过滤槽：不限制 count,允许通过拖放放入物品.
 * count 直接表示目标数量,让 slot 自然显示.
 */
public class SlotStockingConfig extends SlotFake {

    public SlotStockingConfig(IItemHandler inv, int idx, int x, int y) {
        super(inv, idx, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return this.isSlotEnabled();
    }
}
