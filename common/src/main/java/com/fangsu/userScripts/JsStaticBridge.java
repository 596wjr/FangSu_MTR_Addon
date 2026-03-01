package com.fangsu.userScripts;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public final class JsStaticBridge {

    public static ProxyObject fromStaticClass(Class<?> clazz) {
        Map<String, Object> map = new HashMap<>();

        for (Method method : clazz.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;

            method.setAccessible(true);

            map.put(method.getName(), (ProxyExecutable) args -> {
                try {
                    Object[] converted = convertArgs(args, method.getParameterTypes());
                    return method.invoke(null, converted);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        }

        return ProxyObject.fromMap(map);
    }

    private static Object[] convertArgs(Value[] args, Class<?>[] paramTypes) {
        Object[] result = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            if (i < args.length) {
                result[i] = convertValue(args[i], paramTypes[i]);
            } else {
                result[i] = defaultValue(paramTypes[i]);
            }
        }

        return result;
    }

    private static Object convertValue(Value value, Class<?> targetType) {

        if (targetType == String.class) {
            if (value == null || value.isNull()) return "";
            try {
                return value.asString();
            } catch (Exception e) {
                return "";
            }
        }

        if (targetType == int.class || targetType == Integer.class) return value.asInt();
        if (targetType == long.class || targetType == Long.class) return value.asLong();
        if (targetType == double.class || targetType == Double.class) return value.asDouble();
        if (targetType == float.class || targetType == Float.class) return (float) value.asDouble();
        if (targetType == boolean.class || targetType == Boolean.class) return value.asBoolean();

        if (value != null && value.isHostObject()) {
            Object host = value.asHostObject();
            if (targetType.isInstance(host)) {
                return host;
            }
        }

        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == String.class) return "";
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 0L;
        if (type == float.class || type == Float.class) return 0f;
        if (type == double.class || type == Double.class) return 0d;
        return null;
    }
}