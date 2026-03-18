package com.fangsu.userScripts;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ScriptHolderBase {

    protected final Map<String, Value> functions = new ConcurrentHashMap<>();
    protected final Map<String, Long> failTime = new ConcurrentHashMap<>();

    protected String scriptName;
    private boolean isValid = true;
    protected Value scriptScope;

    private final Object executionLock = new Object();

    /**
     * 加载脚本内容
     */
    protected synchronized void loadScript(Context context, ResourceLocation location, Value scope) {
        this.scriptName = location.toString();
        this.isValid = true;

        try {
            String script = ResourceUtil.loadString(location);

            // 清理旧的函数映射
            functions.clear();
            failTime.clear();

            // 创建独立的作用域对象
            if (scope != null) {
                this.scriptScope = scope;
            } else {
                this.scriptScope = context.eval("js", "({})");
            }

            // 在独立作用域中执行脚本
            // 将脚本内容包装后执行，确保函数定义在作用域对象上
            String wrappedScript = String.format(
                    "with (this) {\n" +
                            "    %s\n" +
                            "}\n" +
                            "this;",
                    script
            );

            // 创建一个新的 Source 对象来执行脚本
            Source source = Source.newBuilder("js", wrappedScript, location.toString()).build();

            // 在独立作用域中执行脚本
            // 关键：使用 scriptScope 作为绑定对象
            Value result = context.eval(source);

            // 更新作用域（如果脚本返回了新的对象）
            if (result != null && !result.isNull() && result.hasMembers()) {
                this.scriptScope = result;
            }

            // 子类注册函数（从独立作用域中获取）
            init(context);

        } catch (Exception e) {
            isValid = false;
            Main.LOGGER.error("Error loading script {} : {}", location, e.getMessage());
            if (Main.LOGGER.isDebugEnabled()) {
                for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                    Main.LOGGER.error(stackTraceElement.toString());
                }
            }
        }
    }

    /**
     * 子类实现：注册需要的 JS 函数
     */
    protected abstract void init(Context context);

    /**
     * 注册 JS 函数到 Map
     */
    protected void loadFunction(Context context, String name) {
        if (scriptScope != null && scriptScope.hasMember(name)) {
            Value fn = scriptScope.getMember(name);
            if (fn != null && fn.canExecute()) {
                functions.put(name, fn);
            }
        }
    }

    /**
     * 执行 JS 函数 - 修改为使用独立作用域作为闭包环境
     */
    protected void runFunction(String name, Object... params) {
        if (!isValid || duringFailTimeout(name)) return;

        Value fn = functions.get(name);
        if (fn != null) {
            synchronized (executionLock) {
                try {
                    fn.execute(params);
                } catch (Throwable e) {
                    recordFailure(name, e);
                }
            }
        } else Main.LOGGER.warn("Script {} is not valid", scriptName);
    }

    /**
     * 执行 JS 函数并返回值
     */
    protected Value runFunctionWithResult(String name, Object... params) {
        if (!isValid || duringFailTimeout(name)) return null;

        Value fn = functions.get(name);
        if (fn != null) {
            synchronized (executionLock) {
                try {
                    return fn.execute(params);
                } catch (Throwable e) {
                    recordFailure(name, e);
                }
            }
        }
        return null;
    }

    /**
     * 检查函数是否存在
     */
    protected boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    /**
     * 记录函数执行失败
     */
    private void recordFailure(String name, Throwable e) {
        failTime.put(name, System.currentTimeMillis());
        Main.LOGGER.error("==== Error executing script function {} in {} ====",
                name, scriptName);
        Main.LOGGER.error(e.getMessage());
        Main.LOGGER.error("Stack trace:");
        for (StackTraceElement ste : e.getStackTrace()) {
            Main.LOGGER.error(ste.toString());
        }

    }

    /**
     * 检查是否在失败超时中
     */
    private boolean duringFailTimeout(String name) {
        Long t = failTime.get(name);
        return t != null && (System.currentTimeMillis() - t) < ScriptManager.getFailTimeoutMs();
    }

    /**
     * 清理资源
     */
    protected synchronized void close() {
        isValid = false;
        functions.clear();
        failTime.clear();
    }

    /**
     * 检查脚本是否有效
     */
    protected boolean isValid() {
        return isValid;
    }

    @FunctionalInterface
    public interface JsFunc {
        Object call(Value[] args) throws Exception;
    }
}