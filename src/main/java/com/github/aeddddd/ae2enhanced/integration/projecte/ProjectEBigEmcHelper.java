package com.github.aeddddd.ae2enhanced.integration.projecte;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;

/**
 * 通过反射访问被 Mixin 注入到 ProjectE KnowledgeImpl.DefaultImpl 中的 BigInteger EMC 方法。
 *
 * <p>额外处理 {@code TransmutationOffline$*} 匿名包装类：这类提供者本身没有被 Mixin 增强，
 * 但它内部包装了一个 {@code DefaultImpl} 实例，因此解包后再进行反射即可读取真实的 BigInteger EMC。</p>
 */
public final class ProjectEBigEmcHelper {

    private static final String METHOD_GET_EMC_BIG = "ae2e$getEmcBig";
    private static final String METHOD_ADD_EMC = "ae2e$addEmc";
    private static final String METHOD_SUBTRACT_EMC = "ae2e$subtractEmc";

    private static Class<?> cachedProviderClass;
    private static Method getEmcBigMethod;
    private static Method addEmcMethod;
    private static Method subtractEmcMethod;

    private ProjectEBigEmcHelper() {}

    /**
     * 如果传入的提供者是 {@code TransmutationOffline} 生成的匿名包装类，
     * 则返回其内部包装的 {@code DefaultImpl} 实例；否则原样返回。
     */
    @Nullable
    private static Object unwrapProvider(@Nullable Object provider) {
        if (provider == null) {
            return null;
        }
        Class<?> clazz = provider.getClass();
        String name = clazz.getName();
        if (!name.startsWith("moze_intel.projecte.impl.TransmutationOffline$")) {
            return provider;
        }
        try {
            // 匿名类通过合成字段捕获了 immutableCopy 的参数 toCopy
            // 字段名在不同编译器/混淆环境下可能是 "toCopy" 或 "val$toCopy"，这里统一遍历查找
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getName().contains("toCopy")) {
                    field.setAccessible(true);
                    Object wrapped = field.get(provider);
                    if (wrapped != null) {
                        return wrapped;
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to unwrap TransmutationOffline provider {}", clazz, e);
        }
        return provider;
    }

    private static void ensureMethods(Class<?> clazz) {
        if (getEmcBigMethod != null && cachedProviderClass != null && cachedProviderClass.isAssignableFrom(clazz)) {
            return;
        }
        try {
            getEmcBigMethod = clazz.getMethod(METHOD_GET_EMC_BIG);
            getEmcBigMethod.setAccessible(true);
            addEmcMethod = clazz.getMethod(METHOD_ADD_EMC, long.class);
            addEmcMethod.setAccessible(true);
            subtractEmcMethod = clazz.getMethod(METHOD_SUBTRACT_EMC, long.class);
            subtractEmcMethod.setAccessible(true);
            cachedProviderClass = clazz;
        } catch (NoSuchMethodException e) {
            // 预期内的未增强提供者（如旧版本数据、第三方实现）不会携带 BigInteger 方法，
            // 使用 DEBUG 级别避免日志刷屏；调用方会回退到 long 访问。
            AE2Enhanced.LOGGER.debug("[AE2E] ProjectE provider {} does not expose BigInteger EMC methods", clazz);
            getEmcBigMethod = null;
            addEmcMethod = null;
            subtractEmcMethod = null;
            cachedProviderClass = null;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Unexpected error locating ProjectE BigInteger EMC methods on {}", clazz, e);
            getEmcBigMethod = null;
            addEmcMethod = null;
            subtractEmcMethod = null;
            cachedProviderClass = null;
        }
    }

    public static boolean isBigEmcProvider(Object provider) {
        if (provider == null) return false;
        Object unwrapped = unwrapProvider(provider);
        ensureMethods(unwrapped.getClass());
        return getEmcBigMethod != null;
    }

    @Nonnull
    public static BigInteger getEmcBig(@Nonnull Object provider) {
        Object unwrapped = unwrapProvider(provider);
        ensureMethods(unwrapped.getClass());
        if (getEmcBigMethod == null) return BigInteger.ZERO;
        try {
            Object result = getEmcBigMethod.invoke(unwrapped);
            return result instanceof BigInteger ? (BigInteger) result : BigInteger.ZERO;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to get BigInteger EMC", e);
            return BigInteger.ZERO;
        }
    }

    public static void addEmc(@Nonnull Object provider, long value) {
        if (value <= 0) return;
        // 写入操作不解包离线提供者：TransmutationOffline$* 是只读快照，修改它不应影响缓存。
        ensureMethods(provider.getClass());
        if (addEmcMethod == null) return;
        try {
            addEmcMethod.invoke(provider, value);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to add BigInteger EMC {}", value, e);
        }
    }

    public static void subtractEmc(@Nonnull Object provider, long value) {
        if (value <= 0) return;
        // 写入操作不解包离线提供者：TransmutationOffline$* 是只读快照，修改它不应影响缓存。
        ensureMethods(provider.getClass());
        if (subtractEmcMethod == null) return;
        try {
            subtractEmcMethod.invoke(provider, value);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to subtract BigInteger EMC {}", value, e);
        }
    }

    /**
     * 减少玩家 BigInteger EMC，支持超过 Long.MAX_VALUE 的数量。
     */
    public static void subtractEmc(@Nonnull Object provider, @Nonnull BigInteger value) {
        if (value.signum() <= 0) return;
        // 写入操作不解包离线提供者：TransmutationOffline$* 是只读快照，修改它不应影响缓存。
        ensureMethods(provider.getClass());
        if (subtractEmcMethod == null) return;
        BigInteger chunk = BigInteger.valueOf(Long.MAX_VALUE);
        try {
            while (value.compareTo(chunk) > 0) {
                subtractEmcMethod.invoke(provider, Long.MAX_VALUE);
                value = value.subtract(chunk);
            }
            subtractEmcMethod.invoke(provider, value.longValue());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to subtract BigInteger EMC {}", value, e);
        }
    }
}
