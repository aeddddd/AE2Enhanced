package com.github.aeddddd.ae2enhanced.container.slot;

import appeng.container.slot.SlotFakeTypeOnly;
import com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * 能源存储总线过滤槽, 仅接受 RF 假物品 ItemEnergyDrop 作为白名单模板.
 */
public class SlotEnergyTypeOnly extends SlotFakeTypeOnly {

    public SlotEnergyTypeOnly(IItemHandler inv, int idx, int x, int y) {
        super(inv, idx, x, y);
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
