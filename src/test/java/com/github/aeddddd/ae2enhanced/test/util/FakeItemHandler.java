package com.github.aeddddd.ae2enhanced.test.util;

import java.util.Arrays;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * 测试用 {@link IItemHandlerModifiable} 假实现：基于 ItemStack 数组的确定性槽位行为。
 *
 * <p>严格遵守 simulate 语义（simulate=true 时不修改内部状态），
 * 便于断言两阶段（模拟-真实）原子性。插入仅在同物品（含 NBT）堆叠或空槽时成功，
 * 抽取按请求数量与现有数量的较小值返回。</p>
 */
public final class FakeItemHandler implements IItemHandlerModifiable {

    private final ItemStack[] slots;
    private final int slotLimit;

    public FakeItemHandler(int slots) {
        this(slots, 64);
    }

    public FakeItemHandler(int slots, int slotLimit) {
        this.slots = new ItemStack[slots];
        this.slotLimit = slotLimit;
        Arrays.fill(this.slots, ItemStack.EMPTY);
    }

    /** 直接放置初始内容（不经过 insert 逻辑）。 */
    public void setStack(int slot, ItemStack stack) {
        this.slots[slot] = stack;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        setStack(slot, stack);
    }

    @Override
    public int getSlots() {
        return slots.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slots[slot];
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = slots[slot];
        if (!existing.isEmpty()
                && (!ItemStack.areItemsEqual(existing, stack)
                        || !ItemStack.areItemStackTagsEqual(existing, stack))) {
            return stack;
        }
        int limit = Math.min(slotLimit, stack.getMaxStackSize());
        int room = limit - (existing.isEmpty() ? 0 : existing.getCount());
        if (room <= 0) {
            return stack;
        }
        int toAdd = Math.min(room, stack.getCount());
        if (!simulate) {
            if (existing.isEmpty()) {
                ItemStack placed = stack.copy();
                placed.setCount(toAdd);
                slots[slot] = placed;
            } else {
                existing.grow(toAdd);
            }
        }
        ItemStack remaining = stack.copy();
        remaining.shrink(toAdd);
        return remaining;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = slots[slot];
        if (existing.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int toTake = Math.min(amount, existing.getCount());
        ItemStack out = existing.copy();
        out.setCount(toTake);
        if (!simulate) {
            existing.shrink(toTake);
        }
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slotLimit;
    }
}
