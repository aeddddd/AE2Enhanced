package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

import java.util.ArrayList;
import java.util.List;

public final class AssemblyBatchSupport {

    private AssemblyBatchSupport() {
    }

    public static long checkedAmount(long perOperation, long operations) {
        if (perOperation <= 0 || operations <= 0) {
            throw new ArithmeticException("Batch operands must be positive");
        }
        return Math.multiplyExact(perOperation, operations);
    }

    public static IAEItemStack scaledCopy(IAEItemStack template, long operations) {
        IAEItemStack result = template.copy();
        result.setStackSize(checkedAmount(template.getStackSize(), operations));
        return result;
    }

    public static List<IAEItemStack> aggregate(Iterable<IAEItemStack> requests) {
        IItemList<IAEItemStack> aggregated = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createList();
        for (IAEItemStack request : requests) {
            if (request == null || request.getStackSize() <= 0) continue;
            IAEItemStack existing = aggregated.findPrecise(request);
            if (existing == null) {
                aggregated.add(request.copy());
            } else {
                existing.setStackSize(Math.addExact(existing.getStackSize(), request.getStackSize()));
            }
        }
        List<IAEItemStack> result = new ArrayList<>();
        for (IAEItemStack request : aggregated) {
            result.add(request.copy());
        }
        return result;
    }

    public static List<IAEItemStack> extractExact(IMEInventory<IAEItemStack> inventory,
                                                   Iterable<IAEItemStack> requests,
                                                   IActionSource source) {
        List<IAEItemStack> aggregated = aggregate(requests);
        for (IAEItemStack request : aggregated) {
            IAEItemStack simulated = inventory.extractItems(request.copy(), Actionable.SIMULATE, source);
            if (simulated == null || simulated.getStackSize() != request.getStackSize()) {
                return null;
            }
        }

        List<IAEItemStack> extracted = new ArrayList<>();
        for (IAEItemStack request : aggregated) {
            IAEItemStack actual = inventory.extractItems(request.copy(), Actionable.MODULATE, source);
            long actualCount = actual == null ? 0 : actual.getStackSize();
            if (actualCount > 0) {
                extracted.add(actual.copy());
            }
            if (actualCount != request.getStackSize()) {
                rollbackExtracted(inventory, extracted, source);
                return null;
            }
        }
        return extracted;
    }

    public static void rollbackExtracted(IMEInventory<IAEItemStack> inventory, List<IAEItemStack> extracted,
                                         IActionSource source) {
        for (int i = extracted.size() - 1; i >= 0; i--) {
            IAEItemStack remainder = inventory.injectItems(extracted.get(i).copy(), Actionable.MODULATE, source);
            if (remainder != null && remainder.getStackSize() > 0) {
                throw new IllegalStateException("CPU inventory rejected crafting rollback: " + remainder.getStackSize());
            }
        }
    }
}
