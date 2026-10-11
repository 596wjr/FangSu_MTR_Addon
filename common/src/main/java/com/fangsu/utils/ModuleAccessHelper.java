package com.fangsu.utils;

import java.lang.reflect.Method;
import java.util.Set;

public class ModuleAccessHelper {

    private static boolean initialized = false;

    /**
     * 在 ScriptManager 初始化前调用
     */
    public static void ensureModuleAccess() {
        if (initialized) return;

        try {
            // 检查是否在 Java 9+ 模块系统下
            Class<?> moduleClass = Class.forName("java.lang.Module");
            if (moduleClass != null) {
                addExportsForGraalVM();
            }
        } catch (ClassNotFoundException e) {
            // Java 8 或更早版本，不需要处理
        } catch (Exception e) {
            // 记录但不要崩溃。按仓库约定走 Main.LOGGER（不直接写 System.err），
            // 并把完整异常带上 —— 只打 e.getMessage() 会把「哪个 addExports 失败」这类关键
            // 信息丢在 stack trace 里，而这正是本次失败唯一可诊断的东西。
            com.fangsu.Main.LOGGER.error("[FangSu] 模块访问设置失败（GraalJS 沙箱导出未生效）", e);
        } finally {
            initialized = true;
        }
    }

    private static void addExportsForGraalVM() {
        try {
            // 获取 Module 类和方法
            Class<?> moduleClass = Class.forName("java.lang.Module");
            Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");

            // 获取 addExports 方法
            Method addExportsMethod = moduleClass.getDeclaredMethod(
                    "addExports", String.class, moduleClass);
            addExportsMethod.setAccessible(true);

            // 获取 boot 层
            Method bootMethod = moduleLayerClass.getMethod("boot");
            Object bootLayer = bootMethod.invoke(null);

            // 获取所有模块
            Method modulesMethod = moduleLayerClass.getMethod("modules");
            Set<?> modules = (Set<?>) modulesMethod.invoke(bootLayer);

            // 找到 java.base 模块
            Object javaBaseModule = null;
            for (Object module : modules) {
                if (module.toString().startsWith("module java.base")) {
                    javaBaseModule = module;
                    break;
                }
            }

            if (javaBaseModule == null) {
                return;
            }

            // 获取 ALL-UNNAMED 模块
            Method getUnnamedModuleMethod = moduleClass.getMethod("getUnnamedModule");
            Object allUnnamed = getUnnamedModuleMethod.invoke(javaBaseModule);

            // 需要导出的包
            String[] packagesToExport = {
                    "jdk.internal.module",
                    "jdk.internal.misc",
                    "jdk.internal.ref",
                    "jdk.internal.access",
                    "jdk.internal.org.objectweb.asm",
                    "com.oracle.truffle.api",
                    "com.oracle.truffle.api.nodes"
            };

            // 添加导出
            for (String pkg : packagesToExport) {
                try {
                    addExportsMethod.invoke(javaBaseModule, pkg, allUnnamed);
                } catch (Exception e) {
                    // 忽略单个包失败
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to setup module access", e);
        }
    }
}