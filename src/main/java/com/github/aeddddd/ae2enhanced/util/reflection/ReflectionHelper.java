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

    /**
     * 依序尝试多个字段名, 返回首个命中者.
     * <p>用于原版成员: 开发环境为 MCP 名, 生产环境为 SRG 名, 两种名字都试才能同时可用.</p>
     */
    public static Field findAnyFieldInHierarchy(Class<?> clazz, String... names) {
        for (String name : names) {
            Field field = findFieldInHierarchy(clazz, name);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    /** 依序尝试多个方法名(原版方法: 开发环境 MCP 名 / 生产环境 SRG 名), 返回首个命中者. */
    public static Method findAnyMethodInHierarchy(Class<?> clazz, Class<?>[] paramTypes, String... names) {
        for (String name : names) {
            Method method = findMethodInHierarchy(clazz, name, paramTypes);
            if (method != null) {
                return method;
            }
        }
        return null;
    }
}
