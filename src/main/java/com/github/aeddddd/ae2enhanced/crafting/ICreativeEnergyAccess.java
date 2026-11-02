package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.energy.IEnergyGrid;

/** CraftingCPUCluster 创造能源能力的普通 Java SPI。 */
public interface ICreativeEnergyAccess {

    boolean ae2e$hasCreativeEnergy(IEnergyGrid eg);
}
