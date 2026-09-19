package com.github.aeddddd.ae2enhanced.diag;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 门控诊断日志：所有输出经 {@link DiagSwitch} 按子系统开关控制.
 *
 * <p>替代散落在各系统中的配置判断与 JVM 属性开关，是 {@code SpecialLog}
 * 的通用化版本。开关关闭时零格式化开销（调用方传入参数化模板）。</p>
 */
public final class DiagLog {

    private DiagLog() {
    }

    public static boolean isEnabled(String switchName) {
        return DiagSwitch.isEnabled(switchName);
    }

    public static void info(String switchName, String message, Object... args) {
        if (DiagSwitch.isEnabled(switchName)) {
            AE2Enhanced.LOGGER.info(message, args);
        }
    }

    public static void warn(String switchName, String message, Object... args) {
        if (DiagSwitch.isEnabled(switchName)) {
            AE2Enhanced.LOGGER.warn(message, args);
        }
    }
}
