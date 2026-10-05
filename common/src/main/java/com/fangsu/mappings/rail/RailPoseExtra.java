package com.fangsu.mappings.rail;

/**
 * 版本无关的「万向节点轨道附加姿态数据」。
 * <p>
 * 承载一个轨道两端（{@code position1} / {@code position2} 语义，与 MTR 的 {@code reversePositions}
 * 无关）的：
 * <ul>
 *   <li>锚点三维平移（格，双精度 —— 这是 MTR 的整数 {@code Position} 无法表达的部分）</li>
 *   <li>纵坡俯仰角（度，节点处轨道切线的斜率角）</li>
 *   <li>翻滚角（度，正值表示沿前进方向右手侧抬升）</li>
 * </ul>
 * 外加一个「半轨距」（米，默认 1435&nbsp;mm / 2 = 0.7175）：外轨超高按 ANTE 语义抬升中心线
 * {@code 半轨距 · |sin(滚转角)|}，使内轨保持标高。
 * <p>
 * 序列化为<b>单个字符串键</b>（MTR 的 {@code ReaderBase}/{@code WriterBase} 是字符串键读写，
 * 单键可避免碰键计数语义），格式带版本前缀以便日后扩展（如 ANTE 式多点 {@code distance:value} 剖面）：
 * <pre>v1;ox1,oy1,oz1,ox2,oy2,oz2;halfGauge;pitch1,pitch2;roll1,roll2</pre>
 * <p>
 * 本类禁止 import 任何 {@code org.mtr.*} / {@code mtr.*} / MC 类型，需在两个工程保持逐字节一致
 * （校验见 {@code devtools/check-synced-files.ps1}）。
 */
public final class RailPoseExtra {

    /** MTR 侧序列化用的键名。 */
    public static final String KEY = "fangsu_rail_extra";

    /** 标准轨距 1435 mm 的一半（米），与 ANTE 的 rollingOffset 默认值一致。 */
    public static final double DEFAULT_HALF_GAUGE = 1.435D / 2.0D;

    /** 全零姿态（原版行为，几何内核不会被替换）。 */
    public static final RailPoseExtra DEFAULT = new RailPoseExtra(
            0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
            DEFAULT_HALF_GAUGE,
            0.0D, 0.0D,
            0.0D, 0.0D
    );

    private static final String PREFIX = "v1";

    public final double offsetX1;
    public final double offsetY1;
    public final double offsetZ1;
    public final double offsetX2;
    public final double offsetY2;
    public final double offsetZ2;
    public final double halfGauge;
    public final double pitch1Degrees;
    public final double pitch2Degrees;
    public final double roll1Degrees;
    public final double roll2Degrees;

    public RailPoseExtra(
            double offsetX1, double offsetY1, double offsetZ1,
            double offsetX2, double offsetY2, double offsetZ2,
            double halfGauge,
            double pitch1Degrees, double pitch2Degrees,
            double roll1Degrees, double roll2Degrees
    ) {
        this.offsetX1 = offsetX1;
        this.offsetY1 = offsetY1;
        this.offsetZ1 = offsetZ1;
        this.offsetX2 = offsetX2;
        this.offsetY2 = offsetY2;
        this.offsetZ2 = offsetZ2;
        this.halfGauge = halfGauge > 0.0D ? halfGauge : DEFAULT_HALF_GAUGE;
        this.pitch1Degrees = pitch1Degrees;
        this.pitch2Degrees = pitch2Degrees;
        this.roll1Degrees = roll1Degrees;
        this.roll2Degrees = roll2Degrees;
    }

    /**
     * 是否等价于「无附加姿态」：平移、俯仰、滚转全为 0。
     * <p>
     * 注意 {@code halfGauge} 有非零默认值，不参与判定。
     */
    public boolean isDefault() {
        return offsetX1 == 0.0D && offsetY1 == 0.0D && offsetZ1 == 0.0D
                && offsetX2 == 0.0D && offsetY2 == 0.0D && offsetZ2 == 0.0D
                && pitch1Degrees == 0.0D && pitch2Degrees == 0.0D
                && roll1Degrees == 0.0D && roll2Degrees == 0.0D;
    }

    /** 是否需要替换几何（平移/俯仰/滚转任一非零）。 */
    public boolean affectsGeometry() {
        return !isDefault();
    }

    /** 是否需要纵坡 Hermite 剖面（任一俯仰角非零）。 */
    public boolean hasPitch() {
        return pitch1Degrees != 0.0D || pitch2Degrees != 0.0D;
    }

    /** 是否需要滚转（任一翻滚角非零）。 */
    public boolean hasRoll() {
        return roll1Degrees != 0.0D || roll2Degrees != 0.0D;
    }

    /** 滚转剖面（归一化位置为键）。 */
    public RailRollProfile toRollProfile() {
        return RailRollProfile.twoPointDegrees(roll1Degrees, roll2Degrees);
    }

    /** 序列化为单键字符串。 */
    public String encode() {
        return PREFIX
                + ";" + offsetX1 + "," + offsetY1 + "," + offsetZ1 + "," + offsetX2 + "," + offsetY2 + "," + offsetZ2
                + ";" + halfGauge
                + ";" + pitch1Degrees + "," + pitch2Degrees
                + ";" + roll1Degrees + "," + roll2Degrees;
    }

    /**
     * 反序列化。任何格式问题（null/空/段数不符/数值不可解析/版本前缀不识别）都返回
     * {@link #DEFAULT}，保证旧存档与损坏数据不会让轨道加载失败。
     */
    public static RailPoseExtra decode(String raw) {
        if (raw == null) {
            return DEFAULT;
        }
        final String text = raw.trim();
        if (text.isEmpty()) {
            return DEFAULT;
        }
        try {
            final String[] sections = text.split(";");
            if (sections.length < 5) {
                return DEFAULT;
            }
            final String version = sections[0].trim();
            if (!PREFIX.equals(version)) {
                return DEFAULT;
            }
            final double[] offsets = parseDoubles(sections[1], 6, "offsets");
            final double[] pitch = parseDoubles(sections[3], 2, "pitch");
            final double[] roll = parseDoubles(sections[4], 2, "roll");
            final double halfGauge = Double.parseDouble(sections[2].trim());
            return new RailPoseExtra(
                    offsets[0], offsets[1], offsets[2], offsets[3], offsets[4], offsets[5],
                    halfGauge,
                    pitch[0], pitch[1],
                    roll[0], roll[1]
            );
        } catch (NumberFormatException e) {
            return DEFAULT;
        }
    }

    private static double[] parseDoubles(String section, int expected, String what) {
        final String[] parts = section.trim().split(",");
        if (parts.length != expected) {
            throw new NumberFormatException(what + ": expected " + expected + " values, got " + parts.length);
        }
        final double[] result = new double[expected];
        for (int i = 0; i < expected; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }
}
