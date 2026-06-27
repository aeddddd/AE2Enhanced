package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Method;

/**
 * Modular Machinery (CE) 能源仓专用适配器.
 *
 * <p>MMCE 的 {@code TileEnergyHatch} 虽然实现了标准 {@link IEnergyStorage}，
 * 但其 {@code receiveEnergy(int)} 受 {@code size.transferLimit} 单次限制，
 * 高等级能源仓（如 ULTIMATE 默认 131072 RF/t）在标准接口下会被压成每 tick 仅注入一次上限。</p>
 *
 * <p>此适配器通过反射调用 {@code IEnergyHandlerAsync#receiveEnergy(long)}，
 * 该 long 级接口只检查剩余容量，不受 transferLimit 限制，可一次性充满。</p>
 *
 * <p>仅对安装了 modularmachinery 的环境生效；未安装时该类不会被加载。</p>
 */
public class MMCEEnergyAdapter implements IEnergyAdapter {

    private static Class<?> asyncHandlerClass;
    private static Method getCurrentEnergyMethod;
    private static Method getMaxEnergyMethod;
    private static Method receiveEnergyMethod;
    private static boolean reflectionReady = false;

    public MMCEEnergyAdapter() {
        initReflection();
    }

    private static synchronized void initReflection() {
        if (reflectionReady) {
            return;
        }
        try {
            asyncHandlerClass = Class.forName("hellfirepvp.modularmachinery.common.util.IEnergyHandlerAsync");
            getCurrentEnergyMethod = asyncHandlerClass.getMethod("getCurrentEnergy");
            getMaxEnergyMethod = asyncHandlerClass.getMethod("getMaxEnergy");
            receiveEnergyMethod = asyncHandlerClass.getMethod("receiveEnergy", long.class);
            reflectionReady = true;
        } catch (Exception ignored) {
            // 反射失败：将完全回退到标准 Forge 策略
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return blockId != null && blockId.startsWith("modularmachinery:blockenergy");
    }

    @Override
    public long getReceiveableEnergy(TileEntity tile, IEnergyStorage cap) {
        if (!reflectionReady || tile == null || !asyncHandlerClass.isInstance(tile)) {
            return fallbackReceiveable(cap);
        }
        try {
            long current = (Long) getCurrentEnergyMethod.invoke(tile);
            long max = (Long) getMaxEnergyMethod.invoke(tile);
            return Math.max(0L, max - current);
        } catch (Exception e) {
            return fallbackReceiveable(cap);
        }
    }

    @Override
    public long injectEnergy(TileEntity tile, IEnergyStorage cap, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (!reflectionReady || tile == null || !asyncHandlerClass.isInstance(tile)) {
            return fallbackInject(cap, amount, simulate);
        }
        try {
            long current = (Long) getCurrentEnergyMethod.invoke(tile);
            long max = (Long) getMaxEnergyMethod.invoke(tile);
            long free = max - current;
            long accept = Math.min(amount, free);
            if (accept <= 0) {
                return 0;
            }
            if (simulate) {
                return accept;
            }
            boolean success = (Boolean) receiveEnergyMethod.invoke(tile, accept);
            return success ? accept : 0L;
        } catch (Exception e) {
            return fallbackInject(cap, amount, simulate);
        }
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
        return cap.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate);
    }
}
