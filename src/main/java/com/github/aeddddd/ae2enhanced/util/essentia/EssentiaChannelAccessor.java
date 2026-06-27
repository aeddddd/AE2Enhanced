package com.github.aeddddd.ae2enhanced.util.essentia;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;

/**
 * ThaumicEnergistics 源质存储通道的反射缓存助手.
 *
 * 在类加载时一次性完成所有反射,后续调用直接复用缓存对象.
 * 若 Thaumcraft / ThaumicEnergistics 未安装,所有字段为 null,AVAILABLE = false.
 */
public final class EssentiaChannelAccessor {

    public static final boolean AVAILABLE;

    private static final Class<?> ESSENTIA_STORAGE_CHANNEL_CLASS;
    private static final Method GET_STORAGE_CHANNEL_METHOD;
    private static final Method GET_INVENTORY_METHOD;
    private static final Class<?> IESSENTIA_TRANSPORT_CLASS;

    private static Object essentiaChannelInstance;

    static {
        boolean available = false;
        Class<?> essentiaClass = null;
        Method getChannel = null;
        Method getInv = null;
        Class<?> ieTransportClass = null;

        try {
            if (Loader.isModLoaded("thaumcraft") && Loader.isModLoaded("thaumicenergistics")) {
                essentiaClass = Class.forName("thaumicenergistics.api.storage.IEssentiaStorageChannel");
                getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
                getInv = appeng.api.networking.storage.IStorageGrid.class.getMethod("getInventory", IStorageChannel.class);
                ieTransportClass = Class.forName("thaumcraft.api.aspects.IEssentiaTransport");
                available = true;
            }
        } catch (Exception e) {
            // 源质系统不可用
        }

        AVAILABLE = available;
        ESSENTIA_STORAGE_CHANNEL_CLASS = essentiaClass;
        GET_STORAGE_CHANNEL_METHOD = getChannel;
        GET_INVENTORY_METHOD = getInv;
        IESSENTIA_TRANSPORT_CLASS = ieTransportClass;
    }

    private EssentiaChannelAccessor() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static Object getEssentiaChannel() throws Exception {
        if (!AVAILABLE) return null;
        if (essentiaChannelInstance == null && GET_STORAGE_CHANNEL_METHOD != null) {
            essentiaChannelInstance = GET_STORAGE_CHANNEL_METHOD.invoke(AEApi.instance().storage(), ESSENTIA_STORAGE_CHANNEL_CLASS);
        }
        return essentiaChannelInstance;
    }

    @SuppressWarnings("unchecked")
    public static IMEMonitor<?> getEssentiaInventory(IGrid grid) throws Exception {
        if (!AVAILABLE) return null;
        Object channel = getEssentiaChannel();
        if (channel == null || GET_INVENTORY_METHOD == null) return null;
        appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
        return (IMEMonitor<?>) GET_INVENTORY_METHOD.invoke(storageGrid, channel);
    }

    public static boolean isEssentiaTransport(TileEntity target) {
        if (!AVAILABLE || IESSENTIA_TRANSPORT_CLASS == null || target == null) return false;
        return IESSENTIA_TRANSPORT_CLASS.isInstance(target);
    }
}
