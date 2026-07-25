package com.github.aeddddd.ae2enhanced.test.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 标记需要在测试前完成 Minecraft 注册表引导(bootstrap)的测试类.
 * <p>移植自 AE2 15.3.4 {@code appeng.util.BootstrapMinecraft};AE2 原扩展为空实现,
 * 本项目改为在扩展中真正执行 {@code SharedConstants.tryDetectVersion() + Bootstrap.bootStrap()},
 * 使单元测试可安全引用 {@code Items}/{@code Fluids} 等注册表常量.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith({ BootstrapMinecraftExtension.class })
public @interface BootstrapMinecraft {
}
