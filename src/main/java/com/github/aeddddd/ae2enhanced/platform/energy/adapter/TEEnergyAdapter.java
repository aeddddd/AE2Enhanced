package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Method;

/**
 * Thermal Expansion 专用能量适配器.
 *
 * <p>TE 的能量单元(TileCell)和机器(TileMachineBase)都继承自
 * {@code cofh.core.block.TilePowered},其 {@code receiveEnergy} 受
 * {@code amountRecv} 或 {@code energyConfig.maxPower} 限制.</p>
 *
 * <p>此适配器通过反射直接调用 {@code TilePowered.setEnergyStored(int)},
 * 该方法直接设置底层 {@code EnergyStorage} 的能量值,完全 bypass
 * 所有接收速率限制.注入后显式调用 {@code tile.markDirty()} 确保数据持久化.</p>
 *
 * <p>安全回退：反射失败时自动回退到 {@link ForgeEnergyAdapter}.</p>
 */
public class TEEnergyAdapter extends ForgeEnergyAdapter {

    private static Class<?> tilePoweredClass;
    private static Method setEnergyStoredMethod;
    private static boolean reflectionReady = false;

    public TEEnergyAdapter() {
        initReflection();
    }

    private static synchronized void initReflection() {
        if (reflectionReady) {
            return;
        }
        try {
            tilePoweredClass = Class.forName("cofh.core.block.TilePowered");
            setEnergyStoredMethod = tilePoweredClass.getMethod("setEnergyStored", int.class);
            reflectionReady = true;
        } catch (Exception e) {
            // 反射失败,将完全回退到 ForgeEnergyAdapter
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return blockId.startsWith("thermalexpansion:") || blockId.startsWith("thermaldynamics:");
    }

    @Override
    public long getReceiveableEnergy(TileEntity tile, IEnergyStorage cap) {
        if (reflectionReady && tile != null && tilePoweredClass.isInstance(tile)) {
            try {
                int current = cap != null ? cap.getEnergyStored() : 0;
                int max = cap != null ? cap.getMaxEnergyStored() : 0;
                return Math.max(0L, (long) max - current);
            } catch (Exception e) {
                // 反射失败,回退
            }
        }
        return super.getReceiveableEnergy(tile, cap);
    }

    @Override
    public long injectEnergy(TileEntity tile, IEnergyStorage cap, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (reflectionReady && tile != null && tilePoweredClass.isInstance(tile)) {
            try {
                int current = cap != null ? cap.getEnergyStored() : 0;
                int max = cap != null ? cap.getMaxEnergyStored() : 0;
                long canAdd = Math.max(0L, (long) max - current);
                long toAdd = Math.min(amount, canAdd);
                if (toAdd > 0 && !simulate) {
                    long newEnergy = (long) current + toAdd;
                    setEnergyStoredMethod.invoke(tile, (int) Math.min(newEnergy, Integer.MAX_VALUE));
                    tile.markDirty();
                }
                return toAdd;
            } catch (Exception e) {
                // 反射失败,回退
            }
        }
        return super.injectEnergy(tile, cap, amount, simulate);
    }
}
