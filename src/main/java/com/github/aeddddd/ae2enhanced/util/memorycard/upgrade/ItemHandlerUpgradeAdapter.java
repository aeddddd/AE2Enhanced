package com.github.aeddddd.ae2enhanced.util.memorycard.upgrade;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * 将 Forge IItemHandler 适配为 IUpgradeProvider.
 * 用于 AE2 升级槽等基于 IItemHandler 的实现.
 */
public class ItemHandlerUpgradeAdapter implements IUpgradeProvider {

    private final IItemHandler handler;

    public ItemHandlerUpgradeAdapter(IItemHandler handler) {
        this.handler = handler;
    }

    @Override
    public int getSlotCount() {
        return handler.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return handler.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        // IItemHandler 只读时可能不支持 setStackInSlot
        // 但 AE2 的 AppEngInternalAEInventory 支持
        if (handler instanceof net.minecraftforge.items.IItemHandlerModifiable) {
            ((net.minecraftforge.items.IItemHandlerModifiable) handler).setStackInSlot(slot, stack);
        }
    }

    @Override
    public void clearSlots() {
        for (int i = 0; i < handler.getSlots(); i++) {
            setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
