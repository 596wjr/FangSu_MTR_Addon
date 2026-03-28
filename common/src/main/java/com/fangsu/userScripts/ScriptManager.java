package com.fangsu.userScripts;

import com.fangsu.Main;
import com.fangsu.scripting.*;
import com.fangsu.utils.ModuleAccessHelper;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ScriptManager {
    private boolean initialized = false;

    private static final ScriptManager INSTANCE = new ScriptManager();
    private static final long FAIL_TIMEOUT_MS = 4000;

    private Context context;
    private final Map<ResourceLocation, ScriptHolderBase> holders;
    private boolean isShutdown = false;

    public static final ExecutorService SCRIPT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fangsu-script-manager");
        t.setDaemon(true);
        return t;
    });

    public void init() {
        if (initialized) throw new IllegalStateException("ScriptManager has already been initialized");

        ModuleAccessHelper.ensureModuleAccess();

        createContext();

        initialized = true;
    }

    private ScriptManager() {
        this.holders = new ConcurrentHashMap<>();
    }

    private void createContext() {
        long beginTime = System.currentTimeMillis();

        HostAccess hostAccess = HostAccess.newBuilder()
                .allowPublicAccess(true)
                .allowAllImplementations(true)
                .allowAllClassImplementations(true)
                .allowArrayAccess(true)
                .allowListAccess(true)
                .allowBufferAccess(true)
                .allowIterableAccess(true)
                .allowIteratorAccess(true)
                .allowMapAccess(true)
                .allowAccessInheritance(true)
                .allowBigIntegerNumberAccess(true)
                // Double -> Float 自动转换（优先级最高）
                .targetTypeMapping(
                        Double.class,
                        Float.class,
                        value -> true,  // 所有 Double 都尝试转换
                        value -> value.floatValue(),
                        HostAccess.TargetMappingPrecedence.HIGHEST
                )
                // Double -> Integer 自动转换
                .targetTypeMapping(
                        Double.class,
                        Integer.class,
                        value -> true,
                        value -> (int) Math.round(value),
                        HostAccess.TargetMappingPrecedence.HIGH
                )
                // Double -> Long 自动转换
                .targetTypeMapping(
                        Double.class,
                        Long.class,
                        value -> true,
                        value -> Math.round(value),
                        HostAccess.TargetMappingPrecedence.HIGH
                )
                // Double -> Short 自动转换
                .targetTypeMapping(
                        Double.class,
                        Short.class,
                        value -> true,
                        value -> (short) Math.round(value),
                        HostAccess.TargetMappingPrecedence.LOW
                )
                // Double -> Byte 自动转换
                .targetTypeMapping(
                        Double.class,
                        Byte.class,
                        value -> true,
                        value -> (byte) Math.round(value),
                        HostAccess.TargetMappingPrecedence.LOW
                )
                // Int -> Float
                .targetTypeMapping(
                        Integer.class,
                        Float.class,
                        value -> true,
                        value -> value.floatValue(),
                        HostAccess.TargetMappingPrecedence.LOW
                )
                .build();


        this.context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
//                .option("engine.WarnInterpreterOnly", "false")
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "2020")
                .option("log.file", "./logs/latest.log")
//                .option("engine.timeout", "5000")
//                .option("engine.ScriptTimeout", "5000")
                .allowHostAccess(hostAccess)
                .allowHostClassLookup(c -> true)
                .allowCreateThread(true)
//                .allowHostAccess(org.graalvm.polyglot.HostAccess.ALL)
                .build();

        initializeGlobalBindings();

        Main.LOGGER.info("Initialized ScriptManager in {} ms", System.currentTimeMillis() - beginTime);
    }

    private void initializeGlobalBindings() {
        Value bindings = context.getBindings("js");

        // Java 类绑定
        bindings.putMember("Color", createColorBinding());
        bindings.putMember("Font", java.awt.Font.class);
        bindings.putMember("BasicStroke", java.awt.BasicStroke.class);
        bindings.putMember("RenderingHints", java.awt.RenderingHints.class);
        bindings.putMember("Rectangle", java.awt.Rectangle.class);

        inject(java.awt.geom.Point2D.class, "Point2D");
        inject(java.awt.geom.Rectangle2D.class, "Rectangle2D");
        inject(java.awt.geom.Line2D.class, "Line2D");
        inject(java.awt.geom.Ellipse2D.class, "Ellipse2D");
        inject(java.awt.geom.Arc2D.class, "Arc2D");
        inject(java.awt.geom.CubicCurve2D.class, "CubicCurve2D");
        inject(java.awt.geom.QuadCurve2D.class, "QuadCurve2D");
        inject(java.awt.geom.Path2D.class, "Path2D");
        inject(java.awt.geom.RoundRectangle2D.class, "RoundRectangle2D");

        inject(java.awt.Polygon.class, "Polygon");
        inject(java.awt.Rectangle.class, "Rectangle");
        inject(java.awt.Shape.class, "Shape");

        // 工具类绑定
        bindings.putMember("Timing", JsStaticBridge.fromStaticClass(TimingUtil.class));
        bindings.putMember("TextUtil", JsStaticBridge.fromStaticClass(TextUtil.class));
        bindings.putMember("MinecraftClient", JsStaticBridge.fromStaticClass(MinecraftClientUtil.class));

        // 函数绑定
        bindings.putMember("drawStrUnified", fn(a -> G2dTextHelper.drawStrUnified(a[0].asHostObject(), a[1].asHostObject(), a[2].asString(), a[3].asDouble(), a[4].asDouble(), a[5].asDouble(), a[6].asInt())));
        bindings.putMember("getUnifiedStringWidth", fn(a -> G2dTextHelper.getUnifiedStringWidth(a[0].asHostObject(), a[1].asHostObject(), a[2].asString(), a[4].asFloat())));
        bindings.putMember("drawStrDL", fn(a -> JsFunctions.jsDrawStrDl(a[0].asHostObject(), a[1].asHostObject(), a[2].asHostObject(), a[3].asString(), a[4].asDouble(), a[5].asDouble(), a[6].asDouble(), a[7].asInt(), a[8].asInt())));
        bindings.putMember("getDLStringWidth", fn(a -> JsFunctions.jsGetDLStringWidth(a[0].asHostObject(), a[1].asHostObject(), a[2].asHostObject(), a[3].asString(), a[4].asDouble())));
        bindings.putMember("getMatching", fn(a -> TextUtil.getCjkMatching(a[0].asString(), a[1].asBoolean())));
        bindings.putMember("hasCjkPart", fn(a -> TextUtil.hasCjkPart(a[0].asString())));
        bindings.putMember("hasNonCjkPart", fn(a -> TextUtil.hasNonCjkPart(a[0].asString())));
        bindings.putMember("loadResource", fn(a -> JsFunctions.loadResource(a[0].asString(), a[1].asString())));
        bindings.putMember("addPrefix", fn(a -> JsFunctions.addPrefix(a[0].asString(), a[1].asString(), a[2].asBoolean())));
        bindings.putMember("addSuffix", fn(a -> JsFunctions.addSuffix(a[0].asString(), a[1].asString())));
        bindings.putMember("setDebugInfo", fn(a -> {
            JsFunctions.setDebugInfo(a[0].asString());
            return null;
        }));
        bindings.putMember("setWarnInfo", fn(a -> {
            JsFunctions.setWarnInfo(a[0].asString());
            return null;
        }));
        bindings.putMember("setErrorInfo", fn(a -> {
            JsFunctions.setErrorInfo(a[0].asString());
            return null;
        }));
        bindings.putMember("getCurrentDate", fn(a -> JsFunctions.getCurrentDate()));
        bindings.putMember("getCurrentWeekday", fn(a -> JsFunctions.getCurrentWeekday()));
        bindings.putMember("formatDate", fn(a -> JsFunctions.formatDate(a[0].asBoolean())));
        bindings.putMember("formatWeekday", fn(a -> JsFunctions.formatWeekday(a[0].asBoolean())));
        bindings.putMember("rgbToColor", fn(a -> JsFunctions.rgbToColor(a[0].asInt(), a[1].asInt(), a[2].asInt())));
        bindings.putMember("rgbaToColor", fn(a -> JsFunctions.rgbaToColor(a[0].asInt(), a[1].asInt(), a[2].asInt(), a[3].asInt())));
        bindings.putMember("intToColor", fn(a -> JsFunctions.intToColor(a[0].asInt())));
        bindings.putMember("isLightColor", fn(a -> JsFunctions.isLightColor((java.awt.Color) a[0].asHostObject())));
        bindings.putMember("parseLineName", fn(a -> JsFunctions.parseLineName(a[0].asString())));
        bindings.putMember("getCJKLineName", fn(a -> JsFunctions.getCJKLineName(a[0].asString())));
        bindings.putMember("getNonCJKLineName", fn(a -> JsFunctions.getNonCJKLineName(a[0].asString())));
        bindings.putMember("isNumLine", fn(a -> JsFunctions.isNumLine(a[0].asString())));
        bindings.putMember("changeImageColor", fn(a -> JsFunctions.changeImageColor(a[0].asHostObject(), a[1].asHostObject())));
    }

    public static ScriptManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取或初始化脚本持有者（线程安全）
     */
    public ScriptHolderBase getOrInitHolder(ResourceLocation pos, Supplier<? extends ScriptHolderBase> holderSupplier) {
        if (!initialized) {
            return null;
        }

        if (isShutdown) {
            throw new IllegalStateException("ScriptManager has been shutdown");
        }

        return holders.computeIfAbsent(pos, k -> {
            ScriptHolderBase holder = holderSupplier.get();
            holder.loadScript(context, k, null);
            return holder;
        });
    }

    /**
     * 重载指定脚本
     */
    public boolean reloadScript(ResourceLocation pos) {
        ScriptHolderBase holder = holders.get(pos);
        if (holder != null) {
            holder.close();
            holder.loadScript(context, pos, null);
            return true;
        }
        return false;
    }

    /**
     * 重载所有脚本
     */
    public void reloadAllScripts() {
        holders.forEach((pos, holder) -> {
            holder.close();
            holder.loadScript(context, pos, null);
        });
    }

    /**
     * 移除脚本持有者
     */
    public void removeHolder(ResourceLocation pos) {
        ScriptHolderBase holder = holders.remove(pos);
        if (holder != null) {
            holder.close();
        }
    }

    /**
     * 关闭管理器（游戏结束时调用）
     */
    public void shutdown() {
        if (!isShutdown) {
            isShutdown = true;
            holders.values().forEach(ScriptHolderBase::close);
            holders.clear();
            if (context != null) {
                context.close();
            }
        }
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

    protected void inject(Class<?> clazz, String method, String alias) {
        if (alias == null) alias = method;
        context.eval("js", "var " + alias + " = Java.type('" + clazz.getName() + "')." + method + ";");
    }

    protected void inject(Class<?> clazz, String alias) {
        if (alias == null) alias = clazz.getSimpleName();
        context.eval("js", "var " + alias + " = Java.type('" + clazz.getName() + "');");
    }

    protected void inject(String key, String value) {
        context.eval("js", "var " + key + " = '" + value + "';");
    }

    public static long getFailTimeoutMs() {
        return FAIL_TIMEOUT_MS;
    }

    private ProxyObject createColorBinding() {
        Map<String, Object> map = new HashMap<>();

        // 静态常量
        map.put("WHITE", java.awt.Color.WHITE);
        map.put("BLACK", java.awt.Color.BLACK);
        map.put("RED", java.awt.Color.RED);
        map.put("GREEN", java.awt.Color.GREEN);
        map.put("BLUE", java.awt.Color.BLUE);
        map.put("YELLOW", java.awt.Color.YELLOW);
        map.put("CYAN", java.awt.Color.CYAN);
        map.put("MAGENTA", java.awt.Color.MAGENTA);
        map.put("ORANGE", java.awt.Color.ORANGE);
        map.put("PINK", java.awt.Color.PINK);
        map.put("LIGHT_GRAY", java.awt.Color.LIGHT_GRAY);
        map.put("GRAY", java.awt.Color.GRAY);
        map.put("DARK_GRAY", java.awt.Color.DARK_GRAY);

        // 常用静态方法（如果需要）
        map.put("decode", (ProxyExecutable) args -> {
            String hex = args[0].asString();
            return java.awt.Color.decode(hex);
        });

        map.put("getHSBColor", (ProxyExecutable) args -> {
            float h = (float) args[0].asDouble();
            float s = (float) args[1].asDouble();
            float b = (float) args[2].asDouble();
            return java.awt.Color.getHSBColor(h, s, b);
        });

        map.put("RGBtoHSB", (ProxyExecutable) args -> {
            int r = args[0].asInt();
            int g = args[1].asInt();
            int b = args[2].asInt();
            float[] hsb = new float[3];
            java.awt.Color.RGBtoHSB(r, g, b, hsb);
            return hsb; // 注意返回 float[]，在 JS 中会转为数组
        });

        map.put("HSBtoRGB", (ProxyExecutable) args -> {
            float h = (float) args[0].asDouble();
            float s = (float) args[1].asDouble();
            float b = (float) args[2].asDouble();
            return java.awt.Color.HSBtoRGB(h, s, b);
        });

        return ProxyObject.fromMap(map);
    }

    //线程优化
    public synchronized void requestRunFunction(ScriptHolderBase holder, String name, Object... params) {
        if (isShutdown) {
            return;
        }
        if (holder == null) {
            return;
        }
        if (holder.hasFunction(name)) {
            CompletableFuture.runAsync(() -> {
                holder.runFunction(name, params);
            }, ScriptManager.SCRIPT_EXECUTOR);
        }
    }

    public synchronized void requestRunFunctionWithResult(ScriptHolderBase holder, Consumer<Value> consumer, String name, Object... params) {
        if (isShutdown) {
            return;
        }
        if (holder == null) {
            return;
        }
        if (holder.hasFunction(name)) {
            CompletableFuture.runAsync(() -> {
                holder.runFunctionWithResult(name, consumer, params);
            }, SCRIPT_EXECUTOR);
        }
        return;
    }
}