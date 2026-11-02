package com.github.aeddddd.ae2enhanced.api;

import net.minecraft.item.ItemStack;

/** 不可变物品数量视图；物品栈在构造与读取时均防御性复制。 */
public final class CountedStackView {
    private final ItemStack stack;
    private final long count;

    public CountedStackView(ItemStack stack, long count) {
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
        this.count = Math.max(0L, count);
    }

    public ItemStack getStack() {
        return stack.copy();
    }

    public long getCount() {
        return count;
    }
}
