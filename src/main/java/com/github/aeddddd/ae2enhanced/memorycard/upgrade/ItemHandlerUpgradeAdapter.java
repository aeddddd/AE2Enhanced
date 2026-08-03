package com.github.aeddddd.ae2enhanced.memorycard.upgrade;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * 将 Forge IItemHandler 适配为 IUpgradeProvider.
 * 供第三方 Mod 基于 IItemHandler 的升级槽使用.
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
        if (handler instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, stack);
        }
    }

    @Override
    public void clearSlots() {
        for (int i = 0; i < handler.getSlots(); i++) {
            setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
