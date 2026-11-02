package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.crafting.ICraftingMedium;
import net.minecraft.world.World;

import java.util.function.BooleanSupplier;

/** per-medium 推送失败冷却。 */
public final class PushCooldownService {

    private static final long COOLDOWN_TICKS = 5L;

    private PushCooldownService() {
    }

    public static boolean isBusy(CraftingCpuExecutionContext context, ICraftingMedium medium,
            World world, BooleanSupplier original) {
        Long failed = context.pushFailureTicks().get(medium);
        if (failed != null && world != null && world.getTotalWorldTime() - failed < COOLDOWN_TICKS) {
            return true;
        }
        return original.getAsBoolean();
    }

    public static boolean pushed(CraftingCpuExecutionContext context, ICraftingMedium medium,
            World world, boolean result) {
        if (!result && world != null) {
            context.pushFailureTicks().put(medium, world.getTotalWorldTime());
        }
        return result;
    }
}
