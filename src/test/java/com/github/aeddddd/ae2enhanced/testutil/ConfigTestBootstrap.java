package com.github.aeddddd.ae2enhanced.testutil;

import com.electronwill.nightconfig.core.CommentedConfig;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;

/**
 * 单元测试环境下的 Forge 配置引导工具.
 * <p>开发环境中 {@code ForgeConfigSpec.ConfigValue#get()} 在配置未加载时会抛出
 * {@code IllegalStateException: Cannot get config value before config is loaded}.
 * 本工具把 COMMON 配置的默认值填充到一份内存配置并挂接到 spec 上,
 * 使测试代码读取配置时得到与游戏内一致的默认值.幂等,可安全重复调用.</p>
 */
public final class ConfigTestBootstrap {

    private static volatile boolean loaded;

    private ConfigTestBootstrap() {
    }

    /**
     * 以默认值加载 COMMON 配置.幂等,可重复调用.
     */
    public static void loadDefaults() {
        if (loaded) {
            return;
        }
        synchronized (ConfigTestBootstrap.class) {
            if (loaded) {
                return;
            }
            CommentedConfig config = CommentedConfig.inMemory();
            // correct 会把缺失的键按默认值补齐
            AE2EnhancedConfig.COMMON_SPEC.correct(config);
            AE2EnhancedConfig.COMMON_SPEC.acceptConfig(config);
            loaded = true;
        }
    }
}
