package com.github.aeddddd.ae2enhanced.centralinterface.handler.thermalexpansion;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * {@code cofh.core.block.TileInventory#inventory} 数组的 {@link IItemHandler} 视图.
 *
 * <p>框架层（{@code ItemTransferHelper} / {@code SlotRef}）只认 {@code IItemHandler},
 * 而 TE 机器把槽位内容存在自己的 {@code ItemStack[]} 里（且该数组会被
 * {@code readInventoryFromNBT} 整块替换）。本类把"反射出来的数组"包成 handler,
 * 使推料/收集/回退全部走框架,同时保持"逐槽写数组、写后 {@code markDirty}"的语义。</p>
 *
 * <p><b>{@link #insertItem} 故意不做部分插入</b>：整堆必须放进单个输入槽（空槽直接整堆写入；
 * 非空槽要求同物品同 NBT 且 {@code 现有数量 + 新数量 <= getMaxStackSize()}），放不下就换下一个输入槽。
 * 允许部分插入会让框架把一次 64 个的推料拆进多个槽位并判定成功，推料语义随之改变。</p>
 */
final class InventoryArrayItemHandler implements IItemHandler {

    private final IInventory inventory;
    private final ItemStack[] slots;

    InventoryArrayItemHandler(IInventory inventory, ItemStack[] slots) {
        this.inventory = inventory;
        this.slots = slots;
    }

    @Override
    public int getSlots() {
        return this.slots.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return peek(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || outOfRange(slot)) {
            return stack;
        }
        ItemStack existing = peek(slot);
        if (!existing.isEmpty()) {
            // 判据：同物品同 NBT、且合并后不超过物品自身的最大堆叠（不使用槽位上限）
            if (!ItemStack.areItemsEqual(existing, stack)
                    || !ItemStack.areItemStackTagsEqual(existing, stack)
                    || existing.getCount() + stack.getCount() > existing.getMaxStackSize()
                    || !this.inventory.isItemValidForSlot(slot, stack)) {
                return stack;
            }
            if (!simulate) {
                existing.grow(stack.getCount());
                markDirty();
            }
            return ItemStack.EMPTY;
        }
        if (!this.inventory.isItemValidForSlot(slot, stack)) {
            return stack;
        }
        if (!simulate) {
            this.slots[slot] = stack.copy();
            markDirty();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = peek(slot);
        if (existing.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, existing.getCount());
        ItemStack out = existing.copy();
        out.setCount(taken);
        if (!simulate) {
            existing.shrink(taken);
            if (existing.getCount() <= 0) {
                this.slots[slot] = ItemStack.EMPTY;
            }
            markDirty();
        }
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.inventory.getInventoryStackLimit();
    }

    /** 读取槽位内容；数组内出现 null 时按空槽处理（TE 自身各路径都会填满 EMPTY,此分支只是兜底）。 */
    private ItemStack peek(int slot) {
        if (outOfRange(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.slots[slot];
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private boolean outOfRange(int slot) {
        return slot < 0 || slot >= this.slots.length;
    }

    private void markDirty() {
        try {
            this.inventory.markDirty();
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] markDirty failed", e);
        }
    }
}
