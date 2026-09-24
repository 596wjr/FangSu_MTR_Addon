package com.fangsu.modular;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 通用图形化程序运行时（插件式）。
 * <p>
 * 负责把 {@link ModularDocument}（一个积木程序）解析为一条按时间点展开的语句时间线，并每帧按绝对墙钟推进。
 * 具体积木的“语义”由注册的 {@link Handler} 实现，本类不面向任何特定内容（红绿灯/显示/倒计时等）。
 * <p>
 * 鲁棒性：
 * <ul>
 *   <li>未知积木 type：跳过该条，仅记录一次警告，不影响其它语句与整体运行；</li>
 *   <li>缺省/非法输入：程序为空或循环体缺失时保持空闲；</li>
 *   <li>每个 handler 语句执行被 try/catch 包裹，单条异常不会中断整帧与后续语句；</li>
 *   <li>周期为各「等待」之和，最小钳制为 0.05s 避免除零。</li>
 * </ul>
 */
public final class ModularProgramRuntime {

    /**
     * 一条已编译的语句：在某时间点触发。kind/type 为积木 type，handler 据此分派。
     */
    public static final class Statement {
        final double time;   // 相对循环起点的秒
        final ModularBlock block; // 原始积木（handler 从中读参数）

        Statement(double time, ModularBlock block) {
            this.time = time;
            this.block = block;
        }
    }

    /** 积木语义处理器：由具体内容（如交通灯）注册。 */
    public interface Handler {
        /**
         * 某积木类型是否由本 handler 处理。
         */
        boolean handles(String type);

        /**
         * 执行一条语句。实现方读取参数并应用副作用。
         */
        void execute(Statement stmt);
    }

    private final List<Handler> handlers = new ArrayList<>();
    /** 时间线语句，按 time 升序。 */
    private final List<Statement> statements = new ArrayList<>();
    private double period = 1d;
    private double startOffset = 0d;
    private boolean valid = false;
    private double lastPhase = -1d;
    private double lastNowSeconds = -1d;

    /** 每帧回调：可用于在 tick 中做持续推进（如倒计时递减、段码刷新）。 */
    private final List<Consumer<Double>> frameTickers = new ArrayList<>();
    private Consumer<String> warnLog = msg -> { };

    public ModularProgramRuntime setWarnLog(Consumer<String> warnLog) {
        if (warnLog != null) this.warnLog = warnLog;
        return this;
    }

    /** 注册语义 handler。 */
    public void addHandler(Handler h) {
        if (h != null) handlers.add(h);
    }

    /** 注册一个每帧推进回调（接收本帧墙钟秒）。 */
    public void addFrameTicker(Consumer<Double> ticker) {
        if (ticker != null) frameTickers.add(ticker);
    }

    public double getPeriod() {
        return period;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * 解析程序文档为时间线。空/非法则置为空（空闲）。
     *
     * @param waitType 表示“等待/推进时间”的积木 type（如交通灯的 wait 块）。
     */
    public void reload(String json, String waitType) {
        statements.clear();
        period = 1d;
        startOffset = 0d;
        valid = false;
        lastPhase = -1d;
        lastNowSeconds = -1d;

        if (json == null || json.trim().isEmpty()) return;

        ModularDocument doc;
        try {
            doc = ModularCodec.documentFromJson(json);
        } catch (Exception e) {
            warnLog.accept("ModularProgramRuntime: 文档 JSON 解析失败: " + e.getMessage());
            return;
        }
        if (doc == null || doc.getScripts().isEmpty()) return;

        ModularScript script = doc.getScripts().get(0);
        List<ModularBlock> blocks = script.getBlocks();
        if (blocks.isEmpty()) return;

        // 起始：设置初始时间偏移（可缺省）
        ModularBlock first = blocks.get(0);
        for (ModularBlock b : blocks) {
            // 找“起始”，若 handler 提供了 start 语义则用之；否则取首个
            if (isStartBlock(b)) {
                startOffset = readDouble(b, "offset", 0d);
                break;
            }
        }

        // 循环体：找容器块（含子脚本），取其第一个容器作为循环体
        List<ModularBlock> body = null;
        for (ModularBlock b : blocks) {
            if (!b.getContainers().isEmpty()) {
                body = b.getContainers().get(0).getBlockList();
                break;
            }
        }
        if (body == null) return;

        double t = 0d;
        for (ModularBlock b : body) {
            if (waitType != null && waitType.equals(b.getType())) {
                t += readDouble(b, "sec", 0d);
            } else {
                statements.add(new Statement(t, b));
            }
        }
        period = Math.max(t, 0.05d);
        valid = true;
    }

    /** 判定是否为“起始/时间偏移”块：type 以 start 语义标识。这里用约定：type 含 ".start_offset"。 */
    private boolean isStartBlock(ModularBlock b) {
        return b != null && b.getType() != null && b.getType().contains("start_offset");
    }

    /** 每帧推进。 */
    public void tick(double nowSeconds) {
        if (valid && !statements.isEmpty()) {
            double phase = (nowSeconds - startOffset) % period;
            if (phase < 0) phase += period;
            runStatementsIncrementally(phase);
        }
        // 每帧回调（倒计时递减等）
        for (Consumer<Double> ticker : frameTickers) {
            try {
                ticker.accept(nowSeconds);
            } catch (Exception e) {
                warnLog.accept("ModularProgramRuntime: 帧推进异常: " + e.getMessage());
            }
        }
        lastNowSeconds = nowSeconds;
    }

    private void runStatementsIncrementally(double phase) {
        if (lastPhase < 0d) {
            for (Statement s : statements) {
                if (s.time <= phase) dispatch(s);
            }
        } else if (phase >= lastPhase) {
            for (Statement s : statements) {
                if (s.time > lastPhase && s.time <= phase) dispatch(s);
            }
        } else {
            for (Statement s : statements) {
                if (s.time > lastPhase && s.time <= period) dispatch(s);
            }
            for (Statement s : statements) {
                if (s.time <= phase) dispatch(s);
            }
        }
        lastPhase = phase;
    }

    private void dispatch(Statement s) {
        boolean handled = false;
        for (Handler h : handlers) {
            try {
                if (h.handles(s.block.getType())) {
                    h.execute(s);
                    handled = true;
                    break;
                }
            } catch (Exception e) {
                warnLog.accept("ModularProgramRuntime: 语句执行异常 type=" + s.block.getType() + ": " + e.getMessage());
            }
        }
        if (!handled) {
            warnLog.accept("ModularProgramRuntime: 未处理积木 " + s.block.getType());
        }
    }

    /* ---- 参数读取辅助（对未知块/缺参鲁棒） ---- */

    public static double readDouble(ModularBlock b, String name, double def) {
        if (b == null) return def;
        for (ModularComponent c : b.getComponentList()) {
            if (c == null || c.isLabel() || !name.equals(c.getName())) continue;
            return c.getKind() == ModularComponent.Kind.NUMBER ? c.asNumber() : def;
        }
        return def;
    }

    public static boolean readBool(ModularBlock b, String name, boolean def) {
        if (b == null) return def;
        for (ModularComponent c : b.getComponentList()) {
            if (c == null || c.isLabel() || !name.equals(c.getName())) continue;
            return c.getKind() == ModularComponent.Kind.BOOLEAN ? c.asBoolean() : def;
        }
        return def;
    }

    public static String readChoice(ModularBlock b, String name, String def) {
        if (b == null) return def;
        for (ModularComponent c : b.getComponentList()) {
            if (c == null || c.isLabel() || !name.equals(c.getName())) continue;
            return c.getKind() == ModularComponent.Kind.CHOICE ? c.asString() : def;
        }
        return def;
    }

    public double getStartOffset() {
        return startOffset;
    }
}
