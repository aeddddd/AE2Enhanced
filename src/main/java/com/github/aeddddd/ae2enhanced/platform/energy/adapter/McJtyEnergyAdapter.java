package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Method;

/**
 * mcjtylib(RFTools / XNet 等)专用能量适配器.
 *
 * <p>mcjtylib 的 {@link mcjty.lib.tileentity.McJtyEnergyStorage} 在
 * {@code receiveEnergy()} 中受 {@code maxReceive} 字段限制,导致标准 Forge
 * {@link IEnergyStorage#receiveEnergy} 单次/tick 注入量被 capped.</p>
 *
 * <p>此适配器通过反射直接调用 {@code GenericEnergyStorageTileEntity.modifyEnergyStored(long)}
 * 和 {@code getStoredPower() / getCapacity()},完全 bypass {@code maxReceive} 限制,
 * 只受机器内部能量容量上限约束.</p>
 */
public class McJtyEnergyAdapter implements IEnergyAdapter {

    private static final String[] BLOCK_PATTERNS = {
        "rftools:",
        "rftoolspower:",
        "rftoolscontrol:",
        "rftoolsdim:",
        "xnet:",
        "deepresonance:",
    };

    // mcjty.lib.tileentity.GenericEnergyStorageTileEntity 反射缓存
    private static Class<?> genericEnergyTileClass;
    private static Method getStoredPowerMethod;
    private static Method getCapacityMethod;
    private static Method modifyEnergyStoredMethod;
    private static boolean reflectionReady = false;

    public McJtyEnergyAdapter() {
        initReflection();
    }

    private static synchronized void initReflection() {
        if (reflectionReady) {
            return;
        }
        try {
            genericEnergyTileClass = Class.forName("mcjty.lib.tileentity.GenericEnergyStorageTileEntity");
            getStoredPowerMethod = genericEnergyTileClass.getMethod("getStoredPower");
            getCapacityMethod = genericEnergyTileClass.getMethod("getCapacity");
            modifyEnergyStoredMethod = genericEnergyTileClass.getMethod("modifyEnergyStored", long.class);
            reflectionReady = true;
        } catch (Exception e) {
            // 反射失败,将完全回退到标准 Forge 策略
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        for (String pattern : BLOCK_PATTERNS) {
            if (blockId.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long getReceiveableEnergy(TileEntity tile, IEnergyStorage cap) {
        if (reflectionReady && tile != null && genericEnergyTileClass.isInstance(tile)) {
            try {
                long current = (Long) getStoredPowerMethod.invoke(tile);
                long max = (Long) getCapacityMethod.invoke(tile);
                return Math.max(0L, max - current);
            } catch (Exception e) {
                // 反射失败,回退
            }
        }
        return fallbackReceiveable(cap);
    }

    @Override
    public long injectEnergy(TileEntity tile, IEnergyStorage cap, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (reflectionReady && tile != null && genericEnergyTileClass.isInstance(tile)) {
            try {
                long current = (Long) getStoredPowerMethod.invoke(tile);
                long max = (Long) getCapacityMethod.invoke(tile);
                long canAdd = Math.max(0L, max - current);
                long toAdd = Math.min(amount, canAdd);
                if (toAdd > 0 && !simulate) {
                    modifyEnergyStoredMethod.invoke(tile, toAdd);
                }
                return toAdd;
            } catch (Exception e) {
                // 反射失败,回退
            }
        }
        return fallbackInject(cap, amount, simulate);
    }

    private static long fallbackReceiveable(IEnergyStorage cap) {
        if (cap == null || !cap.canReceive()) {
            return 0;
        }
        return cap.receiveEnergy(Integer.MAX_VALUE, true);
    }

    private static long fallbackInject(IEnergyStorage cap, long amount, boolean simulate) {
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
