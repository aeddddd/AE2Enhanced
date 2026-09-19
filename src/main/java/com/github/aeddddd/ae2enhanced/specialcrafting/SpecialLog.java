package com.github.aeddddd.ae2enhanced.specialcrafting;

import com.github.aeddddd.ae2enhanced.diag.DiagLog;
import com.github.aeddddd.ae2enhanced.diag.DiagSwitch;

/**
 * 特殊配方/DAG 计划引擎诊断日志转发壳.
 * <p>输出经 {@link DiagSwitch#SPECIAL_CRAFTING} 门控,默认关闭,
 * 运行期用 {@code /ae2e debug specialcrafting on|off} 控制.</p>
 */
public final class SpecialLog {

    private SpecialLog() {
    }

    public static boolean isEnabled() {
        return DiagLog.isEnabled(DiagSwitch.SPECIAL_CRAFTING);
    }

    public static void info(String message, Object... args) {
        DiagLog.info(DiagSwitch.SPECIAL_CRAFTING, message, args);
    }

    public static void warn(String message, Object... args) {
        DiagLog.warn(DiagSwitch.SPECIAL_CRAFTING, message, args);
    }
}
