package com.github.aeddddd.ae2enhanced.test.util;

import java.nio.file.Files;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import appeng.core.AEConfig;

/**
 * Minecraft 注册表引导扩展,全局只执行一次.
 * <p>移植自 AE2 15.3.4 {@code appeng.util.BootstrapMinecraftExtension} 并补全真实引导逻辑.</p>
 */
public class BootstrapMinecraftExtension implements Extension, BeforeAllCallback {

    private static boolean bootstrapped;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!bootstrapped) {
            bootstrapped = true;
            // 开发环境缺 version.json 时仅告警,不影响注册表初始化
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            // AE2 15.4.10 的合成模拟在 NetworkCraftingSimulationState 构造时读取 AEConfig.instance(),
            // 单元测试无 Forge 环境,需调用公开的静态 load(会构造并注册静态实例,使用临时目录避免写盘污染)
            AEConfig.load(Files.createTempDirectory("ae2enhanced-test-config"));
        }
    }
}
