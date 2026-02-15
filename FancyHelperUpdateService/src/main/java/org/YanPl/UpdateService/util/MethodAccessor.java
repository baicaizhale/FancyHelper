package org.YanPl.UpdateService.util;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射方法访问工具类
 * 基于 PlugManX 的 MethodAccessor 简化实现
 */
public class MethodAccessor {

    private static final Map<String, Method> methodCache = new ConcurrentHashMap<>();

    /**
     * 获取类的指定方法（带缓存）
     */
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        var key = buildMethodKey(clazz, methodName, parameterTypes);
        return methodCache.computeIfAbsent(key, k -> {
            try {
                var method = clazz.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException | NoSuchMethodError e) {
                return null;
            }
        });
    }

    /**
     * 调用对象的方法
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Class<?> clazz, String methodName, Object instance, Class<?>[] parameterTypes, Object... args) throws Exception {
        var method = getMethod(clazz, methodName, parameterTypes);
        if (method == null) {
            throw new IllegalArgumentException("Method '" + methodName + "' not found in class " + clazz.getName());
        }
        return (T) method.invoke(instance, args);
    }

    /**
     * 调用对象的方法（使用对象自己的类）
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(String methodName, Object instance, Class<?>[] parameterTypes, Object... args) throws Exception {
        return invoke(instance.getClass(), methodName, instance, parameterTypes, args);
    }

    /**
     * 调用对象的无参方法
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Class<?> clazz, String methodName, Object instance) throws Exception {
        return invoke(clazz, methodName, instance, new Class<?>[0]);
    }

    /**
     * 调用对象的无参方法（使用对象自己的类）
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(String methodName, Object instance) throws Exception {
        return invoke(instance.getClass(), methodName, instance, new Class<?>[0]);
    }

    /**
     * 构建方法缓存键
     */
    private static String buildMethodKey(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        var keyBuilder = new StringBuilder();
        keyBuilder.append(clazz.getName()).append(".").append(methodName);

        if (parameterTypes.length == 0) {
            keyBuilder.append("()");
        } else {
            keyBuilder.append("(");
            for (var i = 0; i < parameterTypes.length; i++) {
                if (i > 0) keyBuilder.append(",");
                keyBuilder.append(parameterTypes[i].getName());
            }
            keyBuilder.append(")");
        }

        return keyBuilder.toString();
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        methodCache.clear();
    }
}