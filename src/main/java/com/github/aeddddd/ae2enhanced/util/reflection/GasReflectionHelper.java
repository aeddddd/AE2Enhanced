package com.github.aeddddd.ae2enhanced.util.reflection;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Mekanism 气体系统的反射缓存助手.
 *
 * 在类加载时一次性完成所有反射(Class / Method / Field / Capability),
 * 后续调用直接复用缓存对象,消除每次 tick 的重复反射开销.
 * 若 Mekanism / MekanismEnergistics 未安装,所有字段为 null,AVAILABLE = false.
 */
public final class GasReflectionHelper {

    public static final boolean AVAILABLE;

    public static final Class<?> GAS_HANDLER_CLASS;
    public static final Class<?> GAS_STACK_CLASS;
    public static final Class<?> GAS_CLASS;
    public static final Field GAS_STACK_AMOUNT_FIELD;
    public static final Method GAS_STACK_GET_GAS_METHOD;
    public static final Method DRAW_GAS_METHOD;
    public static final Method RECEIVE_GAS_METHOD;
    public static final Class<?> GAS_STORAGE_CHANNEL_CLASS;
    public static final Method GET_STORAGE_CHANNEL_METHOD;
    public static final Method GET_INVENTORY_METHOD;
    public static final Capability<?> GAS_HANDLER_CAPABILITY;

    private static Object gasChannelInstance;

    static {
        boolean available = false;
        Class<?> gasHandlerClass = null;
        Class<?> gasStackClass = null;
        Class<?> gasClass = null;
        Field amountField = null;
        Method getGasMethod = null;
        Method drawGasMethod = null;
        Method receiveGasMethod = null;
        Class<?> gasStorageChannelClass = null;
        Method getStorageChannelMethod = null;
        Method getInventoryMethod = null;
        Capability<?> gasCapability = null;

        try {
            if (Loader.isModLoaded("mekanism") && Loader.isModLoaded("mekeng")) {
                gasHandlerClass = Class.forName("mekanism.api.gas.IGasHandler");
                gasStackClass = Class.forName("mekanism.api.gas.GasStack");
                gasClass = Class.forName("mekanism.api.gas.Gas");
                amountField = gasStackClass.getField("amount");
                getGasMethod = gasStackClass.getMethod("getGas");
                drawGasMethod = gasHandlerClass.getMethod("drawGas", EnumFacing.class, int.class, boolean.class);
                receiveGasMethod = gasHandlerClass.getMethod("receiveGas", EnumFacing.class, gasStackClass, boolean.class);

                gasStorageChannelClass = Class.forName("com.mekeng.github.common.me.storage.IGasStorageChannel");
                getStorageChannelMethod = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
                getInventoryMethod = appeng.api.networking.storage.IStorageGrid.class.getMethod("getInventory", IStorageChannel.class);

                gasCapability = (Capability<?>) Class.forName("mekanism.common.capabilities.Capabilities")
                        .getField("GAS_HANDLER_CAPABILITY").get(null);

                available = true;
            }
        } catch (Exception e) {
            // 气体系统不可用,所有字段保持 null
        }

        AVAILABLE = available;
        GAS_HANDLER_CLASS = gasHandlerClass;
        GAS_STACK_CLASS = gasStackClass;
        GAS_CLASS = gasClass;
        GAS_STACK_AMOUNT_FIELD = amountField;
        GAS_STACK_GET_GAS_METHOD = getGasMethod;
        DRAW_GAS_METHOD = drawGasMethod;
        RECEIVE_GAS_METHOD = receiveGasMethod;
        GAS_STORAGE_CHANNEL_CLASS = gasStorageChannelClass;
        GET_STORAGE_CHANNEL_METHOD = getStorageChannelMethod;
        GET_INVENTORY_METHOD = getInventoryMethod;
        GAS_HANDLER_CAPABILITY = gasCapability;
    }

    private GasReflectionHelper() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    @SuppressWarnings("unchecked")
    public static Object getGasHandler(TileEntity target, EnumFacing side) {
        if (!AVAILABLE || GAS_HANDLER_CAPABILITY == null) return null;
        return target.getCapability((Capability<Object>) GAS_HANDLER_CAPABILITY, side);
    }

    public static Object getGasChannel() throws Exception {
        if (!AVAILABLE) return null;
        if (gasChannelInstance == null && GET_STORAGE_CHANNEL_METHOD != null) {
            gasChannelInstance = GET_STORAGE_CHANNEL_METHOD.invoke(AEApi.instance().storage(), GAS_STORAGE_CHANNEL_CLASS);
        }
        return gasChannelInstance;
    }

    @SuppressWarnings("unchecked")
    public static IMEMonitor<?> getGasInventory(IGrid grid) throws Exception {
        if (!AVAILABLE) return null;
        Object channel = getGasChannel();
        if (channel == null || GET_INVENTORY_METHOD == null) return null;
        appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
        return (IMEMonitor<?>) GET_INVENTORY_METHOD.invoke(storageGrid, channel);
    }

    public static Object createGasStack(Object gas, int amount) throws Exception {
        if (!AVAILABLE || GAS_STACK_CLASS == null || GAS_CLASS == null) return null;
        return GAS_STACK_CLASS.getConstructor(GAS_CLASS, int.class).newInstance(gas, amount);
    }

    public static int getGasAmount(Object gasStack) throws Exception {
        if (!AVAILABLE || gasStack == null || GAS_STACK_AMOUNT_FIELD == null) return 0;
        return GAS_STACK_AMOUNT_FIELD.getInt(gasStack);
    }

    public static Object getGasType(Object gasStack) throws Exception {
        if (!AVAILABLE || gasStack == null || GAS_STACK_GET_GAS_METHOD == null) return null;
        return GAS_STACK_GET_GAS_METHOD.invoke(gasStack);
    }

    public static Object drawGas(Object handler, EnumFacing side, int amount, boolean doTransfer) throws Exception {
        if (!AVAILABLE || handler == null || DRAW_GAS_METHOD == null) return null;
        return DRAW_GAS_METHOD.invoke(handler, side, amount, doTransfer);
    }

    public static int receiveGas(Object handler, EnumFacing side, Object gasStack, boolean doTransfer) throws Exception {
        if (!AVAILABLE || handler == null || gasStack == null || RECEIVE_GAS_METHOD == null) return 0;
        Object result = RECEIVE_GAS_METHOD.invoke(handler, side, gasStack, doTransfer);
        return result instanceof Number ? ((Number) result).intValue() : 0;
    }
}
