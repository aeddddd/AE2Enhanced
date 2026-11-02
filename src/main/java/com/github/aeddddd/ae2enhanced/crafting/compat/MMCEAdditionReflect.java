package com.github.aeddddd.ae2enhanced.crafting.compat;

import java.lang.reflect.Method;

/**
 * MMCE / MMCE-addition 类的反射隔离访问.
 *
 * <p>本类常量池仅含字符串字面量与 JDK 反射类，目标 mod 未安装时所有判定返回 false。
 * 可被无条件加载的代码安全引用（反向引用 MMCE 类才是被禁止的方向）。</p>
 */
public final class MMCEAdditionReflect {

    /** mmceaddition ME 样板总成（ICraftingProvider, Long 缓冲）. */
    private static final Class<?> PATTERN_ASSEMBLY =
            find("com.github.aeddddd.mmceaddition.tile.TileMEPatternAssembly");

    /** mmceaddition 输入总成仓（纯 IItemHandler/IFluidHandler capability, Long 缓冲）. */
    private static final Class<?> INPUT_ASSEMBLY =
            find("com.github.aeddddd.mmceaddition.tile.TileInputAssembly");

    /** MMCE-CE 机械样板供应器. */
    private static final Class<?> PATTERN_PROVIDER =
            find("github.kasuminova.mmce.common.tile.MEPatternProvider");

    /** MEPatternProvider#getWorkMode（返回枚举，以 name() 字符串比较，兼容任意 MMCE 版本）. */
    private static final Method PROVIDER_GET_WORK_MODE = findWorkMode();

    private MMCEAdditionReflect() {
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findWorkMode() {
        if (PATTERN_PROVIDER == null) {
            return null;
        }
        try {
            return PATTERN_PROVIDER.getMethod("getWorkMode");
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isPatternAssembly(Object obj) {
        return PATTERN_ASSEMBLY != null && PATTERN_ASSEMBLY.isInstance(obj);
    }

    /** mmceaddition 是否可用（输入总成类存在即视为可用）. */
    public static boolean isAvailable() {
        return INPUT_ASSEMBLY != null;
    }

    public static boolean isInputAssembly(Object obj) {
        return INPUT_ASSEMBLY != null && INPUT_ASSEMBLY.isInstance(obj);
    }

    /**
     * 判定对象是否为可聚合发配的 MMCE 机械样板供应器
     * （仅 DEFAULT / ISOLATION_INPUT 工作模式；阻挡类模式不排除中间状态，不发配批量）.
     */
    public static boolean isBatchablePatternProvider(Object obj) {
        if (PATTERN_PROVIDER == null || PROVIDER_GET_WORK_MODE == null || !PATTERN_PROVIDER.isInstance(obj)) {
            return false;
        }
        try {
            Object mode = PROVIDER_GET_WORK_MODE.invoke(obj);
            if (mode instanceof Enum) {
                String name = ((Enum<?>) mode).name();
                return "DEFAULT".equals(name) || "ISOLATION_INPUT".equals(name);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
