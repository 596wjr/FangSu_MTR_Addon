package com.fangsu.modular;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 交通灯图形化程序的运行时（基于通用 {@link ModularProgramRuntime} 的插件式 handler 实现）。
 * <p>
 * 语义（灯/显示/倒计时）由本站注册的 {@link ModularProgramRuntime.Handler} 与帧推进器实现，
 * 时间线解析、鲁棒容错、未知块跳过等由通用运行时负责，本站不面向特定结构硬编码执行流程。
 * <p>
 * 对外 API（reload/tick/getLightStates/getDisplayGroups/各显示 setter）保持稳定，供
 * {@code BlockEntityTrafficLight} 调用。
 */
public final class TrafficLightProgram {

    /** 一个显示组（red/green）的运行时状态。 */
    public static final class DisplayGroup {
        private boolean on = false;          // 整组亮灭
        private byte[] masks;                // 每位共阳段码（0xFF=全灭）；长度=该组显示位数
        private boolean counting = false;    // 是否倒计时中
        private double countdown = 0d;       // 倒计时剩余秒

        DisplayGroup(int digits) {
            this.masks = new byte[digits];
            for (int i = 0; i < digits; i++) masks[i] = (byte) 0xFF;
        }

        public boolean isOn() {
            return on;
        }

        public byte[] getMasks() {
            return masks.clone();
        }

        public boolean isCounting() {
            return counting;
        }

        public double getCountdown() {
            return countdown;
        }
    }

    private final ModularProgramRuntime runtime = new ModularProgramRuntime();
    private final Map<String, Boolean> lightStates = new LinkedHashMap<>();
    private final Map<String, DisplayGroup> displayGroups = new LinkedHashMap<>();

    private boolean valid = false;

    public TrafficLightProgram() {
        // 鲁棒性：未知块/执行异常记录到日志，便于排查且不中断运行
        runtime.setWarnLog(msg -> com.fangsu.Main.LOGGER.warn("[TrafficLightProgram] {}", msg));
        // 帧推进器：倒计时按真实墙钟时间差递减并刷新段码
        runtime.addFrameTicker(this::tickCountdowns);
        // 交通灯语义 handler（灯 / 显示块）
        runtime.addHandler(new ModularProgramRuntime.Handler() {
            @Override
            public boolean handles(String type) {
                return isTrafficLightType(type);
            }

            @Override
            public void execute(ModularProgramRuntime.Statement stmt) {
                ModularBlock b = stmt.block;
                String type = b.getType();
                if (TrafficLightProgramBlocks.TYPE_SET_LIGHT.equals(type)) {
                    String light = ModularProgramRuntime.readChoice(b, "light", "green");
                    boolean on = ModularProgramRuntime.readBool(b, "on", true);
                    setLight(light, on);
                } else if (TrafficLightProgramBlocks.TYPE_DISPLAY_ON.equals(type)) {
                    String group = ModularProgramRuntime.readChoice(b, "disp", "red");
                    boolean on = ModularProgramRuntime.readBool(b, "on", true);
                    setDisplayOnInstant(group, on);
                } else if (TrafficLightProgramBlocks.TYPE_DISPLAY_VALUE.equals(type)) {
                    String group = ModularProgramRuntime.readChoice(b, "disp", "red");
                    long value = (long) ModularProgramRuntime.readDouble(b, "value", 0d);
                    setDisplayValue(group, value);
                } else if (TrafficLightProgramBlocks.TYPE_DISPLAY_HEX.equals(type)) {
                    String group = ModularProgramRuntime.readChoice(b, "disp", "red");
                    long value = (long) ModularProgramRuntime.readDouble(b, "value", 0xC0d);
                    setDisplayHex(group, value);
                } else if (TrafficLightProgramBlocks.TYPE_DISPLAY_CD_START.equals(type)) {
                    String group = ModularProgramRuntime.readChoice(b, "disp", "red");
                    startCountdown(group);
                } else if (TrafficLightProgramBlocks.TYPE_DISPLAY_CD_STOP.equals(type)) {
                    String group = ModularProgramRuntime.readChoice(b, "disp", "red");
                    stopCountdown(group);
                }
            }
        });
    }

    private boolean isTrafficLightType(String type) {
        return TrafficLightProgramBlocks.TYPE_SET_LIGHT.equals(type)
                || TrafficLightProgramBlocks.TYPE_DISPLAY_ON.equals(type)
                || TrafficLightProgramBlocks.TYPE_DISPLAY_VALUE.equals(type)
                || TrafficLightProgramBlocks.TYPE_DISPLAY_HEX.equals(type)
                || TrafficLightProgramBlocks.TYPE_DISPLAY_CD_START.equals(type)
                || TrafficLightProgramBlocks.TYPE_DISPLAY_CD_STOP.equals(type);
    }

    /** 解析程序。displayDigits 为各显示组位数（配置注入）。 */
    public void reload(String json, Map<String, Integer> displayDigits) {
        lightStates.clear();
        displayGroups.clear();
        for (Map.Entry<String, Integer> e : displayDigits.entrySet()) {
            displayGroups.put(e.getKey(), new DisplayGroup(Math.max(0, e.getValue())));
        }
        runtime.reload(json, TrafficLightProgramBlocks.TYPE_WAIT);
        valid = runtime.isValid();
    }

    /** 每帧推进。 */
    public void tick(double nowSeconds) {
        runtime.tick(nowSeconds);
    }

    /* ================= 灯状态 ================= */

    /** 设置一盏灯的亮灭（幂等：同值不影响其它）。读灯状态时叠加，最终由 resetLights + apply 得到。 */
    private void setLight(String light, boolean on) {
        lightStates.put(light, on);
    }

    public Map<String, Boolean> getLightStates() {
        // 灯时间顺序语义：默认全灭，由时间线 handler 逐条叠加（最终覆盖）。由于通用运行时按相位增量触发，
        // 灯状态在有灯积木时为最近一次触发的结果；无灯积木时保持上次。
        return lightStates;
    }

    /* ================= 显示组状态 ================= */

    public Map<String, DisplayGroup> getDisplayGroups() {
        return displayGroups;
    }

    private void tickCountdowns(double nowSeconds) {
        // 倒计时递减用真实墙钟时间差（由运行时记录，这里直接按上一帧与当前帧差）
        double last = lastTickSeconds < 0d ? nowSeconds : lastTickSeconds;
        double frameDt = Math.max(0d, nowSeconds - last);
        lastTickSeconds = nowSeconds;
        for (DisplayGroup g : displayGroups.values()) {
            if (g.counting) {
                g.countdown = Math.max(0d, g.countdown - frameDt);
                applyCountdownMask(g);
                if (g.countdown <= 0d) g.counting = false;
            }
        }
    }

    private double lastTickSeconds = -1d;

    /** 设置显示[ENUM]为[BOOL]：整组亮灭（时间线触发时立即生效）。 */
    private void setDisplayOnInstant(String group, boolean on) {
        DisplayGroup g = displayGroups.get(group);
        if (g != null) g.on = on;
    }

    /** 设置显示[ENUM]为[BOOL]：整组亮灭（外部直接调用）。 */
    public void setDisplayOn(String group, boolean on) {
        setDisplayOnInstant(group, on);
    }

    /** 设置显示[ENUM]为[INT]：把整数按位拆分显示到该组所有显示位（第1位高阶），停止倒计时。 */
    public void setDisplayValue(String group, long value) {
        DisplayGroup g = displayGroups.get(group);
        if (g == null) return;
        g.counting = false;
        int digits = g.masks.length;
        long display = Math.abs(value);
        for (int i = 0; i < digits; i++) {
            long place = 1;
            for (int j = 0; j < digits - 1 - i; j++) place *= 10;
            int digit = (int) ((display / place) % 10);
            g.masks[i] = digitMask(digit);
        }
    }

    /** 设置显示[ENUM]显示内容为[INT(16进制)]：按共阳型段码送数（hgfedcba，C0=显示0）。 */
    public void setDisplayHex(String group, long value) {
        DisplayGroup g = displayGroups.get(group);
        if (g == null) return;
        g.counting = false;
        long v = value & 0xFFFFFFFFFFFFFFFFL;
        for (int i = 0; i < g.masks.length; i++) {
            int shift = (g.masks.length - 1 - i) * 8;
            g.masks[i] = (shift >= 0 && shift < 64) ? (byte) ((v >> shift) & 0xFF) : (byte) 0xFF;
        }
    }

    /** 显示[ENUM]开始倒计时：以当前显示值启动每秒-1；已在倒计时则保持（幂等）。 */
    public void startCountdown(String group) {
        DisplayGroup g = displayGroups.get(group);
        if (g == null) return;
        if (g.counting) return;
        long init = 0;
        for (int i = 0; i < g.masks.length; i++) {
            init = init * 10 + maskToDigit(g.masks[i]);
        }
        g.countdown = init;
        g.counting = true;
    }

    /** 显示[ENUM]停止倒计时。 */
    public void stopCountdown(String group) {
        DisplayGroup g = displayGroups.get(group);
        if (g == null) return;
        g.counting = false;
    }

    private void applyCountdownMask(DisplayGroup g) {
        long display = Math.max(0, (long) Math.round(g.countdown));
        int digits = g.masks.length;
        for (int i = 0; i < digits; i++) {
            long place = 1;
            for (int j = 0; j < digits - 1 - i; j++) place *= 10;
            int digit = (int) ((display / place) % 10);
            g.masks[i] = digitMask(digit);
        }
    }

    /** 十进制数字→共阳段码（0=亮、1=灭；段序 bit0=a … bit6=g，bit7=h）。
     * 段字母按标准 7 段布局：a=上 b=右上 c=右下 d=下 e=左下 f=左上 g=中 h=点。 */
    private static byte digitMask(int digit) {
        switch (digit) {
            case 0: return (byte) 0xC0;
            case 1: return (byte) 0xF9;
            case 2: return (byte) 0xA4;
            case 3: return (byte) 0xB0;
            case 4: return (byte) 0x99;
            case 5: return (byte) 0x92;
            case 6: return (byte) 0x82;
            case 7: return (byte) 0xF8;
            case 8: return (byte) 0x80;
            case 9: return (byte) 0x90;
            default: return (byte) 0xFF;
        }
    }

    /** 共阳段码→十进制数字（倒计时初值反解）。 */
    private static int maskToDigit(byte mask) {
        int m = mask & 0xFF;
        for (int d = 0; d <= 9; d++) {
            if ((digitMask(d) & 0xFF) == m) return d;
        }
        return 0;
    }
}
