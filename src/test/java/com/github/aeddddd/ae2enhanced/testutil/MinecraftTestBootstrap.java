package com.github.aeddddd.ae2enhanced.testutil;

import java.lang.reflect.Method;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

/**
 * 单元测试环境下的原版（Minecraft）引导工具.
 * <p>原版内置注册表（{@code BuiltInRegistries}）在静态初始化时要求游戏已完成引导,
 * 否则抛出 {@code IllegalArgumentException: Not bootstrapped}.
 * 任何引用 {@code Blocks} / {@code Items} / {@code Fluids} / {@code ItemStack} 等
 * 原版注册内容的单元测试,都必须先调用 {@link #bootstrap()}.
 * 整个过程幂等,可安全重复调用.</p>
 */
public final class MinecraftTestBootstrap {

    private static volatile boolean bootstrapped;

    private MinecraftTestBootstrap() {
    }

    /**
     * 确保原版注册表已引导.幂等,可重复调用.
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
            // 原版引导结束后内置注册表处于冻结状态,此时 new Item(...)/new Block(...) 会在
            // Forge 的注册表包装层抛出 IllegalStateException: Registry is already frozen.
            // 包装类包私有,通过反射调用其公开的 unfreeze() 解冻,使测试可自由构造物品/方块.
            unfreeze(BuiltInRegistries.ITEM);
            unfreeze(BuiltInRegistries.BLOCK);
            bootstrapped = true;
        }
    }

    /**
     * 反射调用 Forge 注册表包装层（包私有类）的 {@code unfreeze()} 方法.
     */
    private static void unfreeze(Object registry) {
        try {
            Method unfreeze = registry.getClass().getMethod("unfreeze");
            unfreeze.setAccessible(true);
            unfreeze.invoke(registry);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法在测试环境中解冻注册表: " + registry, e);
        }
    }
}
