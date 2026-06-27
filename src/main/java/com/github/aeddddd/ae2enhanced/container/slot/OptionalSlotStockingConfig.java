package com.github.aeddddd.ae2enhanced.container.slot;

import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFake;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Stocking Bus 的可选过滤槽：容量卡解锁,不限制 count.
 */
public class OptionalSlotStockingConfig extends OptionalSlotFake {

    public OptionalSlotStockingConfig(IItemHandler inv, IOptionalSlotHost host, int idx, int x, int y, int offX, int offY, int groupNum) {
        super(inv, host, idx, x, y, offX, offY, groupNum);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return this.isSlotEnabled();
    }
}
