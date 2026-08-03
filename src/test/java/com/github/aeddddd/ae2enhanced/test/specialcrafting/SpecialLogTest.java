package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.electronwill.nightconfig.toml.TomlParser;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialLog;

/**
 * {@link SpecialLog} 单元测试:诊断日志的配置门控.
 * <p>配置加载是 JVM 级动作,本类显式重设配置保证与其他测试类无关的顺序稳定性,
 * 测试顺序固定为先验证关闭、再验证开启.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SpecialLogTest {

    /** 以指定 TOML 内容重设 COMMON 配置(重复调用安全). */
    private static void loadConfig(String toml) {
        AE2EnhancedConfig.COMMON_SPEC.setConfig(new TomlParser().parse(toml));
    }

    /** debug.debugMode = false 时门控关闭,info 静默通过且不抛异常. */
    @Test
    @Order(1)
    void testDisabledWhenDebugModeFalse() {
        loadConfig("");

        assertThat(SpecialLog.isEnabled()).isFalse();
        assertThatCode(() -> SpecialLog.info("测试日志 {} {}", 1, "x")).doesNotThrowAnyException();
    }

    /** debug.debugMode = true 时门控开启,info 走真实 logger. */
    @Test
    @Order(2)
    void testEnabledWhenDebugModeTrue() {
        loadConfig("[debug]\ndebugMode = true");

        assertThat(SpecialLog.isEnabled()).isTrue();
        assertThatCode(() -> SpecialLog.info("测试日志 {}", 42)).doesNotThrowAnyException();
    }
}
