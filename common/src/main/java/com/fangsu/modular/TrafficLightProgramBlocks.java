package com.fangsu.modular;

import java.awt.*;
import java.util.List;

/** 交通灯图形化程序积木库（白名单）。
 * <p>
 * 交通灯的程序由编辑器用固定白名单搭建，逻辑对应旧版 {@code traffic_light.js} 的循环播放：
 * <pre>
 * 当程序开始时以初始时间偏移启动；
 * 循环（无下方链接，即 forever，包裹灯与等待）；
 *   设置灯[下拉框]为[布尔]；
 *   等待[秒]；
 * </pre>
 * 运行时按“总时长 = 各等待之和 + 初始偏移”在循环内取模推进，到点切换灯亮灭。
 * <p>
 * 本库只负责注册积木模板与提供“程序白名单”（{@link #ALLOWED}）；白名单本身不侵入编辑器，
 * 而是作为钩子由使用方（如 {@code BlockEntityTrafficLight}）传入 {@code ProgramConfig}。
 */
public final class TrafficLightProgramBlocks {

    private TrafficLightProgramBlocks() {
    }

    /** 本积木库统一的族 id。 */
    public static final String FAM_TRAFFIC_LIGHT = "modular_traffic_light";

    /** 各积木 type（供白名单/运行时识别）。 */
    public static final String TYPE_START_OFFSET = "modular.tl.start_offset";
    public static final String TYPE_LOOP = "modular.tl.loop";
    public static final String TYPE_SET_LIGHT = "modular.tl.set_light";
    public static final String TYPE_WAIT = "modular.tl.wait";
    /** 数码管显示块。 */
    public static final String TYPE_DISPLAY_ON = "modular.tl.display_on";      // 设置显示[ENUM]为[BOOL]
    public static final String TYPE_DISPLAY_VALUE = "modular.tl.display_value"; // 设置显示[ENUM]为[INT]
    public static final String TYPE_DISPLAY_HEX = "modular.tl.display_hex";     // 设置显示[ENUM]显示内容为[INT(16进制)]
    public static final String TYPE_DISPLAY_CD_START = "modular.tl.display_cd_start"; // 显示[ENUM]开始倒计时
    public static final String TYPE_DISPLAY_CD_STOP = "modular.tl.display_cd_stop";   // 显示[ENUM]停止倒计时

    /** 交通灯程序允许在编辑器中使用的积木 type 白名单（钩子，供 ProgramConfig 传入）。 */
    public static final java.util.Set<String> ALLOWED = java.util.Set.of(
            TYPE_START_OFFSET,
            TYPE_LOOP,
            TYPE_SET_LIGHT,
            TYPE_WAIT,
            TYPE_DISPLAY_ON,
            TYPE_DISPLAY_VALUE,
            TYPE_DISPLAY_HEX,
            TYPE_DISPLAY_CD_START,
            TYPE_DISPLAY_CD_STOP
    );

    /** 灯标签（与旧版 subModels/主色一致）：green/yellow/red。 */
    public static final List<String> LIGHT_ITEMS = List.of("green", "yellow", "red");

    /** 数码管显示组（对应 displays 配置的键）：red/green。 */
    public static final List<String> DISPLAY_ITEMS = List.of("red", "green");

    public static void init() {
        ModularBlockFactory f = ModularBlockFactory.getInstance();
        // 图例：族颜色用黄绿红三色混合的交通灯黄
        f.registerFamily(FAM_TRAFFIC_LIGHT, "modular.fangsu.family.modular_traffic_light", new Color(0xf2c037));

        // 起始：设置初始时间偏移[double]（HAT）
        f.register(ModularBlockBuilder.of(TYPE_START_OFFSET)
                .family(FAM_TRAFFIC_LIGHT).color(new Color(0xe07b39)).hat()
                .text("modular.fangsu.tl.start_offset")
                .num("offset", 0d)
                .build());

        // 循环（无下方链接）：C 形容器，永久循环包住灯与等待
        f.register(ModularBlockBuilder.of(TYPE_LOOP)
                .family(FAM_TRAFFIC_LIGHT).color(new Color(0x5b8bd4)).containerBlock().terminatesStack()
                .text("modular.fangsu.tl.loop")
                .container("body")
                .build());

        // 设置灯[下拉框]为[布尔]
        f.register(ModularBlockBuilder.of(TYPE_SET_LIGHT)
                .family(FAM_TRAFFIC_LIGHT).color(new Color(0x3aa86a)).stack()
                .text("modular.fangsu.tl.set_light")
                .choice("light", LIGHT_ITEMS)
                .text("modular.fangsu.tl.to")
                .bool("on", true)
                .build());

        // 等待[double]
        f.register(ModularBlockBuilder.of(TYPE_WAIT)
                .family(FAM_TRAFFIC_LIGHT).color(new Color(0x9966cc)).stack()
                .text("modular.fangsu.tl.wait")
                .num("sec", 1d)
                .build());

        // ---- 数码管显示块（red/green 各对应一组显示位）----

        // 设置显示[ENUM]为[BOOL]：整体亮/灭
        f.register(ModularBlockBuilder.of(TYPE_DISPLAY_ON)
                .family(FAM_TRAFFIC_LIGHT).color(new Color(0x2f6f8f)).stack()
                .text("modular.fangsu.tl.display_on")
                .choice("disp", DISPLAY_ITEMS)
                .text("modular.fangsu.tl.to")
                .bool("on", true)
                .build());

        // 设置显示[ENUM]为[INT]：把整数按位拆分显示到该组显示位（第1位高阶）
        f.register(ModularBlockBuilder.of(TYPE_DISPLAY_VALUE)
                .family(FAM_TRAFFIC_LIGHT).color(new Color(0x2f6f8f)).stack()
                .text("modular.fangsu.tl.display_value")
                .choice("disp", DISPLAY_ITEMS)
                .text("modular.fangsu.tl.to")
                .num("value", 0d)
                .build());

        // 设置显示[ENUM]显示内容为[INT(16进制)]：按共阳型段码送数，字节按 hgfedcba 对应 bit7..bit0
        // （段 a=bit0…h=bit7；共阳 0=亮、1=灭）。如 0xC0=1100 0000 → g/h 灭、a-f 亮 = 显示"0"
        f.register(ModularBlockBuilder.of(TYPE_DISPLAY_HEX)
                .family(FAM_TRAFFIC_LIGHT).color(new Color(0x2f6f8f)).stack()
                .text("modular.fangsu.tl.display_hex")
                .choice("disp", DISPLAY_ITEMS)
                .text("modular.fangsu.tl.display_hex_to")
                .num("value", 0xC0d)
                .build());

        // 显示[ENUM]开始倒计时
        f.register(ModularBlockBuilder.of(TYPE_DISPLAY_CD_START)
                .family(FAM_TRAFFIC_LIGHT).color(new Color(0x2f6f8f)).stack()
                .text("modular.fangsu.tl.display_cd_start")
                .choice("disp", DISPLAY_ITEMS)
                .text("modular.fangsu.tl.display_cd_start_to")
                .build());

        // 显示[ENUM]停止倒计时
        f.register(ModularBlockBuilder.of(TYPE_DISPLAY_CD_STOP)
                .family(FAM_TRAFFIC_LIGHT).color(new Color(0x2f6f8f)).stack()
                .text("modular.fangsu.tl.display_cd_stop")
                .choice("disp", DISPLAY_ITEMS)
                .text("modular.fangsu.tl.display_cd_stop_to")
                .build());
    }
}
