package com.fangsu.userScripts;

import com.fangsu.Main;
import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.scripting.*;
import com.fangsu.utils.ModuleAccessHelper;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ScriptManager {
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private static final ScriptManager INSTANCE = new ScriptManager();
    private static final long FAIL_TIMEOUT_MS = 4000;
    private static final long SCRIPT_EXECUTION_TIMEOUT_MS = 500;

    protected HostAccess hostAccess;
    protected Engine engine;
    private final Map<ResourceLocation, ScriptHolderBase> holders;
    private boolean isShutdown = false;

    public static final ExecutorService SCRIPT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fangsu-script-manager");
        t.setDaemon(true);
        return t;
    });

    private static final ScheduledExecutorService SCRIPT_WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fangsu-script-watchdog");
                t.setDaemon(true);
                return t;
            });

    public void init() {
        if (initialized.get()) throw new IllegalStateException("ScriptManager has already been initialized");

        ModuleAccessHelper.ensureModuleAccess();

        createEngine();

        initialized.set(true);
    }

    private ScriptManager() {
        this.holders = new ConcurrentHashMap<>();
    }

    private void createEngine() {
        long beginTime = System.currentTimeMillis();

        hostAccess = HostAccess.newBuilder()
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


        this.engine = Engine.newBuilder("js")
                .allowExperimentalOptions(true)
//                .option("engine.WarnInterpreterOnly", "false")
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "2020")
                .option("log.file", "./logs/latest.log")
                .build();

        Main.LOGGER.info("Initialized ScriptManager engine in {} ms", System.currentTimeMillis() - beginTime);
    }

    protected static void initializeGlobalBindings(Context context) {
        Value bindings = context.getBindings("js");

        // Java 类绑定
        bindings.putMember("Color", createColorBinding());
        bindings.putMember("Font", java.awt.Font.class);
        bindings.putMember("BasicStroke", java.awt.BasicStroke.class);
        bindings.putMember("RenderingHints", java.awt.RenderingHints.class);
        bindings.putMember("Rectangle", java.awt.Rectangle.class);

        inject(context, java.awt.geom.Point2D.class, "Point2D");
        inject(context, java.awt.geom.Rectangle2D.class, "Rectangle2D");
        inject(context, java.awt.geom.Line2D.class, "Line2D");
        inject(context, java.awt.geom.Ellipse2D.class, "Ellipse2D");
        inject(context, java.awt.geom.Arc2D.class, "Arc2D");
        inject(context, java.awt.geom.CubicCurve2D.class, "CubicCurve2D");
        inject(context, java.awt.geom.QuadCurve2D.class, "QuadCurve2D");
        inject(context, java.awt.geom.Path2D.class, "Path2D");
        inject(context, java.awt.geom.RoundRectangle2D.class, "RoundRectangle2D");
        inject(context, java.awt.geom.AffineTransform.class, "AffineTransform");

        inject(context, java.awt.Polygon.class, "Polygon");
        inject(context, java.awt.Rectangle.class, "Rectangle");
        inject(context, java.awt.Shape.class, "Shape");

        // 工具类绑定
        bindings.putMember("Timing", JsStaticBridge.fromStaticClass(TimingUtil.class));
        bindings.putMember("TextUtil", JsStaticBridge.fromStaticClass(TextUtil.class));
        bindings.putMember("MinecraftClient", JsStaticBridge.fromStaticClass(MinecraftClientUtil.class));
        bindings.putMember("Resources", JsStaticBridge.fromStaticClass(JsResources.class));

        // 数学类
        bindings.putMember("Vector3f", JsStaticBridge.fromStaticClass(Vector3f.class));

        // 函数绑定
        bindings.putMember("drawStrUnified", fn(a -> JsFunctions.jsDrawStrUnified(a[0].asHostObject(), a[1].asHostObject(), a[2].asString(), a[3].asDouble(), a[4].asDouble(), a[5].asDouble(), a[6].asInt())));
        bindings.putMember("getUnifiedStringWidth", fn(a -> JsFunctions.jsGetUnifiedStringWidth(a[0].asHostObject(), a[1].asHostObject(), a[2].asString(), a[3].asDouble())));
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
        bindings.putMember("routeToObj", fn(a -> JsFunctions.jsRouteToObj(a[0].asHostObject())));
    }

    public static ScriptManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取或初始化脚本持有者（线程安全）
     */
    public ScriptHolderBase getOrInitHolder(ResourceLocation pos, Supplier<? extends ScriptHolderBase> holderSupplier) {
        if (!initialized.get()) {
            return null;
        }

        if (isShutdown) {
            throw new IllegalStateException("ScriptManager has been shutdown");
        }

        return holders.computeIfAbsent(pos, k -> {
            ScriptHolderBase holder = holderSupplier.get();
            holder.loadScript(k, null);
            return holder;
        });
    }

//    /**
//     * 重载指定脚本
//     */
//    public boolean reloadScript(ResourceLocation pos) {
//        ScriptHolderBase holder = holders.get(pos);
//        if (holder != null) {
//            holder.close();
//            holder.loadScript(context, pos, null);
//            return true;
//        }
//        return false;
//    }
//
//    /**
//     * 重载所有脚本
//     */
//    public void reloadAllScripts(Context context) {
//        holders.forEach((pos, holder) -> {
//            holder.close();
//            holder.loadScript(context, pos, null);
//        });
//    }

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
            if (engine != null) {
                engine.close();
            }
            SCRIPT_WATCHDOG.shutdownNow();
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

    protected static void inject(Context context, Class<?> clazz, String method, String alias) {
        if (alias == null) alias = method;
        context.eval("js", "var " + alias + " = Java.type('" + clazz.getName() + "')." + method + ";");
    }

    protected static void inject(Context context, Class<?> clazz, String alias) {
        if (alias == null) alias = clazz.getSimpleName();
        context.eval("js", "var " + alias + " = Java.type('" + clazz.getName() + "');");
    }

    protected void inject(Context context, String key, String value) {
        context.eval("js", "var " + key + " = '" + value + "';");
    }

    public static long getFailTimeoutMs() {
        return FAIL_TIMEOUT_MS;
    }

    private static ProxyObject createColorBinding() {
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

    /**
     * 同步执行 JS 函数（用于绘制回调，确保 GraphicsTextureHelper 能正确感知绘制完成状态）。
     * 直接在调用线程上执行 JS，阻塞直到完成或失败。
     * 与 requestRunFunctionWithCallback 不同，这个方法不提交到 SCRIPT_EXECUTOR，
     * 因此调用者可以精确知道 JS 执行是否成功。
     * 如果 JS 执行失败或函数不存在，会抛出异常让调用者重试。
     */
    public void requestRunFunctionSync(ScriptHolderBase holder, Runnable callback, String name, Object... params) {
        if (!initialized.get() || isShutdown) return;
        if (holder == null) return;
        holder.runFunctionSync(name, callback, params);
    }

    //线程优化
    public synchronized void requestRunFunction(ScriptHolderBase holder, String name, Object... params) {
        executeScriptWithTimeout(holder, name, params, () -> {
        });
    }

    public synchronized void requestRunFunctionWithCallback(ScriptHolderBase holder, Runnable callback, String name, Object... params) {
        executeScriptWithTimeout(holder, name, params, callback);
    }

    public synchronized void requestRunFunctionWithResult(ScriptHolderBase holder,
                                                          Consumer<Value> consumer,
                                                          String name,
                                                          Object... params) {
        executeScriptWithTimeout(holder, name, params, consumer);
    }

    private void executeScriptWithTimeout(ScriptHolderBase holder,
                                          String name,
                                          Object[] params,
                                          Runnable callback) {
        executeWithWatchdog(holder, name, params, () -> {
            holder.runFunction(name, callback, params);
            return null; // 不需要返回值
        }, null);
    }

    private void executeScriptWithTimeout(ScriptHolderBase holder,
                                          String name,
                                          Object[] params,
                                          Consumer<Value> resultConsumer) {
        executeWithWatchdog(holder, name, params, () -> {
            if (resultConsumer != null) {
                holder.runFunctionWithResult(name, resultConsumer, params);
            } else {
                holder.runFunction(name, null, params);
            }
            return null;
        }, null);
    }

    /**
     * 带看门狗超时的脚本执行
     */
    private void executeWithWatchdog(ScriptHolderBase holder,
                                     String name,
                                     Object[] params,
                                     Supplier<Void> action,
                                     Consumer<Value> resultConsumer) {
        if (!initialized.get() || isShutdown) return;
        if (holder == null || !holder.hasFunction(name)) return;

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            // 清除可能残留的中断状态（重要！）
            Thread.interrupted();
            try {
                action.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 正常结束后主动取消看门狗并清除中断
                clearContextInterrupt(holder);
            }
        }, SCRIPT_EXECUTOR);

        // 安排看门狗：超时后从外部线程发起中断
        ScheduledFuture<?> watchdog = SCRIPT_WATCHDOG.schedule(() -> {
            if (!future.isDone()) {
                // 关键：从看门狗线程调用 interrupt，此时执行线程正在 runFunction 内部等待 JS
                try {
                    holder.context.interrupt(Duration.ZERO); // 立即中断
                } catch (TimeoutException ignored) {
                }
                future.cancel(true); // 中断 Java 线程（配合下面 exceptionally 处理）
                Main.LOGGER.warn("Script function {} timed out after {}ms, interrupting",
                        name, SCRIPT_EXECUTION_TIMEOUT_MS);
            }
        }, SCRIPT_EXECUTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // 任务结束时取消看门狗
        future.whenComplete((res, ex) -> {
            watchdog.cancel(false);
        }).exceptionally(throwable -> {
            if (throwable instanceof CancellationException) {
                Main.LOGGER.error("Script function {} was cancelled due to timeout", name);
            } else {
                Main.LOGGER.error("Unexpected error in script execution: {}", throwable.getMessage());
            }
            return null;
        });
    }

    /**
     * 清除上下文中断状态（需要重置超时倒计时）
     */
    private void clearContextInterrupt(ScriptHolderBase holder) {
        try {
            holder.context.interrupt(Duration.ZERO);
        } catch (TimeoutException ignored) {
        }
    }
}