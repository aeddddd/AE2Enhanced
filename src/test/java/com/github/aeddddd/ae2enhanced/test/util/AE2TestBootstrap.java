package com.github.aeddddd.ae2enhanced.test.util;

import java.lang.reflect.Field;
import java.util.EnumSet;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;

import appeng.api.AEApi;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;

/**
 * 无头测试引导:初始化 Minecraft 注册表 + AE2 配置与 API 实例.
 * <p>对应 1.20.1 的 BootstrapMinecraftExtension。1.12.2 关键点:</p>
 * <ul>
 * <li>{@code Bootstrap.register()} 使 Items.* 等注册表常量可用;</li>
 * <li>{@code AEConfig.instance} 以 Mockito mock 注入(功能开关一律 false),
 * 避免无头环境下 Forge Configuration 构造 NPE,CraftingJob.run 的 AELog 调用可用;</li>
 * <li>{@code AEApi.instance()} 触发 appeng.core.Api.INSTANCE 静态初始化,
 * ApiStorage 构造时自动注册物品/流体通道(createList 可用).</li>
 * </ul>
 */
public final class AE2TestBootstrap {

    private static boolean booted;

    private AE2TestBootstrap() {
    }

    public static synchronized void boot() {
        try {
            com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.requireProcess();
        } catch (IllegalStateException absent) {
            com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.startProcess(
                    new com.github.aeddddd.ae2enhanced.runtime.ModProcessScope(
                            "ae2enhanced", "test", false,
                            com.github.aeddddd.ae2enhanced.config.BootConfig.capture()));
        }
        if (booted) {
            return;
        }
        booted = true;
        try {
            net.minecraft.init.Bootstrap.register();
            injectMockConfig();
            injectSidedDelegate();
            injectMinecraftInstance();
            // 触发 Api.INSTANCE 初始化(ApiStorage 构造时注册存储通道)
            AEApi.instance();
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    /**
     * 注入 mock 的 FML sidedDelegate:Platform 等 AE2 工具类静态初始化时
     * 调用 FMLCommonHandler.getSide(),无 FML 环境时 sidedDelegate 为 null.
     */
    private static void injectSidedDelegate() throws ReflectiveOperationException {
        IFMLSidedHandler delegate = org.mockito.Mockito.mock(IFMLSidedHandler.class);
        org.mockito.Mockito.when(delegate.getSide()).thenReturn(Side.SERVER);
        Field field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), delegate);
    }

    /**
     * 注入 mock 的 Minecraft 静态实例:ApiDefinitions 构建块定义时实例化
     * BlockRendering,其构造函数向资源管理器注册重载监听.无头环境无 Minecraft
     * 实例,以 mock 替代(仅 getResourceManager 被调用).
     */
    private static void injectMinecraftInstance() throws ReflectiveOperationException {
        net.minecraft.client.Minecraft minecraft = org.mockito.Mockito.mock(net.minecraft.client.Minecraft.class);
        net.minecraft.client.resources.IReloadableResourceManager resourceManager = org.mockito.Mockito
                .mock(net.minecraft.client.resources.IReloadableResourceManager.class);
        org.mockito.Mockito.when(minecraft.getResourceManager()).thenReturn(resourceManager);
        Field field = net.minecraft.client.Minecraft.class.getDeclaredField("instance");
        field.setAccessible(true);
        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
        field.set(null, minecraft);
    }

    /**
     * 注入"空配置"AEConfig 单例:AEConfig 为 final 无法 mock,且其构造函数依赖
     * 无头环境不可用的 Forge Configuration,故用 Unsafe 绕过构造、反射填充
     * featureFlags 为空集(isFeatureEnabled 一律 false,AELog 静默).
     * Api 初始化链路(GrinderRecipeManager 等)同样依赖 AEConfig.instance().
     */
    private static void injectMockConfig() throws ReflectiveOperationException {
        AEConfig config = newConfigInstance();
        Field featureFlags = AEConfig.class.getDeclaredField("featureFlags");
        featureFlags.setAccessible(true);
        featureFlags.set(config, EnumSet.noneOf(AEFeature.class));
        // 其余对象字段兜底默认值:Unsafe 分配跳过构造函数,getXxx() 不能返回 null
        // (如 GrinderRecipeManager.oreRegistered → getGrinderBlackList().contains)
        for (Field field : AEConfig.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()
                    || field.getType().isEnum() || field.getType() == AEConfig.class) {
                continue;
            }
            field.setAccessible(true);
            if (field.get(config) != null) {
                continue;
            }
            if (java.util.List.class.isAssignableFrom(field.getType())) {
                field.set(config, new java.util.ArrayList<>());
            } else if (java.util.Set.class.isAssignableFrom(field.getType())) {
                field.set(config, new java.util.HashSet<>());
            } else if (java.util.Map.class.isAssignableFrom(field.getType())) {
                field.set(config, new java.util.HashMap<>());
            } else if (field.getType() == String.class) {
                field.set(config, "");
            } else if (field.getType().isArray()) {
                field.set(config, java.lang.reflect.Array.newInstance(field.getType().getComponentType(), 0));
            }
        }
        Field instanceField = AEConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, config);
    }

    @SuppressWarnings("restriction")
    private static AEConfig newConfigInstance() throws ReflectiveOperationException {
        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        return (AEConfig) unsafe.allocateInstance(AEConfig.class);
    }
}
