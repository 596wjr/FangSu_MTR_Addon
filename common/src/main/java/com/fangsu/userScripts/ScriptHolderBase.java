package com.fangsu.userScripts;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class ScriptHolderBase {
    private static final long FUNCTION_TIMEOUT_MS = 5000;

    protected final Map<String, Value> functions = new ConcurrentHashMap<>();
    protected final Map<String, Long> failTime = new ConcurrentHashMap<>();

    protected String scriptName;
    private boolean isValid = true;
    protected Value scriptScope;

    private final Object executionLock = new Object();

    protected final Context context;

    public ScriptHolderBase() {
        Engine engine = ScriptManager.getInstance().engine;
        context = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(ScriptManager.getInstance().hostAccess)
                .allowHostClassLookup(c -> true)
                .allowCreateThread(true)
                .build();
        ScriptManager.initializeGlobalBindings(context);
    }

    /**
     * 加载脚本内容
     */
    protected synchronized void loadScript(ResourceLocation location, Value scope) {


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
            init();

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
    protected abstract void init();

    /**
     * 注册 JS 函数到 Map
     */
    protected void loadFunction(String name) {
        if (scriptScope != null && scriptScope.hasMember(name)) {
            Value fn = scriptScope.getMember(name);
            if (fn != null && fn.canExecute()) {
                functions.put(name, fn);
            }
        }
    }

    /**
     * 同步执行 JS 函数（公共接口，供外部同步调用）
     * 在调用线程上直接执行 JS，阻塞直到完成或失败。
     * 与 runFunction 不同，如果 JS 执行失败，会将异常抛出，
     * 以便调用者（如 GraphicsTextureHelper）能正确感知绘制失败并重试。
     */
    public void runFunctionSync(String name, Runnable callback, Object... params) {
        if (!isValid) {
            throw new RuntimeException("Script " + scriptName + " is not valid");
        }
        // 失败超时中：抛出异常让调用者重试
        if (duringFailTimeout(name)) {
            throw new RuntimeException("Script function " + name + " in " + scriptName + " is in fail timeout, will retry later");
        }

        Value fn = functions.get(name);
        if (fn != null) {
            synchronized (executionLock) {
                try {
                    fn.execute(params);
                    if (callback != null) callback.run();
                } catch (Throwable e) {
                    recordFailure(name, e);
                    throw new RuntimeException("Script function " + name + " in " + scriptName + " failed", e);
                }
            }
        } else {
            Main.LOGGER.warn("Script function '{}' not found in {}", name, scriptName);
            throw new RuntimeException("Script function '" + name + "' not found in " + scriptName);
        }
    }

    /**
     * 执行 JS 函数 - 修改为使用独立作用域作为闭包环境
     */
    protected void runFunction(String name, Runnable callback, Object... params) {
        if (!isValid || duringFailTimeout(name)) return;

        Value fn = functions.get(name);
        if (fn != null) {
            synchronized (executionLock) {
                try {
                    fn.execute(params);
                    if (callback != null) callback.run();
                } catch (Throwable e) {
                    recordFailure(name, e);
                }
            }
        } else Main.LOGGER.warn("Script {} is not valid", scriptName);
    }

    /**
     * 执行 JS 函数并返回值
     */
    protected void runFunctionWithResult(String name, Consumer<Value> consumer, Object... params) {
        if (!isValid || duringFailTimeout(name)) return;

        Value fn = functions.get(name);
        if (fn != null) {
            synchronized (executionLock) {
                try {
                    Value v = fn.execute(params);
                    consumer.accept(v);
                } catch (Throwable e) {
                    recordFailure(name, e);
                }
            }
        }
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
        if (e instanceof org.graalvm.polyglot.PolyglotException &&
                ((org.graalvm.polyglot.PolyglotException) e).isCancelled()) {
            Main.LOGGER.error("Script function {} in {} timed out after {}ms",
                    name, scriptName, FUNCTION_TIMEOUT_MS);
            return; // 超时异常不打印完整堆栈，避免刷屏
        }
        if (e instanceof CancellationException) {
            Main.LOGGER.error("=== Script function {} in {} timed out in drawing thread ===", name, scriptName);
            Main.LOGGER.error("- If you are a resource pack developer: check for infinite loops or heavy computations in your script.");
            Main.LOGGER.error("- If you are a player: please report this to the resource pack author, or submit an issue to the mod's bug tracker if you are sure this is caused by the mod.");
            return;
        }
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
        context.close();
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