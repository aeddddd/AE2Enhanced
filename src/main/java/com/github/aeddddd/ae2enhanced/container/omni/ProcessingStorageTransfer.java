package com.github.aeddddd.ae2enhanced.container.omni;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public final class ProcessingStorageTransfer {
    private final IItemHandler storage;
    private final Function<ItemStack, ItemStack> extractor;
    private final Consumer<ItemStack> returnSink;

    public ProcessingStorageTransfer(IItemHandler storage, Function<ItemStack, ItemStack> extractor,
            Consumer<ItemStack> returnSink) {
        this.storage = storage;
        this.extractor = extractor;
        this.returnSink = returnSink;
    }

    public void replace(Collection<ItemStack> requested) {
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            returnSink.accept(storage.extractItem(slot, Integer.MAX_VALUE, false));
        }
        for (ItemStack request : requested) {
            if (request == null || request.isEmpty()) {
                continue;
            }
            ItemStack extracted = extractor.apply(request.copy());
            if (extracted == null || extracted.isEmpty()) {
                continue;
            }
            returnSink.accept(ItemHandlerHelper.insertItemStacked(storage, extracted, false));
        }
    }
}
