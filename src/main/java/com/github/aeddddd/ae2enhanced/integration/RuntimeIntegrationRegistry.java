package com.github.aeddddd.ae2enhanced.integration;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.BootConfig;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** preInit 之后使用的 mod 能力快照、适配器调用与共享反射查找缓存。 */
public final class RuntimeIntegrationRegistry {

    private static final Map<String, Boolean> MOD_CAPABILITIES = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> CLASSES = new ConcurrentHashMap<>();
    private static final Map<MethodKey, Method> METHODS = new ConcurrentHashMap<>();

    private RuntimeIntegrationRegistry() {
    }

    public static void preInit() {
        MOD_CAPABILITIES.clear();
        StorageIntegration.register();
        com.github.aeddddd.ae2enhanced.api.MicroSingularityApi.registerProvider(
                new com.github.aeddddd.ae2enhanced.integration.MicroSingularityApiImpl());
        com.github.aeddddd.ae2enhanced.api.ChamberApi.registerProvider(
                new com.github.aeddddd.ae2enhanced.integration.ChamberApiImpl());
        if (isModLoaded("ftblib") && BootConfig.current().integrationEnabled("ftblib")
                && BootstrapCapabilities.hasResource(BootstrapCapabilities.FTB_UNIVERSE)) {
            try {
                invokeStatic("com.github.aeddddd.ae2enhanced.integration.ftb.FTBTeamIntegration", "init");
            } catch (Throwable exception) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize FTB team integration", exception);
            }
        }
    }

    public static void init() {
        if (isModLoaded("mekanism") && isModLoaded("mekeng")
                && BootConfig.current().integrationEnabled("mekanism")
                && BootConfig.current().channelEnabled("gas")) {
            try {
                classForName("com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper");
            } catch (Throwable e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize gas runtime integration", e);
            }
        }
    }

    public static void serverStopping() {
        if (isModLoaded("ftblib") && BootConfig.current().integrationEnabled("ftblib")
                && com.github.aeddddd.ae2enhanced.dimension.dev.PrivateDimensionHarness.enabled()) {
            try {
                invokeStatic("com.github.aeddddd.ae2enhanced.integration.ftb.FTBTeamIntegration", "onServerStopping");
            } catch (Throwable exception) {
                AE2Enhanced.LOGGER.warn("[AE2E] FTB private harness shutdown failed", exception);
            }
        }
    }

    public static void postInit() {
        if (isModLoaded("cellterminal") && BootConfig.current().integrationEnabled("cellterminal")) {
            try {
                invokeStatic("com.github.aeddddd.ae2enhanced.integration.cellterminal.CellTerminalIntegration", "init");
            } catch (Throwable e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to register Cell Terminal integration", e);
            }
        }
    }

    public static boolean isModLoaded(String modId) {
        return MOD_CAPABILITIES.computeIfAbsent(modId, Loader::isModLoaded);
    }

    public static Class<?> classForName(String className) throws ClassNotFoundException {
        Class<?> cached = CLASSES.get(className);
        if (cached != null) {
            return cached;
        }
        Class<?> loaded = Class.forName(className);
        Class<?> raced = CLASSES.putIfAbsent(className, loaded);
        return raced != null ? raced : loaded;
    }

    public static Method method(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        MethodKey key = new MethodKey(owner, name, parameterTypes);
        Method cached = METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        Method found = owner.getMethod(name, parameterTypes);
        Method raced = METHODS.putIfAbsent(key, found);
        return raced != null ? raced : found;
    }

    public static Object invokeStatic(String className, String methodName, Class<?>[] parameterTypes,
                                      Object... arguments) throws ReflectiveOperationException {
        Class<?> owner = classForName(className);
        return method(owner, methodName, parameterTypes).invoke(null, arguments);
    }

    public static Object invokeStatic(String className, String methodName) throws ReflectiveOperationException {
        return invokeStatic(className, methodName, new Class<?>[0]);
    }

    private static final class MethodKey {
        private final Class<?> owner;
        private final String name;
        private final Class<?>[] parameterTypes;
        private final int hashCode;

        private MethodKey(Class<?> owner, String name, Class<?>[] parameterTypes) {
            this.owner = owner;
            this.name = name;
            this.parameterTypes = parameterTypes.clone();
            this.hashCode = 31 * (31 * owner.hashCode() + name.hashCode()) + Arrays.hashCode(this.parameterTypes);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MethodKey)) {
                return false;
            }
            MethodKey that = (MethodKey) other;
            return owner.equals(that.owner) && name.equals(that.name)
                    && Arrays.equals(parameterTypes, that.parameterTypes);
        }
    }
}
