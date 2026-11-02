package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/** Central 虚拟批量与原生 CPU 之间的无状态执行服务。 */
public final class VirtualBatchCraftingService {

    private VirtualBatchCraftingService() {
    }

    public static boolean dispatch(CraftingCpuAdapter cpu, VirtualBatchCraftingHandler duality,
            ICraftingPatternDetails details, PushCall original) {
        if (duality == null) {
            return original.call();
        }

        long pending = cpu.taskRemaining(details);
        if (pending > 0) {
            duality.setNextVirtualBatchLimit(pending);
        }
        duality.setVirtualItemSource(cpu.inventory());

        boolean result;
        try {
            result = original.call();
        } finally {
            duality.setVirtualItemSource(null);
        }
        if (!result) {
            return false;
        }

        try {
            correctCounts(cpu, details, duality.getLastVirtualBatchSize());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Virtual batch CPU correction failed: {}", e.toString(), e);
        }
        return true;
    }

    private static void correctCounts(CraftingCpuAdapter cpu, ICraftingPatternDetails details, long batchSize) {
        if (batchSize <= 1 || cpu.tasks().get(details) == null) {
            return;
        }
        long remaining = cpu.taskRemaining(details);
        long extra = Math.min(batchSize - 1, remaining - 1);
        if (extra <= 0) {
            return;
        }

        for (IAEItemStack output : details.getCondensedOutputs()) {
            if (output != null && output.getStackSize() > 0) {
                Math.multiplyExact(output.getStackSize(), extra);
            }
        }
        cpu.correctTaskRemaining(details, extra);
        if (cpu.waitingFor() == null) {
            return;
        }
        for (IAEItemStack output : details.getCondensedOutputs()) {
            if (output == null || output.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack extraOutput = output.copy();
            extraOutput.setStackSize(Math.multiplyExact(output.getStackSize(), extra));
            cpu.waitingFor().add(extraOutput);
            cpu.access().ae2e$postCraftingStatusChange(extraOutput.copy());
        }
    }

    @FunctionalInterface
    public interface PushCall {
        boolean call();
    }
}
