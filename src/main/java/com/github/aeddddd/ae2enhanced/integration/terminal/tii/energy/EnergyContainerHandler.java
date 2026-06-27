package com.github.aeddddd.ae2enhanced.integration.terminal.tii.energy;

import appeng.api.networking.security.IActionSource;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import nyonio.terminal_interaction_integration.api.IContainerHandler;

/**
 * TII RF 能量容器处理器.
 * <p>
 * 通过 Forge {@link CapabilityEnergy#ENERGY} 与手持容器交互.
 * </p>
 */
public class EnergyContainerHandler implements IContainerHandler {

    @Override
    public boolean canHandle(ItemStack container) {
        return !container.isEmpty() && container.hasCapability(CapabilityEnergy.ENERGY, null);
    }

    @Override
    public long getStoredAmount(ItemStack container) {
        IEnergyStorage cap = getEnergyStorage(container);
        return cap == null ? 0 : cap.getEnergyStored();
    }

    @Override
    public long getMaxCapacity(ItemStack container) {
        IEnergyStorage cap = getEnergyStorage(container);
        return cap == null ? 0 : cap.getMaxEnergyStored();
    }

    @Override
    public long extract(ItemStack container, long amount, IActionSource source) {
        IEnergyStorage cap = getEnergyStorage(container);
        if (cap == null) {
            return 0;
        }
        return cap.extractEnergy(clamp(amount), false);
    }

    @Override
    public long inject(ItemStack container, long amount, IActionSource source) {
        IEnergyStorage cap = getEnergyStorage(container);
        if (cap == null) {
            return 0;
        }
        return cap.receiveEnergy(clamp(amount), false);
    }

    @Override
    public String getContainerDisplayName(ItemStack container) {
        return container.getDisplayName();
    }

    @Override
    public ItemStack getEmptyContainer() {
        return ItemStack.EMPTY;
    }

    private static IEnergyStorage getEnergyStorage(ItemStack container) {
        if (container.isEmpty()) {
            return null;
        }
        return container.getCapability(CapabilityEnergy.ENERGY, null);
    }

    private static int clamp(long amount) {
        return (int) Math.min(Math.max(amount, 0), Integer.MAX_VALUE);
    }
}
