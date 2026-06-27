package com.github.aeddddd.ae2enhanced.util.memorycard.handler.enderio;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Ender IO 导管的配置复制粘贴 Handler.
 * 整根复制：遍历 bundle 中所有 conduit 的所有 6 个面的连接设置.
 */
public class EnderIOConduitHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;
    private static final Class<?> CONDUIT_BUNDLE_CLASS;
    private static final Method GET_SERVER_CONDUITS;
    private static final Method BUNDLE_DIRTY;

    private static final Class<?> SERVER_CONDUIT_CLASS;
    private static final Method WRITE_CONNECTION_SETTINGS;
    private static final Method READ_CONDUIT_SETTINGS;

    static {
        boolean available = false;
        Class<?> conduitBundleClass = null;
        Method getServerConduits = null;
        Method bundleDirty = null;

        Class<?> serverConduitClass = null;
        Method writeConnectionSettings = null;
        Method readConduitSettings = null;

        try {
            if (Loader.isModLoaded("enderio")) {
                conduitBundleClass = Class.forName("crazypants.enderio.base.conduit.IConduitBundle");
                getServerConduits = conduitBundleClass.getMethod("getServerConduits");
                bundleDirty = conduitBundleClass.getMethod("dirty");

                serverConduitClass = Class.forName("crazypants.enderio.base.conduit.IServerConduit");
                writeConnectionSettings = serverConduitClass.getMethod("writeConnectionSettingsToNBT", EnumFacing.class, NBTTagCompound.class);
                readConduitSettings = serverConduitClass.getMethod("readConduitSettingsFromNBT", EnumFacing.class, NBTTagCompound.class);

                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize EIO conduit reflection for UMC", e);
        }

        AVAILABLE = available;
        CONDUIT_BUNDLE_CLASS = conduitBundleClass;
        GET_SERVER_CONDUITS = getServerConduits;
        BUNDLE_DIRTY = bundleDirty;

        SERVER_CONDUIT_CLASS = serverConduitClass;
        WRITE_CONNECTION_SETTINGS = writeConnectionSettings;
        READ_CONDUIT_SETTINGS = readConduitSettings;
    }

    @Override
    public boolean canHandle(Object target) {
        return AVAILABLE && target != null && CONDUIT_BUNDLE_CLASS.isInstance(target);
    }

    @Override
    @SuppressWarnings("unchecked")
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            Collection<Object> conduits = (Collection<Object>) GET_SERVER_CONDUITS.invoke(tile);
            if (conduits == null || conduits.isEmpty()) {
                return output;
            }

            int conduitIndex = 0;
            for (Object conduit : conduits) {
                NBTTagCompound conduitNbt = new NBTTagCompound();
                boolean hasData = false;

                for (EnumFacing face : EnumFacing.values()) {
                    NBTTagCompound faceNbt = new NBTTagCompound();
                    boolean written = (Boolean) WRITE_CONNECTION_SETTINGS.invoke(conduit, face, faceNbt);
                    if (written && !faceNbt.isEmpty()) {
                        conduitNbt.setTag(face.getName(), faceNbt);
                        hasData = true;
                    }
                }

                if (hasData) {
                    // 使用 conduit 类名作为 key,避免不同类型 conduit 冲突
                    String conduitKey = conduit.getClass().getName() + "_" + conduitIndex;
                    output.setTag(conduitKey, conduitNbt);
                }
                conduitIndex++;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy EIO conduit config", e);
        }

        return output;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            Collection<Object> conduits = (Collection<Object>) GET_SERVER_CONDUITS.invoke(tile);
            if (conduits == null || conduits.isEmpty()) {
                return PasteResult.INVALID_MACHINE;
            }

            // 构建类名到 conduit 的映射
            java.util.Map<String, Object> conduitMap = new java.util.HashMap<>();
            int idx = 0;
            for (Object conduit : conduits) {
                String key = conduit.getClass().getName() + "_" + idx;
                conduitMap.put(key, conduit);
                idx++;
            }

            boolean anyApplied = false;
            for (String key : data.getKeySet()) {
                Object conduit = conduitMap.get(key);
                if (conduit == null) {
                    // 尝试按类名前缀匹配(处理注册顺序变化)
                    String className = key.substring(0, key.lastIndexOf('_'));
                    for (Object c : conduits) {
                        if (c.getClass().getName().equals(className)) {
                            conduit = c;
                            break;
                        }
                    }
                }

                if (conduit == null) continue;

                NBTTagCompound conduitNbt = data.getCompoundTag(key);
                for (String faceName : conduitNbt.getKeySet()) {
                    EnumFacing face = EnumFacing.byName(faceName);
                    if (face == null) continue;

                    NBTTagCompound faceNbt = conduitNbt.getCompoundTag(faceName);
                    READ_CONDUIT_SETTINGS.invoke(conduit, face, faceNbt);
                }
                anyApplied = true;
            }

            if (anyApplied) {
                BUNDLE_DIRTY.invoke(tile);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste EIO conduit config", e);
            return PasteResult.FAILED;
        }
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}
