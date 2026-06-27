package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Tech Reborn 专用能量适配器.
 *
 * <p>Tech Reborn 机器内部以 EU 存储能量,通过 RebornCore 的
 * {@code IEnergyInterfaceTile} 暴露 {@link IEnergyStorage} capability.
 * 标准 Forge 接口每次 {@code receiveEnergy} 会把 RF 按 {@code euPerFU} 取整为 EU,
 * 并受 {@code getMaxInput()} 单次限制,大能量注入时需要反复循环.</p>
 *
 * <p>此适配器直接反射调用 {@code IEnergyInterfaceTile#getEnergy() / addEnergy(double, boolean)}
 * 写入 EU,避免 RF↔EU 取整损失与单次输入上限,实现瞬间满充.</p>
 *
 * <p>安全回退：反射失败或目标不可接收能量时回退到标准 Forge 策略.</p>
 */
public class TechRebornEnergyAdapter implements IEnergyAdapter {

    private static Class<?> energyInterfaceClass;
    private static Method getEnergyMethod;
    private static Method getMaxPowerMethod;
    private static Method addEnergyMethod;
    private static Method canAcceptEnergyMethod;
    private static Field euPerFUField;
    private static boolean reflectionReady = false;

    public TechRebornEnergyAdapter() {
        initReflection();
    }

    private static synchronized void initReflection() {
        if (reflectionReady) {
            return;
        }
        try {
            energyInterfaceClass = Class.forName("reborncore.api.power.IEnergyInterfaceTile");
            getEnergyMethod = energyInterfaceClass.getMethod("getEnergy");
            getMaxPowerMethod = energyInterfaceClass.getMethod("getMaxPower");
            addEnergyMethod = energyInterfaceClass.getMethod("addEnergy", double.class, boolean.class);
            canAcceptEnergyMethod = energyInterfaceClass.getMethod("canAcceptEnergy", EnumFacing.class);

            Class<?> configClass = Class.forName("reborncore.common.RebornCoreConfig");
            euPerFUField = configClass.getField("euPerFU");

            reflectionReady = true;
        } catch (Exception e) {
            // 反射失败，将完全回退到标准 Forge 策略
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return blockId != null && blockId.startsWith("techreborn:");
    }

    @Override
    public long getReceiveableEnergy(TileEntity tile, IEnergyStorage cap) {
        if (!reflectionReady || tile == null || !energyInterfaceClass.isInstance(tile)) {
            return fallbackReceiveable(cap);
        }
        try {
            if (!canAcceptEnergyFromAnySide(tile)) {
                return 0;
            }
            double current = (Double) getEnergyMethod.invoke(tile);
            double max = (Double) getMaxPowerMethod.invoke(tile);
            double freeEU = Math.max(0.0, max - current);
            return (long) (freeEU * getEuPerFU());
        } catch (Exception e) {
            return fallbackReceiveable(cap);
        }
    }

    @Override
    public long injectEnergy(TileEntity tile, IEnergyStorage cap, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (!reflectionReady || tile == null || !energyInterfaceClass.isInstance(tile)) {
            return fallbackInject(cap, amount, simulate);
        }
        try {
            if (!canAcceptEnergyFromAnySide(tile)) {
                return 0;
            }
            double euPerFU = getEuPerFU();
            if (euPerFU <= 0.0) {
                return fallbackInject(cap, amount, simulate);
            }
            double euToAdd = (double) amount / euPerFU;
            double euReceived = (Double) addEnergyMethod.invoke(tile, euToAdd, simulate);
            return (long) (euReceived * euPerFU);
        } catch (Exception e) {
            return fallbackInject(cap, amount, simulate);
        }
    }

    private static boolean canAcceptEnergyFromAnySide(TileEntity tile) throws Exception {
        for (EnumFacing facing : EnumFacing.values()) {
            if ((Boolean) canAcceptEnergyMethod.invoke(tile, facing)) {
                return true;
            }
        }
        return false;
    }

    private static double getEuPerFU() {
        try {
            if (euPerFUField != null) {
                return ((Integer) euPerFUField.get(null)).doubleValue();
            }
        } catch (Exception e) {
            // fall through
        }
        return 4.0;
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
