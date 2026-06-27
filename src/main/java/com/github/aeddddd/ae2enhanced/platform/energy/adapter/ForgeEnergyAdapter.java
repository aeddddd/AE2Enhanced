package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * 标准 Forge IEnergyStorage 适配器(fallback).
 *
 * <p>使用多调用策略突破机器单次 {@link IEnergyStorage#receiveEnergy} 上限,
 * 但无法突破模组自定义的 tick 级限流(如 EIO 的 EnergyOverflowProtection).</p>
 */
public class ForgeEnergyAdapter implements IEnergyAdapter {

    @Override
    public boolean canHandle(String blockId) {
        return true; // fallback,最后匹配
    }

    @Override
    public long getReceiveableEnergy(TileEntity tile, IEnergyStorage cap) {
        if (cap == null || !cap.canReceive()) {
            return 0;
        }
        return cap.receiveEnergy(Integer.MAX_VALUE, true);
    }

    @Override
    public long injectEnergy(TileEntity tile, IEnergyStorage cap, long amount, boolean simulate) {
        if (cap == null || !cap.canReceive() || amount <= 0) {
            return 0;
        }
        if (simulate) {
            return cap.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), true);
        }
        long total = 0;
        while (amount > 0) {
            int toReceive = (int) Math.min(amount, Integer.MAX_VALUE);
            int injected = cap.receiveEnergy(toReceive, false);
            if (injected <= 0) {
                break;
            }
            total += injected;
            amount -= injected;
        }
        return total;
    }
}
