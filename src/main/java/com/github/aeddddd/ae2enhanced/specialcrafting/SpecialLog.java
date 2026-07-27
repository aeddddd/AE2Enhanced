package com.github.aeddddd.ae2enhanced.specialcrafting;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;

/**
 * 特殊配方诊断日志门控:仅在配置 debug.debugMode = true 时输出.
 * 配置未加载(单元测试等环境)时静默.
 */
public final class SpecialLog {

    private SpecialLog() {
    }

    public static boolean isEnabled() {
        try {
            return AE2EnhancedConfig.COMMON.debugMode.get();
        } catch (IllegalStateException e) {
            // 配置未加载(测试环境)
            return false;
        }
    }

    public static void info(String message, Object... args) {
        if (isEnabled()) {
            AE2Enhanced.LOGGER.info(message, args);
        }
    }
}
