package com.github.aeddddd.ae2enhanced.container.slot;

import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFakeTypeOnly;
import com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * 能源存储总线的可选过滤槽, 由容量卡解锁, 仅接受 RF 假物品.
 */
public class OptionalSlotEnergyTypeOnly extends OptionalSlotFakeTypeOnly {

    public OptionalSlotEnergyTypeOnly(IItemHandler inv, IOptionalSlotHost containerBus, int idx,
                                      int x, int y, int offX, int offY, int groupNum) {
        super(inv, containerBus, idx, x, y, offX, offY, groupNum);
    }

    @Override
    public void putStack(ItemStack is) {
        if (!is.isEmpty() && !ItemEnergyDrop.isEnergyDrop(is)) {
            return;
        }
        super.putStack(is);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return ItemEnergyDrop.isEnergyDrop(stack);
    }
}
