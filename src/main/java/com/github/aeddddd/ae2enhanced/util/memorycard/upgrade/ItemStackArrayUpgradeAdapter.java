package com.github.aeddddd.ae2enhanced.util.memorycard.upgrade;

import net.minecraft.item.ItemStack;

/**
 * 将 ItemStack[] 数组适配为 IUpgradeProvider.
 * 用于 TE augments[]、EIO inventory[] 等直接操作数组的场景.
 *
 * 支持可选的 offset 和 count,用于 EIO 的 inventory 中某一段连续 slot.
 */
public class ItemStackArrayUpgradeAdapter implements IUpgradeProvider {

    private final ItemStack[] array;
    private final int offset;
    private final int count;

    public ItemStackArrayUpgradeAdapter(ItemStack[] array) {
        this(array, 0, array != null ? array.length : 0);
    }

    public ItemStackArrayUpgradeAdapter(ItemStack[] array, int offset, int count) {
        this.array = array;
        this.offset = offset;
        this.count = Math.max(0, count);
    }

    @Override
    public int getSlotCount() {
        return count;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (array == null) return ItemStack.EMPTY;
        int idx = offset + slot;
        if (idx < 0 || idx >= array.length) return ItemStack.EMPTY;
        return array[idx];
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (array == null) return;
        int idx = offset + slot;
        if (idx < 0 || idx >= array.length) return;
        array[idx] = stack.copy();
    }

    @Override
    public void clearSlots() {
        for (int i = 0; i < count; i++) {
            setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
