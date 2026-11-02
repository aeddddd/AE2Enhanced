package com.github.aeddddd.ae2enhanced.dimension;

import java.util.Locale;

/** 玩家默认进入目标；团队与个人维度并存，切换不移动世界。 */
public enum DimensionPreference {
    AUTO,
    TEAM,
    PERSONAL;

    public static DimensionPreference parse(String value) {
        if (value == null) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return AUTO;
        }
    }
}
