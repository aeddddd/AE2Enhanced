package com.github.aeddddd.ae2enhanced.util.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 公共反射辅助工具.
 * 统一处理 hierarchy walk 和 setAccessible,避免各 handler 重复实现.
 */
public final class ReflectionHelper {

    private ReflectionHelper() {}

    public static Method findMethodInHierarchy(Class<?> clazz, String name, Class<?>... paramTypes) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Method m = current.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    public static Field findFieldInHierarchy(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
