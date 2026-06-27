package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量收集待注入的物品.
 */
public class BulkCollector {

    private final List<IAEItemStack> buffer = new ArrayList<>();
    private long totalCount = 0;

    public void add(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return;
        IAEItemStack ae = AEItemStack.fromItemStack(stack);
        if (ae == null) return;
        add(ae);
    }

    public void add(@Nonnull IAEItemStack stack) {
        if (stack.getStackSize() <= 0) return;
        for (IAEItemStack existing : buffer) {
            if (existing.isSameType(stack)) {
                existing.add(stack);
                totalCount += stack.getStackSize();
                return;
            }
        }
        buffer.add(stack.copy());
        totalCount += stack.getStackSize();
    }

    @Nonnull
    public List<IAEItemStack> drain() {
        if (buffer.isEmpty()) return Collections.emptyList();
        List<IAEItemStack> result = new ArrayList<>(buffer);
        buffer.clear();
        totalCount = 0;
        return result;
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public int getTypeCount() {
        return buffer.size();
    }

    public long getTotalCount() {
        return totalCount;
    }
}
