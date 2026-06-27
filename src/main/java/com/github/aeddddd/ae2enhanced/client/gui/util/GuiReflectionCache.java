package com.github.aeddddd.ae2enhanced.client.gui.util;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI 反射字段缓存工具.
 * <p>
 * 避免在 drawBG / initGui / getJEIExclusionArea 等高频/重复调用路径中
 * 每次重新执行 {@link Class#getDeclaredField(String)}.
 * <p>
 * 用法：在 GUI 类中声明 static final 字段,通过本类一次性获取并缓存.
 */
public final class GuiReflectionCache {

    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private GuiReflectionCache() {}

    /**
     * 获取并缓存指定类的字段.首次调用后结果常驻内存.
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "." + fieldName;
        Field f = FIELD_CACHE.get(key);
        if (f == null) {
            try {
                f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                Field existing = FIELD_CACHE.putIfAbsent(key, f);
                if (existing != null) {
                    f = existing;
                }
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("Field not found: " + key, e);
            }
        }
        return f;
    }

    public static boolean getBoolean(Object target, Class<?> clazz, String fieldName) {
        try {
            return getField(clazz, fieldName).getBoolean(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getInt(Object target, Class<?> clazz, String fieldName) {
        try {
            return getField(clazz, fieldName).getInt(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setInt(Object target, Class<?> clazz, String fieldName, int value) {
        try {
            getField(clazz, fieldName).setInt(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setObject(Object target, Class<?> clazz, String fieldName, Object value) {
        try {
            getField(clazz, fieldName).set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getObject(Object target, Class<?> clazz, String fieldName) {
        try {
            return (T) getField(clazz, fieldName).get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
