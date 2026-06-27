package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Method;

/**
 * Mekanism 专用能量适配器.
 *
 * <p>Mekanism 的 Forge Energy 兼容性层 {@code ForgeEnergyIntegration} 在
 * {@code IEnergyStorage#receiveEnergy} 中会将 RF → Joules → RF 进行转换,
 * 且 {@code toRF(double)} 返回值被 {@code clampToInt} 截断为 {@code int}.
 * 对于大容量设备(如 Elite/Ultimate 能量立方、感应矩阵端口),这会导致
 * 单次注入上限被隐式限制在 {@code Integer.MAX_VALUE} RF 附近.</p>
 *
 * <p>此适配器通过反射直接调用 {@code IEnergyWrapper#setEnergy(double)},
 * 完全绕过 FE 包装层,实现 {@code double}(Joules)级别的瞬间注入.</p>
 */
public class MekanismEnergyAdapter implements IEnergyAdapter {

    // RFIntegration: fromRF(double) / toRF(double) — 用于 Joules↔RF 换算
    private static Class<?> rfIntegrationClass;
    private static Method fromRFMethod;
    private static Method toRFMethod;

    // IEnergyWrapper: getEnergy() / setEnergy(double) / getMaxEnergy() / acceptEnergy(EnumFacing, double, boolean)
    private static Class<?> energyWrapperClass;
    private static Method getEnergyMethod;
    private static Method setEnergyMethod;
    private static Method getMaxEnergyMethod;
    private static boolean reflectionReady = false;

    // 缓存 1 RF 对应的 Joules 数量(默认 Mekanism 0.4),避免每次重复反射调用
    private static double joulesPerRF = Double.NaN;

    public MekanismEnergyAdapter() {
        initReflection();
    }

    private static synchronized void initReflection() {
        if (reflectionReady) {
            return;
        }
        try {
            rfIntegrationClass = Class.forName("mekanism.common.integration.redstoneflux.RFIntegration");
            fromRFMethod = rfIntegrationClass.getMethod("fromRF", double.class);
            toRFMethod = rfIntegrationClass.getMethod("toRF", double.class);

            energyWrapperClass = Class.forName("mekanism.common.base.IEnergyWrapper");
            getEnergyMethod = energyWrapperClass.getMethod("getEnergy");
            setEnergyMethod = energyWrapperClass.getMethod("setEnergy", double.class);
            getMaxEnergyMethod = energyWrapperClass.getMethod("getMaxEnergy");

            reflectionReady = true;
        } catch (Exception e) {
            // 反射失败,将完全回退到标准 Forge 策略
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return blockId.startsWith("mekanism:")
            || blockId.startsWith("mekanismgenerators:")
            || blockId.startsWith("mekanismtools:");
    }

    @Override
    public long getReceiveableEnergy(TileEntity tile, IEnergyStorage cap) {
        if (reflectionReady && energyWrapperClass.isInstance(tile)) {
            try {
                double current = (Double) getEnergyMethod.invoke(tile);
                double max = (Double) getMaxEnergyMethod.invoke(tile);
                double canAddJoules = Math.max(0.0, max - current);
                if (canAddJoules > 0) {
                    return toRF(canAddJoules);
                }
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
        if (reflectionReady && energyWrapperClass.isInstance(tile)) {
            try {
                double current = (Double) getEnergyMethod.invoke(tile);
                double max = (Double) getMaxEnergyMethod.invoke(tile);
                double canAddJoules = Math.max(0.0, max - current);
                if (canAddJoules <= 0) {
                    return 0;
                }

                double joulesToAdd = fromRF((double) amount);
                double toAdd = Math.min(canAddJoules, joulesToAdd);
                if (toAdd > 0 && !simulate) {
                    setEnergyMethod.invoke(tile, current + toAdd);
                }
                return toRF(toAdd);
            } catch (Exception e) {
                // 反射失败,回退
            }
        }
        return fallbackInject(cap, amount, simulate);
    }

    private static double getJoulesPerRF() {
        if (Double.isNaN(joulesPerRF)) {
            try {
                joulesPerRF = (Double) fromRFMethod.invoke(null, 1.0);
            } catch (Exception e) {
                joulesPerRF = 0.4;
            }
        }
        return joulesPerRF;
    }

    private static double fromRF(double rf) {
        return rf * getJoulesPerRF();
    }

    private static long toRF(double joules) {
        return (long) (joules / getJoulesPerRF());
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
