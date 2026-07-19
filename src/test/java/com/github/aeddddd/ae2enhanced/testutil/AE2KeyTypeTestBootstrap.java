package com.github.aeddddd.ae2enhanced.testutil;

import java.lang.reflect.Method;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryManager;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;

/**
 * 单元测试环境下的 AE2 key type 注册表引导工具。
 * <p>AE2 在游戏启动时才通过 {@code NewRegistryEvent} 创建 key type 注册表并调用
 * {@link AEKeyTypesInternal#setRegistry}；纯 JUnit 环境下该注册表不存在，
 * 任何依赖 {@link AEKeyTypes#getAll()} / {@link AEKeyTypes#get(ResourceLocation)} /
 * {@code AEKey.fromTagGeneric} 的代码都会抛出异常。
 * 本工具在测试 JVM 中手动创建等价的 {@link ForgeRegistry} 并注入，
 * 同时注册内置的物品/流体 key type。整个过程幂等，可安全重复调用。</p>
 */
public final class AE2KeyTypeTestBootstrap {

    /** key type 注册表名称，与 AE2 运行时使用的一致。 */
    private static final ResourceLocation REGISTRY_NAME = new ResourceLocation("ae2", "keytypes");

    private static volatile boolean bootstrapped;

    private AE2KeyTypeTestBootstrap() {
    }

    /**
     * 确保 AE2 key type 注册表可用。幂等，可重复调用。
     */
    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        synchronized (AE2KeyTypeTestBootstrap.class) {
            if (bootstrapped) {
                return;
            }
            doBootstrap();
            bootstrapped = true;
        }
    }

    private static void doBootstrap() {
        // AE2 注册表依赖原版注册表，先完成原版引导
        MinecraftTestBootstrap.bootstrap();
        try {
            // 已初始化（例如在游戏内运行的 GameTest）时直接返回
            AEKeyTypesInternal.getRegistry();
            return;
        } catch (RuntimeException notInitialized) {
            // AE2 尚未初始化，继续手动引导
        }

        ForgeRegistry<AEKeyType> registry = createKeyTypeRegistry();
        AEKeyTypesInternal.setRegistry(() -> registry);

        // 注册内置物品/流体类型，使 AEKey.fromTagGeneric 等依赖注册表的逻辑可用
        AEKeyTypes.register(AEKeyType.items());
        AEKeyTypes.register(AEKeyType.fluids());
    }

    /**
     * 通过反射调用包私有的 {@code RegistryManager#createRegistry} 创建 key type 注册表，
     * 参数与 AE2 在 {@code NewRegistryEvent} 中使用的保持一致（maxId=127）。
     */
    @SuppressWarnings("unchecked")
    private static ForgeRegistry<AEKeyType> createKeyTypeRegistry() {
        try {
            RegistryBuilder<AEKeyType> builder = new RegistryBuilder<AEKeyType>()
                    .setName(REGISTRY_NAME)
                    .setMaxID(127);
            Method create = RegistryManager.class.getDeclaredMethod(
                    "createRegistry", ResourceLocation.class, RegistryBuilder.class);
            create.setAccessible(true);
            return (ForgeRegistry<AEKeyType>) create.invoke(RegistryManager.ACTIVE, REGISTRY_NAME, builder);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法在测试环境中创建 AE2 key type 注册表", e);
        }
    }
}
