package com.fangsu.userScripts;

import com.fangsu.scripting.JsFunctions;
import com.fangsu.scripting.TextUtil;
import com.fangsu.scripting.TimingUtil;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ScriptManager {

    private static final ScriptManager INSTANCE = new ScriptManager();

    private final Context context;
    private final Map<ResourceLocation, ScriptHolderBase> holders;

    private ScriptManager() {
        context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "2020")
                .allowHostClassLookup(c -> true)
                .allowHostAccess(org.graalvm.polyglot.HostAccess.ALL)
                .allowCreateThread(true)
                .build();

        Value bindings = context.getBindings("js");
        setBuiltInFunction(bindings);
        injectJsFunctions(context);

        holders = new HashMap<>();
    }

    public static ScriptManager getInstance() {
        return INSTANCE;
    }

    public synchronized ScriptHolderBase getHolder(ResourceLocation id) {
        return holders.getOrDefault(id, null);
    }

    public synchronized void initHolder(ResourceLocation pos, Supplier<? extends ScriptHolderBase> holderSupplier) {
        if (holders.containsKey(pos)) return;
        ScriptHolderBase holder = holderSupplier.get();
        holder.loadScript(context, pos);
        holders.put(pos, holder);
    }

    public synchronized ScriptHolderBase getOrInitHolder(ResourceLocation pos, Supplier<? extends ScriptHolderBase> holderSupplier) {
        ScriptHolderBase holder = holders.get(pos);
        if (holder == null) {
            holder = holderSupplier.get();
            holder.loadScript(context, pos);
            holders.put(pos, holder);
        }
        return holder;
    }

    private static void setBuiltInFunction(Value b) {
        b.putMember("Color", java.awt.Color.class);
        b.putMember("Font", java.awt.Font.class);
        b.putMember("BasicStroke", java.awt.BasicStroke.class);
        b.putMember("RenderingHints", java.awt.RenderingHints.class);
        b.putMember("Rectangle", java.awt.Rectangle.class);

        b.putMember("Timing", JsStaticBridge.fromStaticClass(TimingUtil.class));
        b.putMember("TextUtil", JsStaticBridge.fromStaticClass(TextUtil.class));

        b.putMember("getMatching", fn(a -> TextUtil.getCjkMatching(a[0].asString(), a[1].asBoolean())));
        b.putMember("hasCjkPart", fn(a -> TextUtil.hasCjkPart(a[0].asString())));
        b.putMember("hasNonCjkPart", fn(a -> TextUtil.hasNonCjkPart(a[0].asString())));
        b.putMember("loadResource", fn(a -> JsFunctions.loadResource(a[0].asString(), a[1].asString())));
        b.putMember("addPrefix", fn(a -> JsFunctions.addPrefix(a[0].asString(), a[1].asString(), a[2].asBoolean())));
        b.putMember("addSuffix", fn(a -> JsFunctions.addSuffix(a[0].asString(), a[1].asString())));
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
        b.putMember("rgbToColor", fn(a -> JsFunctions.rgbToColor(a[0].asInt(), a[1].asInt(), a[2].asInt())));
        b.putMember("rgbaToColor", fn(a -> JsFunctions.rgbaToColor(a[0].asInt(), a[1].asInt(), a[2].asInt(), a[3].asInt())));
        b.putMember("intToColor", fn(a -> JsFunctions.intToColor(a[0].asInt())));
        b.putMember("isLightColor", fn(a -> JsFunctions.isLightColor((java.awt.Color) a[0].asHostObject())));
    }

    private static ProxyExecutable fn(ScriptHolderBase.JsFunc f) {
        return args -> {
            try {
                return f.call(args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static void injectJsFunctions(Context context) {
        String jsFunctions = """
                """;
        context.eval("js", jsFunctions);
    }
}
