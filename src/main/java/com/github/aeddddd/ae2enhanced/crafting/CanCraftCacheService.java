package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;

import java.util.function.BooleanSupplier;

/** 以 CPU 库存注入版本号为键的 canCraft 失败缓存。 */
public final class CanCraftCacheService {

    private CanCraftCacheService() {
    }

    public static boolean canCraft(CraftingCpuAdapter cpu, ICraftingPatternDetails details,
            BooleanSupplier original) {
        return canCraft(cpu, details, original, original);
    }

    public static boolean canCraft(CraftingCpuAdapter cpu, ICraftingPatternDetails details,
            BooleanSupplier withoutVersion, BooleanSupplier original) {
        IMEInventory<IAEItemStack> inventory = cpu.inventory();
        if (!(inventory instanceof IMeInventoryVersionAccess)) {
            return withoutVersion.getAsBoolean();
        }
        long version = ((IMeInventoryVersionAccess) inventory).ae2e$getInjectVersion();
        Long failedAt = cpu.context().canCraftFailures().get(details);
        if (failedAt != null && failedAt == version) {
            return false;
        }
        boolean result = original.getAsBoolean();
        if (result) {
            cpu.context().canCraftFailures().remove(details);
        } else {
            cpu.context().canCraftFailures().put(details, version);
        }
        return result;
    }

    public static void invalidate(CraftingCpuExecutionContext context) {
        context.canCraftFailures().clear();
    }
}
