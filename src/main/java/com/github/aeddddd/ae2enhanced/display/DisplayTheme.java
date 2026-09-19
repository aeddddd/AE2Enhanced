package com.github.aeddddd.ae2enhanced.display;

/**
 * 屏幕底色主题(由边框方块颜色决定).
 */
public enum DisplayTheme {
    DARK,
    LIGHT;

    public static DisplayTheme byOrdinal(int ord) {
        return ord == 1 ? LIGHT : DARK;
    }
}
