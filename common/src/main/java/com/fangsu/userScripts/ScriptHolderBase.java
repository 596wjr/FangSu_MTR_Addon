package com.fangsu.userScripts;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;

public abstract class ScriptHolderBase {

    protected final Map<String, Value> functions = new HashMap<>();
    protected String scriptName;

    public final Map<String, Long> failTime = new HashMap<>();

    /**
     * 加载脚本内容
     */
    public synchronized void loadScript(Context context, ResourceLocation location) {
        scriptName = location.toString();
        try {
            String script = ResourceUtil.loadString(location);
            functions.clear();
            context.eval(Source.newBuilder("js", script, location.toString()).buildLiteral());
            init(context); // 子类负责注册函数
        } catch (Exception e) {
            Main.LOGGER.error("Error loading script {} : {}", location, e.getMessage());
            Main.LOGGER.error("Stacktrace:");
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                Main.LOGGER.error(stackTraceElement.toString());
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
        Value fn = context.getBindings("js").getMember(name);
        if (fn != null && fn.canExecute()) {
            functions.put(name, fn);
        }
    }

    /**
     * 执行 JS 函数
     */
    public synchronized void runFunction(String name, Object... params) {
        if (duringFailTimeout(name)) return;
        Value fn = functions.get(name);
        if (fn != null) {
            try {
                fn.execute(params);
            } catch (Throwable e) {
                failTime.put(name, System.currentTimeMillis());
                Main.LOGGER.error("Error executing FangSu JavaScript function {} : {}", name, e.getMessage());
                Main.LOGGER.error("Stacktrace:");
                for (StackTraceElement ste : e.getStackTrace()) {
                    Main.LOGGER.error(ste.toString());
                }
            }
        }
    }

    public synchronized void close() {
        functions.clear();
        failTime.clear();
    }

    @FunctionalInterface
    public interface JsFunc {
        Object call(Value[] args) throws Exception;
    }

    private boolean duringFailTimeout(String name) {
        Long t = failTime.get(name);
        return t != null && (System.currentTimeMillis() - t) < 4000;
    }
}
