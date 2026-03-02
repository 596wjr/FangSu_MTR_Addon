package com.fangsu.userScripts;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public final class JsStaticBridge {

    public static ProxyObject fromStaticClass(Class<?> clazz) {
        Map<String, Object> map = new HashMap<>();

        // 添加静态字段
        for (Field field : clazz.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                try {
                    field.setAccessible(true);
                    map.put(field.getName(), field.get(null));
                } catch (IllegalAccessException e) {
                    // 忽略
                }
            }
        }

        // 添加静态方法
        for (Method method : clazz.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;

            if (map.containsKey(method.getName())) {
                // 字段与方法同名，合并为一个代理对象
                Object fieldValue = map.get(method.getName());
                map.put(method.getName(), createFieldAndMethodProxy(fieldValue, method));
            } else {
                method.setAccessible(true);
                map.put(method.getName(), (ProxyExecutable) args -> {
                    try {
                        Object[] converted = convertArgs(args, method.getParameterTypes());
                        return method.invoke(null, converted);
                    } catch (Throwable e) {
                        throw new RuntimeException("Error invoking static method " + method.getName(), e);
                    }
                });
            }
        }

        return ProxyObject.fromMap(map);
    }

    private static ProxyObject createFieldAndMethodProxy(Object fieldValue, Method method) {
        return new ProxyObject() {
            @Override
            public Object getMember(String key) {
                if ("value".equals(key)) return fieldValue;
                if ("call".equals(key)) return (ProxyExecutable) args -> {
                    try {
                        Object[] converted = convertArgs(args, method.getParameterTypes());
                        return method.invoke(null, converted);
                    } catch (Throwable e) {
                        throw new RuntimeException("Error invoking static method " + method.getName(), e);
                    }
                };
                return null;
            }

            @Override
            public Object getMemberKeys() {
                return new String[]{"value", "call"};
            }

            @Override
            public boolean hasMember(String key) {
                return "value".equals(key) || "call".equals(key);
            }

            @Override
            public void putMember(String key, Value value) {
                throw new UnsupportedOperationException("Cannot modify static field/method");
            }
        };
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
        if (value == null || value.isNull()) return defaultValue(targetType);

        if (targetType == String.class) {
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

        if (value.isHostObject()) {
            Object host = value.asHostObject();
            if (targetType.isInstance(host)) return host;
        }

        try {
            return value.as(targetType);
        } catch (Exception e) {
            return null;
        }
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