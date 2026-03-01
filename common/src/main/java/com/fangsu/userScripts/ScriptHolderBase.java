package com.fangsu.userScripts;

import com.fangsu.Main;
import com.fangsu.scripting.JsFunctions;
import com.fangsu.scripting.TextUtil;
import com.fangsu.scripting.TimingUtil;
import com.fangsu.utils.ResourceUtil;
import org.graalvm.polyglot.*;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.*;

public abstract class ScriptHolderBase {

    protected Context context;
    protected Map<String, Value> functions = new HashMap<>();
    protected String scriptName;

    public Map<String, Long> failTime = new HashMap<>();

    protected ScriptHolderBase() {
        context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "2020")
                .allowHostClassLookup(c -> true)
                .allowHostAccess(HostAccess.ALL)
                .allowCreateThread(true)
                .build();

        Value bindings = context.getBindings("js");
        setBuiltInFunction(context, bindings);
        injectJsFunctions(context);
    }

    /**
     * 加载脚本内容
     */
    public void loadScript(ResourceLocation location) {
        scriptName = location.toString();
        try {
            String script = ResourceUtil.loadString(location);

            context.eval(Source.newBuilder("js", script, location.toString()).buildLiteral());
            init(); // 子类负责注册函数}
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
    protected abstract void init();

    /**
     * 注册 JS 函数到 Map
     */
    protected void loadFunction(String name) {
        Value fn = context.getBindings("js").getMember(name);
        if (fn != null && fn.canExecute()) {
            functions.put(name, fn);
        }
    }

    /**
     * 执行 JS 函数
     */
    public void runFunction(String name, Object... params) {
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

    public void close() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    private static void setBuiltInFunction(Context ctx, Value b) {
        b.putMember("Color", java.awt.Color.class);
        b.putMember("Font", java.awt.Font.class);
        b.putMember("BasicStroke", java.awt.BasicStroke.class);
        b.putMember("RenderingHints", java.awt.RenderingHints.class);
        b.putMember("Rectangle", java.awt.Rectangle.class);

//        inject(ctx, TimingUtil.class, "Timing");
        b.putMember("Timing",
                JsStaticBridge.fromStaticClass(TimingUtil.class));
        b.putMember("TextUtil",
                JsStaticBridge.fromStaticClass(TextUtil.class));
//        inject(ctx, TextUtil.class, "TextUtil");

        b.putMember("getMatching", fn(a ->
                TextUtil.getCjkMatching(a[0].asString(), a[1].asBoolean())
        ));
        b.putMember("hasCjkPart", fn(a ->
                TextUtil.hasCjkPart(a[0].asString())
        ));
        b.putMember("hasNonCjkPart", fn(a ->
                TextUtil.hasCjkPart(a[0].asString())
        ));
        b.putMember("loadResource", fn(a ->
                JsFunctions.loadResource(a[0].asString(), a[1].asString())
        ));
        b.putMember("addPrefix", fn(a ->
                JsFunctions.addPrefix(a[0].asString(), a[1].asString(), a[2].asBoolean())
        ));
        b.putMember("addSuffix", fn(a ->
                JsFunctions.addSuffix(a[0].asString(), a[1].asString())
        ));
        b.putMember("setDebugInfo", fn(a -> {
            JsFunctions.setDebugInfo(a[0].asString());
            return null;
        }));
        b.putMember("setWarnInfo", fn(a -> {
            JsFunctions.setWarnInfo(a[0].asString());
            return null;
        }));
        b.putMember("setErrorInfo", fn(a -> {
            JsFunctions.setErrorInfo(a[0].asString());
            return null;
        }));
        b.putMember("getCurrentDate", fn(a -> JsFunctions.getCurrentDate()));
        b.putMember("getCurrentWeekday", fn(a -> JsFunctions.getCurrentWeekday()));
        b.putMember("formatDate", fn(a -> JsFunctions.formatDate(a[0].asBoolean())));
        b.putMember("formatWeekday", fn(a -> JsFunctions.formatWeekday(a[0].asBoolean())));
        b.putMember("rgbToColor", fn(a ->
                JsFunctions.rgbToColor(a[0].asInt(), a[1].asInt(), a[2].asInt())
        ));
        b.putMember("rgbaToColor", fn(a ->
                JsFunctions.rgbaToColor(a[0].asInt(), a[1].asInt(), a[2].asInt(), a[3].asInt())
        ));
        b.putMember("intToColor", fn(a ->
                JsFunctions.intToColor(a[0].asInt())
        ));
        b.putMember("isLightColor", fn(a ->
                JsFunctions.isLightColor((java.awt.Color) a[0].asHostObject())
        ));

    }

    private static ProxyExecutable fn(JsFunc f) {
        return args -> {
            try {
                return f.call(args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    @FunctionalInterface
    public interface JsFunc {
        Object call(Value[] args) throws Exception;
    }

    private boolean duringFailTimeout(String name) {
        Long t = failTime.get(name);
        return t != null && (System.currentTimeMillis() - t) < 4000;
    }

    protected static void inject(Context context, Class clazz, String alias) {
        if (alias == null) alias = clazz.getSimpleName();
        context.eval("js", "var " + alias + " = () => Java.type('" + clazz.getName() + "');");
    }

    protected static void inject(Context context, String key, String value) {
        context.eval("js", "var " + key + " = '" + value + "';");
    }

    private static void injectJsFunctions(Context context) {
        String jsFunctions = """
                """;
        context.eval("js", jsFunctions);
    }

}