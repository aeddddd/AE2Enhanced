package com.github.aeddddd.ae2enhanced.testutil;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * 单元测试环境下的原版（Minecraft）引导工具。
 * <p>原版内置注册表（{@code BuiltInRegistries}）在静态初始化时要求游戏已完成引导，
 * 否则抛出 {@code IllegalArgumentException: Not bootstrapped}。
 * 任何引用 {@code Blocks} / {@code Items} / {@code Fluids} / {@code ItemStack} 等
 * 原版注册内容的单元测试，都必须先调用 {@link #bootstrap()}。
 * 整个过程幂等，可安全重复调用。</p>
 */
public final class MinecraftTestBootstrap {

    private static volatile boolean bootstrapped;

    private MinecraftTestBootstrap() {
    }

    /**
     * 确保原版注册表已引导。幂等，可重复调用。
     */
    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        synchronized (MinecraftTestBootstrap.class) {
            if (bootstrapped) {
                return;
            }
            // 注入版本信息并触发原版注册表初始化（内部同样幂等）
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            bootstrapped = true;
        }
    }
}
