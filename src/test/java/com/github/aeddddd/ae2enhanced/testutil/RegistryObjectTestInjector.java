package com.github.aeddddd.ae2enhanced.testutil;

import java.lang.reflect.Field;

import net.minecraftforge.registries.RegistryObject;

/**
 * 单元测试环境下的 {@link RegistryObject} 值注入工具.
 * <p>模组的 {@code DeferredRegister} 只在游戏启动的注册事件中填充 {@link RegistryObject#get()},
 * 纯 JUnit 环境下调用 {@code get()} 会抛出 {@code NullPointerException}.
 * 本工具通过反射直接写入 {@code RegistryObject#value} 字段,使被测代码中的
 * {@code ModItems.X.get()} / {@code ModRecipes.Y.get()} 等调用可返回测试构造的实例.
 * 整个过程幂等,可安全重复调用.</p>
 */
public final class RegistryObjectTestInjector {

    private static final Field VALUE_FIELD;

    static {
        try {
            VALUE_FIELD = RegistryObject.class.getDeclaredField("value");
            VALUE_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法访问 RegistryObject#value 字段", e);
        }
    }

    private RegistryObjectTestInjector() {
    }

    /**
     * 将指定值注入 RegistryObject,使其 {@code get()} 返回该值.可重复调用.
     */
    public static <T> void inject(RegistryObject<T> registryObject, T value) {
        try {
            VALUE_FIELD.set(registryObject, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("无法注入 RegistryObject 值", e);
        }
    }
}
