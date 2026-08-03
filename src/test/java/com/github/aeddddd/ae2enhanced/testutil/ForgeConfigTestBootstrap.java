package com.github.aeddddd.ae2enhanced.testutil;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;

/**
 * 单元测试环境下的 Forge 配置引导工具.
 * <p>{@code ForgeConfigSpec.ConfigValue#get()} 在配置加载前会抛出
 * {@code IllegalStateException: Cannot get config value before config is loaded}.
 * 任何读取 {@link AE2EnhancedConfig#COMMON} 配置项的被测代码,都必须先调用
 * {@link #bootstrap()}（默认配置）或 {@link #bootstrap(String)}（自定义 TOML 内容）.
 * 整个过程幂等,可安全重复调用.</p>
 */
public final class ForgeConfigTestBootstrap {

    private static volatile boolean bootstrapped;

    private ForgeConfigTestBootstrap() {
    }

    /**
     * 以空配置（全部使用默认值）加载 COMMON 配置.幂等,可重复调用.
     */
    public static void bootstrap() {
        bootstrap("");
    }

    /**
     * 以指定 TOML 内容加载 COMMON 配置；未覆盖的键使用默认值.仅首次调用生效.
     */
    public static synchronized void bootstrap(String tomlContent) {
        if (bootstrapped) {
            return;
        }
        CommentedConfig config = new TomlParser().parse(tomlContent);
        AE2EnhancedConfig.COMMON_SPEC.setConfig(config);
        bootstrapped = true;
    }
}
