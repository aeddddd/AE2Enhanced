package com.github.aeddddd.ae2enhanced.integration.projecte;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ProjectE API 反射封装.
 *
 * <p>所有 ProjectE 类/方法均通过反射访问,确保未安装 ProjectE 时不会触发类加载.</p>
 */
public final class ProjectEHelper {

    private ProjectEHelper() {}

    private static boolean initialized = false;
    private static boolean available = false;

    // ProjectEAPI
    private static Class<?> projectEAPIClass;
    private static Method getEMCProxyMethod;
    private static Method getTransmutationProxyMethod;

    // IEMCProxy
    private static Class<?> emcProxyClass;
    private static Method emcGetValueMethod;

    // ITransmutationProxy
    private static Class<?> transmutationProxyClass;
    private static Method getKnowledgeProviderForMethod;

    // IKnowledgeProvider
    private static Class<?> knowledgeProviderClass;
    private static Method hasKnowledgeMethod;
    private static Method getKnowledgeMethod;
    private static Method getEmcMethod;
    private static Method setEmcMethod;
    private static Method syncMethod;

    // Events
    private static Class<?> playerKnowledgeChangeEventClass;
    private static Class<?> emcRemapEventClass;

    public static boolean isAvailable() {
        ensureInit();
        return available;
    }

    private static void ensureInit() {
        if (initialized) return;
        initialized = true;
        if (!Loader.isModLoaded("projecte")) {
            available = false;
            return;
        }
        try {
            projectEAPIClass = Class.forName("moze_intel.projecte.api.ProjectEAPI");
            getEMCProxyMethod = projectEAPIClass.getMethod("getEMCProxy");
            getTransmutationProxyMethod = projectEAPIClass.getMethod("getTransmutationProxy");

            emcProxyClass = Class.forName("moze_intel.projecte.api.proxy.IEMCProxy");
            emcGetValueMethod = emcProxyClass.getMethod("getValue", ItemStack.class);

            transmutationProxyClass = Class.forName("moze_intel.projecte.api.proxy.ITransmutationProxy");
            getKnowledgeProviderForMethod = transmutationProxyClass.getMethod("getKnowledgeProviderFor", UUID.class);

            knowledgeProviderClass = Class.forName("moze_intel.projecte.api.capabilities.IKnowledgeProvider");
            hasKnowledgeMethod = knowledgeProviderClass.getMethod("hasKnowledge", ItemStack.class);
            getKnowledgeMethod = knowledgeProviderClass.getMethod("getKnowledge");
            getEmcMethod = knowledgeProviderClass.getMethod("getEmc");
            setEmcMethod = knowledgeProviderClass.getMethod("setEmc", long.class);
            syncMethod = knowledgeProviderClass.getMethod("sync", net.minecraft.entity.player.EntityPlayerMP.class);

            playerKnowledgeChangeEventClass = Class.forName("moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent");
            emcRemapEventClass = Class.forName("moze_intel.projecte.api.event.EMCRemapEvent");

            available = true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to initialize ProjectE reflection helper", e);
            available = false;
        }
    }

    /**
     * 获取物品 EMC 值.
     *
     * @param stack 物品堆
     * @return EMC 值;失败或禁用时返回 0
     */
    public static long getEmcValue(@Nonnull ItemStack stack) {
        if (!isAvailable() || stack.isEmpty()) return 0;
        try {
            Object proxy = getEMCProxyMethod.invoke(null);
            Object result = emcGetValueMethod.invoke(proxy, stack);
            return result instanceof Long ? (Long) result : 0L;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to get EMC value for {}", stack, e);
            return 0;
        }
    }

    /**
     * 获取指定 UUID 玩家的知识提供者.
     *
     * @param uuid 玩家 UUID
     * @return IKnowledgeProvider 实例;失败时返回 null
     */
    @Nullable
    public static Object getKnowledgeProvider(@Nullable UUID uuid) {
        if (!isAvailable() || uuid == null) return null;
        try {
            Object proxy = getTransmutationProxyMethod.invoke(null);
            return getKnowledgeProviderForMethod.invoke(proxy, uuid);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to get knowledge provider for {}", uuid, e);
            return null;
        }
    }

    /**
     * 玩家是否学习过某物品.
     */
    public static boolean hasKnowledge(@Nullable Object provider, @Nonnull ItemStack stack) {
        if (!isAvailable() || provider == null || stack.isEmpty()) return false;
        try {
            Object result = hasKnowledgeMethod.invoke(provider, stack);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to check knowledge", e);
            return false;
        }
    }

    /**
     * 获取玩家已学知识列表.
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    public static List<ItemStack> getKnowledge(@Nullable Object provider) {
        if (!isAvailable() || provider == null) return Collections.emptyList();
        try {
            Object result = getKnowledgeMethod.invoke(provider);
            if (result instanceof List) {
                return (List<ItemStack>) result;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to get knowledge list", e);
        }
        return Collections.emptyList();
    }

    /**
     * 获取玩家当前 EMC 余额.
     */
    public static long getEmc(@Nullable Object provider) {
        if (!isAvailable() || provider == null) return 0;
        try {
            Object result = getEmcMethod.invoke(provider);
            return result instanceof Long ? (Long) result : 0L;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to get player EMC", e);
            return 0;
        }
    }

    /**
     * 设置玩家 EMC 余额.
     */
    public static void setEmc(@Nullable Object provider, long emc) {
        if (!isAvailable() || provider == null) return;
        try {
            setEmcMethod.invoke(provider, emc);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to set player EMC", e);
        }
    }

    /**
     * 获取玩家 BigInteger EMC 余额（若 Mixin 未生效则回退到 long）。
     */
    @Nonnull
    public static BigInteger getEmcBig(@Nullable Object provider) {
        if (!isAvailable() || provider == null) return BigInteger.ZERO;
        if (ProjectEBigEmcHelper.isBigEmcProvider(provider)) {
            return ProjectEBigEmcHelper.getEmcBig(provider);
        }
        return BigInteger.valueOf(getEmc(provider));
    }

    /**
     * 设置玩家 BigInteger EMC 余额。
     */
    public static void setEmcBig(@Nullable Object provider, @Nullable BigInteger emc) {
        if (!isAvailable() || provider == null) return;
        if (ProjectEBigEmcHelper.isBigEmcProvider(provider)) {
            try {
                Method m = provider.getClass().getMethod("ae2e$setEmcBig", BigInteger.class);
                m.invoke(provider, emc == null ? BigInteger.ZERO : emc);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to set BigInteger EMC", e);
            }
        } else {
            setEmc(provider, emc == null ? 0L : emc.longValue());
        }
    }

    /**
     * 增加玩家 BigInteger EMC。
     */
    public static void addEmc(@Nullable Object provider, long value) {
        if (!isAvailable() || provider == null || value <= 0) return;
        if (ProjectEBigEmcHelper.isBigEmcProvider(provider)) {
            ProjectEBigEmcHelper.addEmc(provider, value);
        } else {
            setEmc(provider, getEmc(provider) + value);
        }
    }

    /**
     * 减少玩家 BigInteger EMC。
     */
    public static void subtractEmc(@Nullable Object provider, long value) {
        if (!isAvailable() || provider == null || value <= 0) return;
        if (ProjectEBigEmcHelper.isBigEmcProvider(provider)) {
            ProjectEBigEmcHelper.subtractEmc(provider, value);
        } else {
            setEmc(provider, getEmc(provider) - value);
        }
    }

    /**
     * 减少玩家 BigInteger EMC，支持超过 Long.MAX_VALUE 的数量。
     */
    public static void subtractEmcBig(@Nullable Object provider, @Nullable BigInteger value) {
        if (!isAvailable() || provider == null || value == null || value.signum() <= 0) return;
        if (ProjectEBigEmcHelper.isBigEmcProvider(provider)) {
            ProjectEBigEmcHelper.subtractEmc(provider, value);
        } else {
            BigInteger remaining = value;
            long current = getEmc(provider);
            BigInteger chunk = BigInteger.valueOf(Long.MAX_VALUE);
            while (remaining.compareTo(chunk) > 0) {
                current -= Long.MAX_VALUE;
                remaining = remaining.subtract(chunk);
            }
            current -= remaining.longValue();
            setEmc(provider, current);
        }
    }

    /**
     * 同步知识提供者到在线玩家.
     */
    public static void sync(@Nullable Object provider, @Nullable net.minecraft.entity.player.EntityPlayerMP player) {
        if (!isAvailable() || provider == null || player == null) return;
        try {
            syncMethod.invoke(provider, player);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to sync knowledge provider", e);
        }
    }

    /**
     * 获取 ProjectE 事件类(供事件总线反射订阅).
     */
    @Nullable
    public static Class<?> getPlayerKnowledgeChangeEventClass() {
        ensureInit();
        return playerKnowledgeChangeEventClass;
    }

    @Nullable
    public static Class<?> getEmcRemapEventClass() {
        ensureInit();
        return emcRemapEventClass;
    }

    /**
     * 从 PlayerKnowledgeChangeEvent 获取玩家 UUID.
     */
    @Nullable
    public static UUID getPlayerUUIDFromKnowledgeEvent(@Nullable Object event) {
        if (event == null || playerKnowledgeChangeEventClass == null
                || !playerKnowledgeChangeEventClass.isInstance(event)) {
            return null;
        }
        try {
            java.lang.reflect.Method m = playerKnowledgeChangeEventClass.getMethod("getPlayerUUID");
            Object result = m.invoke(event);
            return result instanceof UUID ? (UUID) result : null;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to get player UUID from knowledge event", e);
            return null;
        }
    }
}
