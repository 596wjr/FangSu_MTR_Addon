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
            // 记录但不要崩溃。按仓库约定日志只走 Main.LOGGER / Main.debug（不直接写标准输出/标准错误流）。
            // 【日志级别已由 ERROR 降为 DEBUG（仓库主拍板：保留代码，只降级日志）】
            // 在 JDK 9+ 上这条反射路径【注定失败】（三重原因见 addExportsForGraalVM 的 Javadoc），
            // 原先的 Main.LOGGER.error(..., e) 会把一次预期内的失败连整条堆栈刷进玩家日志，
            // 看起来像方速坏了，实际只是「保守尝试没成功」。降级后：
            //   · 只在 DEBUG 开关（Main.debug 内部的 debug 标志）打开时才输出；
            //   · ensureModuleAccess() 由 initialized 门闩保证整局只走一次，不刷屏；
            //   · 仍然带上异常对象本身（类名 + message 恰好是最有用的诊断信息，例如
            //     java.lang.NoSuchMethodException: java.lang.Module.getUnnamedModule()），
            //     只是不再展开堆栈。
            com.fangsu.Main.debug("[FangSu] 模块访问设置失败（GraalJS 沙箱导出未生效，属预期，已忽略）: " + e);
        } finally {
            initialized = true;
        }
    }

    /**
     * 保守尝试：直接让 {@code java.base} 把 GraalJS 需要的内部包导出给「未命名模块」。
     * <p>
     * <b>这段反射在当前 JDK（9 及以上）上注定失败，而且是三重失败</b>，所以它只是
     * 「环境允许时或许能生效、不允许时静默放弃」的保守代码，<b>不是</b>一条可用的沙箱方案；
     * 真正能让这些导出生效的只有启动参数：
     * {@code --add-exports java.base/jdk.internal.module=ALL-UNNAMED} 等。
     * 本类<b>刻意不</b>去改构建脚本或追加启动参数，也不要据此认为方速已经打开了
     * GraalJS 沙箱所需的全部导出。
     * <ol>
     *   <li>{@code java.lang.Module} 上<b>没有</b> {@code getUnnamedModule()} —— 这个方法在
     *       {@code java.lang.ClassLoader} 上（Java 9 起）。所以
     *       {@code moduleClass.getMethod("getUnnamedModule")} 直接抛
     *       {@link java.lang.NoSuchMethodException}。
     *       即便改成 {@code ClassLoader.getSystemClassLoader().getUnnamedModule()}，
     *       也只是走到下面第二重。</li>
     *   <li>{@code Module.addExports(String, Module)} 要求<b>调用方就是该模块本身</b>：
     *       JDK 的实现是「{@code this} 不是调用方的模块（或 {@code this} 是未命名模块）就抛
     *       {@link java.lang.IllegalCallerException}」。方速站在未命名模块里对
     *       {@code java.base} 调用它，调用方 ≠ {@code java.base}，必然抛
     *       {@code IllegalCallerException} —— 这也正是任务书里写明的那条结论。</li>
     *   <li>比上面两条更早一步：{@code addExportsMethod.setAccessible(true)} 本身就会失败 ——
     *       {@code java.base} 并没有 {@code opens java.lang} 给未命名模块，会抛
     *       {@link java.lang.reflect.InaccessibleObjectException}。</li>
     * </ol>
     * 三重里任意一重都足以让整个方法抛异常，调用方 {@link #ensureModuleAccess()} 捕获后
     * 只打一条 DEBUG 级日志，不再视为故障。
     */
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