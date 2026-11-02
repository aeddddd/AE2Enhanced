package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.IGrid;
import appeng.tile.networking.TileCreativeEnergyCell;
import net.minecraft.world.World;

import java.util.function.DoubleSupplier;

/** 创造能源检测与原生能量扣费旁路。 */
public final class CreativeEnergyService {

    private static final long CHECK_INTERVAL = 100L;

    private CreativeEnergyService() {
    }

    public static boolean hasCreativeEnergy(CraftingCpuExecutionContext context, World world, IGrid grid) {
        long now = world != null ? world.getTotalWorldTime() : Long.MIN_VALUE;
        if (now != Long.MIN_VALUE && now - context.creativeEnergyCheckTick() < CHECK_INTERVAL) {
            return context.creativeEnergy();
        }
        context.creativeEnergyCheckTick(now);
        boolean found = false;
        try {
            if (grid != null) {
                found = !grid.getMachines(TileCreativeEnergyCell.class).isEmpty();
            }
        } catch (Throwable ignored) {
        }
        context.creativeEnergy(found);
        return found;
    }

    public static double extract(CraftingCpuExecutionContext context, World world, IGrid grid,
            double amount, DoubleSupplier original) {
        if (hasCreativeEnergy(context, world, grid)) {
            return amount;
        }
        return original.getAsDouble();
    }
}
