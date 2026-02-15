package org.YanPl.UpdateService.util;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射字段访问工具类
 * 基于 PlugManX 的 FieldAccessor 简化实现
 */
public class FieldAccessor {

    private static final Map<String, Field> fieldCache = new ConcurrentHashMap<>();

    /**
     * 获取类的指定字段（带缓存）
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        if (clazz == null || fieldName == null) return null;

        var key = clazz.getName() + "." + fieldName;
        return fieldCache.computeIfAbsent(key, k -> {
            try {
                var field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException | NoSuchFieldError e) {
                return null;
            }
        });
    }

    /**
     * 获取对象字段的值
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(Class<?> clazz, String fieldName, Object instance) throws IllegalAccessException {
        var field = getField(clazz, fieldName);
        if (field == null) return null;
        return (T) field.get(instance);
    }

    /**
     * 设置对象字段的值
     */
    public static void setValue(Class<?> clazz, String fieldName, Object instance, Object value) throws IllegalAccessException {
        var field = getField(clazz, fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found in class " + clazz.getName());
        }
        field.set(instance, value);
    }

    /**
     * 获取对象字段的值（使用对象自己的类）
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(String fieldName, Object instance) throws IllegalAccessException {
        return getValue(instance.getClass(), fieldName, instance);
    }

    /**
     * 设置对象字段的值（使用对象自己的类）
     */
    public static void setValue(String fieldName, Object instance, Object value) throws IllegalAccessException {
        setValue(instance.getClass(), fieldName, instance, value);
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        fieldCache.clear();
    }
}