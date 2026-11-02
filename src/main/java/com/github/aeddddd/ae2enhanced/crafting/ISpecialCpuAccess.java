package com.github.aeddddd.ae2enhanced.crafting;

/** CraftingCPUCluster 织入能力的普通 Java SPI。 */
public interface ISpecialCpuAccess extends com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuAccess {

    @Override
    CraftingCpuExecutionContext ae2e$context();
}
