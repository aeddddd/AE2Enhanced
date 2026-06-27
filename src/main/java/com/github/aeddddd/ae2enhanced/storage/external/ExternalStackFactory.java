package com.github.aeddddd.ae2enhanced.storage.external;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.reflect.Method;

/**
 * 外部存储通道堆叠创建工厂.
 * <p>
 * 通过反射调用 {@link IStorageChannel#createFromNBT(NBTTagCompound)} 与
 * {@link IStorageChannel#createStack(Object)},避免在通用适配器中直接依赖外部通道的具体返回类型.
 * </p>
 */
public final class ExternalStackFactory {

    private static final Method CREATE_FROM_NBT;
    private static final Method CREATE_STACK;

    static {
        Method nbt = null;
        Method stack = null;
        try {
            nbt = IStorageChannel.class.getMethod("createFromNBT", NBTTagCompound.class);
            stack = IStorageChannel.class.getMethod("createStack", Object.class);
        } catch (NoSuchMethodException e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to cache IStorageChannel reflection methods", e);
        }
        CREATE_FROM_NBT = nbt;
        CREATE_STACK = stack;
    }

    private ExternalStackFactory() {
    }

    /**
     * 通过反射调用外部通道的 {@code createFromNBT} 创建堆叠.
     *
     * @param channel 外部存储通道
     * @param nbt     包含资源数量的 NBT
     * @return 创建的 AE 堆叠,失败时返回 null
     */
    @SuppressWarnings("unchecked")
    public static IAEStack createFromNBT(IStorageChannel<?> channel, NBTTagCompound nbt) {
        if (CREATE_FROM_NBT == null || channel == null) {
            return null;
        }
        try {
            return (IAEStack) CREATE_FROM_NBT.invoke(channel, nbt);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to create external stack from NBT for channel {}", channel.getClass().getName(), e);
            return null;
        }
    }

    /**
     * 通过反射调用外部通道的 {@code createStack} 创建堆叠.
     *
     * @param channel 外部存储通道
     * @param input   输入对象(通常为 {@link Number})
     * @return 创建的 AE 堆叠,失败时返回 null
     */
    @SuppressWarnings("unchecked")
    public static IAEStack createStack(IStorageChannel<?> channel, Object input) {
        if (CREATE_STACK == null || channel == null) {
            return null;
        }
        try {
            return (IAEStack) CREATE_STACK.invoke(channel, input);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to create external stack from input {} for channel {}", input, channel.getClass().getName(), e);
            return null;
        }
    }
}
