package com.github.aeddddd.ae2enhanced.integration.terminal.tii;

import net.minecraftforge.fml.common.Loader;

/**
 * Terminal Interaction Integration (TII) 兼容性检测类.
 * 仅通过 {@link Loader#isModLoaded(String)} 检测 TII 是否安装,不直接引用其类.
 */
public final class TiiCompat {

    private TiiCompat() {
    }

    /**
     * 判断 TII 是否已经加载.
     */
    public static boolean isLoaded() {
        return Loader.isModLoaded("terminal_interaction_integration");
    }
}
