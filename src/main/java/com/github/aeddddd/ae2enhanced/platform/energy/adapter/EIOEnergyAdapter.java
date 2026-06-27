package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Method;

/**
 * Ender IO 专用能量适配器.
 *
 * <p>EIO 的 {@code LimitingRecieverTileCapabilityProvider} 在 Forge {@link IEnergyStorage}
 * 包装层做了 tick 级限流(每 tick 累计不超过 {@code getMaxEnergyRecieved()}).
 * 此适配器通过反射直接访问 EIO 机器底层的 {@code ILegacyPoweredTile} 接口,
 * 调用 {@code setEnergyStored()} 直接设置能量值,完全 bypass 包装层限流.</p>
 *
 * <p>安全回退：反射失败时自动回退到 {@link ForgeEnergyAdapter} 的多调用策略.</p>
 */
public class EIOEnergyAdapter implements IEnergyAdapter {

    private static final String[] BLOCK_PATTERNS = {
        "enderio:", "enderiomachines:", "enderioconduits:"
    };

    // ILegacyPoweredTile 反射缓存
    private static Class<?> legacyTileClass;
    private static Method getEnergyStoredMethod;
    private static Method getMaxEnergyStoredMethod;
    private static Method setEnergyStoredMethod;
    private static boolean reflectionReady = false;

    public EIOEnergyAdapter() {
        initReflection();
    }

    private static synchronized void initReflection() {
        if (reflectionReady) {
            return;
        }
        try {
            legacyTileClass = Class.forName("crazypants.enderio.base.power.forge.tile.ILegacyPoweredTile");
            getEnergyStoredMethod = legacyTileClass.getMethod("getEnergyStored");
            getMaxEnergyStoredMethod = legacyTileClass.getMethod("getMaxEnergyStored");
            setEnergyStoredMethod = legacyTileClass.getMethod("setEnergyStored", int.class);
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
        if (reflectionReady && tile != null && legacyTileClass.isInstance(tile)) {
            try {
                int current = (Integer) getEnergyStoredMethod.invoke(tile);
                int max = (Integer) getMaxEnergyStoredMethod.invoke(tile);
                return Math.max(0L, (long) max - current);
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
        if (reflectionReady && tile != null && legacyTileClass.isInstance(tile)) {
            try {
                int current = (Integer) getEnergyStoredMethod.invoke(tile);
                int max = (Integer) getMaxEnergyStoredMethod.invoke(tile);
                long canAdd = Math.max(0L, (long) max - current);
                long toAdd = Math.min(amount, canAdd);
                if (toAdd > 0 && !simulate) {
                    long newEnergy = (long) current + toAdd;
                    setEnergyStoredMethod.invoke(tile, (int) Math.min(newEnergy, Integer.MAX_VALUE));
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
