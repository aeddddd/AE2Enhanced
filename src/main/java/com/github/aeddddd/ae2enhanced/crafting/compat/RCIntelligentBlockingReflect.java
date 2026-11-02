package com.github.aeddddd.ae2enhanced.crafting.compat;

import java.lang.reflect.Method;

/**
 * RandomComplement 智能阻挡（Intelligent Blocking）设置的反射隔离访问.
 *
 * <p>RandomComplement 通过其 MixinDualityInterface 给 AE2 DualityInterface 附加
 * {@code RCIConfigurableObject} 接口与独立的 RCConfigManager，
 * 智能阻挡设置键为 {@code RCSettings.IntelligentBlocking}，值为枚举
 * {@code IntelligentBlocking.OPEN/CLOSE}。其语义：阻挡模式开启时，
 * 相同样板在目标有物的情况下仍可继续推送（r$lastInputHash 匹配），
 * 与批量发配（同样板一次 N 份）语义兼容。</p>
 *
 * <p>本类常量池仅含字符串字面量，RandomComplement 未安装时所有判定返回 false。
 * 仅被 mmce 条件 mixin 引用，不得被无条件加载的类引用。</p>
 */
public final class RCIntelligentBlockingReflect {

    private static final Class<?> RCI_CONFIGURABLE =
            find("com.circulation.random_complement.common.interfaces.RCIConfigurableObject");

    private static final Method GET_RC_CONFIG_MANAGER;
    private static final Method GET_SETTING;
    private static final Object INTELLIGENT_BLOCKING_KEY;
    private static final Object INTELLIGENT_BLOCKING_OPEN;

    static {
        Method getMgr = null;
        Method getSetting = null;
        Object key = null;
        Object open = null;
        try {
            if (RCI_CONFIGURABLE != null) {
                getMgr = RCI_CONFIGURABLE.getMethod("r$getConfigManager");
                Class<?> rcSettings = Class.forName("com.circulation.random_complement.client.RCSettings");
                Class<?> rciConfigManager =
                        Class.forName("com.circulation.random_complement.common.interfaces.RCIConfigManager");
                getSetting = rciConfigManager.getMethod("getSetting", rcSettings);
                key = Enum.valueOf(rcSettings.asSubclass(Enum.class), "IntelligentBlocking");
                Class<?> intelligentBlocking =
                        Class.forName("com.circulation.random_complement.client.buttonsetting.IntelligentBlocking");
                open = Enum.valueOf(intelligentBlocking.asSubclass(Enum.class), "OPEN");
            }
        } catch (Throwable ignored) {
            getMgr = null;
            getSetting = null;
            key = null;
            open = null;
        }
        GET_RC_CONFIG_MANAGER = getMgr;
        GET_SETTING = getSetting;
        INTELLIGENT_BLOCKING_KEY = key;
        INTELLIGENT_BLOCKING_OPEN = open;
    }

    private RCIntelligentBlockingReflect() {
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 判定该 duality 是否开启了 RandomComplement 智能阻挡.
     * 原版接口与 ae2fc 二合一接口（内部 itemDuality）均为 DualityInterface，均可命中。
     */
    public static boolean isIntelligentBlockingOpen(Object duality) {
        if (GET_SETTING == null || !RCI_CONFIGURABLE.isInstance(duality)) {
            return false;
        }
        try {
            Object manager = GET_RC_CONFIG_MANAGER.invoke(duality);
            if (manager == null) {
                return false;
            }
            return GET_SETTING.invoke(manager, INTELLIGENT_BLOCKING_KEY) == INTELLIGENT_BLOCKING_OPEN;
        } catch (Throwable t) {
            return false;
        }
    }
}
