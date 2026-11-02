package com.github.aeddddd.ae2enhanced.specialcrafting;

import com.github.aeddddd.ae2enhanced.observation.Observations;

/** 特殊配方/DAG 计划引擎诊断日志转发壳。 */
public final class SpecialLog {

    private static final String CHANNEL = "specialcrafting";

    private SpecialLog() {
    }

    public static boolean isEnabled() {
        return Observations.isEnabled(CHANNEL);
    }

    public static void info(String message, Object... args) {
        Observations.log("info", CHANNEL, message, args);
    }

    public static void warn(String message, Object... args) {
        Observations.log("warn", CHANNEL, message, args);
    }
}
