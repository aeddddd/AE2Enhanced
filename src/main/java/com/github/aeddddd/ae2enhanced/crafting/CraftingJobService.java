package com.github.aeddddd.ae2enhanced.crafting;

import appeng.crafting.CraftingJob;
import com.github.aeddddd.ae2enhanced.craftingapi.SpecialCraftingPorts;

/** 原生 CraftingJob 的显示、验证与预算心跳。 */
public final class CraftingJobService {

    private CraftingJobService() {
    }

    public static long runHead() {
        return System.nanoTime();
    }

    public static void runReturn(CraftingJob job, long start) {
        SpecialCraftingPorts.get().onJobComputed(job, start >= 0L ? System.nanoTime() - start : 0L);
    }

    public static void handlePausing(CraftingJob job) throws InterruptedException {
        SpecialCraftingPorts.get().checkDeadline(job);
    }
}
