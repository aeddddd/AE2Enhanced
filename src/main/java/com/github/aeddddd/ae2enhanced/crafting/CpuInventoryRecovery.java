package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

public final class CpuInventoryRecovery {

    private CpuInventoryRecovery() {
    }

    public static boolean cancelAndDrain(Runnable cancel, IMEInventory<IAEItemStack> inventory,
            IMEMonitor<IAEItemStack> network, IActionSource source, Predicate<ItemStack> drop) {
        try {
            cancel.run();
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error(
                    "[AE2E] Error cancelling CraftingCPUCluster on disassemble: {}", e.toString());
        }
        return drain(inventory, network, source, drop);
    }

    public static boolean drain(IMEInventory<IAEItemStack> inventory, IMEMonitor<IAEItemStack> network,
                                IActionSource source, Predicate<ItemStack> drop) {
        if (network == null && drop == null) return false;
        IItemList<IAEItemStack> snapshot = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createList();
        inventory.getAvailableItems(snapshot);
        for (IAEItemStack available : snapshot) {
            if (available == null || available.getStackSize() <= 0) continue;
            IAEItemStack extracted = inventory.extractItems(available.copy(), Actionable.MODULATE, source);
            if (extracted == null || extracted.getStackSize() <= 0) continue;
            IAEItemStack remainder = extracted.copy();
            if (network != null) {
                try {
                    IAEItemStack rejected = network.injectItems(extracted.copy(), Actionable.MODULATE, source);
                    remainder = rejected == null ? null : rejected;
                } catch (RuntimeException ignored) {
                    remainder = extracted.copy();
                }
            }
            if (remainder != null && remainder.getStackSize() > 0
                    && !dropRemainder(inventory, remainder, source, drop)) {
                return false;
            }
        }
        IItemList<IAEItemStack> leftovers = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createList();
        inventory.getAvailableItems(leftovers);
        return leftovers.isEmpty();
    }

    private static boolean dropRemainder(IMEInventory<IAEItemStack> inventory, IAEItemStack remainder,
                                         IActionSource source, Predicate<ItemStack> drop) {
        long count = remainder.getStackSize();
        if (drop != null) {
            while (count > 0) {
                long chunk = Math.min(count, Integer.MAX_VALUE);
                IAEItemStack part = remainder.copy();
                part.setStackSize(chunk);
                if (!drop.test(part.createItemStack())) {
                    IAEItemStack rejected = remainder.copy();
                    rejected.setStackSize(count);
                    inventory.injectItems(rejected, Actionable.MODULATE, source);
                    return false;
                }
                count -= chunk;
            }
            return true;
        }
        inventory.injectItems(remainder.copy(), Actionable.MODULATE, source);
        return false;
    }
}
