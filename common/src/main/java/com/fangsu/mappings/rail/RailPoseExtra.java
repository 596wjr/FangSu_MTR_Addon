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
 * 单键可避免碰键计数语义），格式带版本前缀以便日后扩展。两个版本（详见 {@link #encode()}）：
 * <pre>
 * v1;ox1,oy1,oz1,ox2,oy2,oz2;halfGauge;pitch1,pitch2;roll1,roll2
 * v2;ox1,oy1,oz1,ox2,oy2,oz2;halfGauge;pitch1,pitch2;roll1,roll2;railTiltStart,railTiltMiddle,railTiltEnd,railTiltMiddleFraction
 * </pre>
 * <b>v1 永远可读</b>，且解出的姿态与引入 v2 之前<b>逐位相同</b>（新增的逐轨道倾斜字段为「未编辑」），
 * 因此老存档里的轨道渲染结果不会发生任何变化。只有真正编辑过逐轨道超高的轨道才会写成 v2。
 * <p>
 * 本类禁止 import 任何 {@code org.mtr.*} / {@code mtr.*} / MC 类型，需在两个工程保持逐字节一致
 * （校验见 {@code devtools/check-synced-files.ps1}）。
 */
public final class RailPoseExtra {

    /** MTR 侧序列化用的键名。 */
    public static final String KEY = "fangsu_rail_extra";

    /** 标准轨距 1435 mm 的一半（米），与 ANTE 的 rollingOffset 默认值一致。 */
    public static final double DEFAULT_HALF_GAUGE = 1.435D / 2.0D;

    /**
     * 逐轨道超高（外轨超高）三个控制点的角度硬上限（度）。
     * <p>
     * <b>服务端校验与编辑层的唯一权威值</b>：内核工厂 {@code RailRollProfile.threePointDegrees}
     * 刻意不再夹取，以免多份限制互相矛盾。写侧（{@code RailTiltPackets}）按本常量夹取。
     */
    public static final double MAX_RAIL_TILT_DEGREES = 45.0D;

    /**
     * 逐轨道超高中点控制点的默认归一化位置：几何退化（直线轨）时的回退值。
     * <p>
     * 曲线轨上服务端会按 {@code count1 / length}（两半径接缝）算出真实位置并存进姿态，
     * 所以本常量只在「轨道退化为单段、没有接缝」时生效，与 MTR 上游
     * {@code middlePoint = count1 == 0 || count2 == 0 ? length / 2 : count1} 一致。
     */
    public static final double DEFAULT_RAIL_TILT_MIDDLE_FRACTION = 0.5D;

    /** 全零姿态（原版行为，几何内核不会被替换）。 */
    public static final RailPoseExtra DEFAULT = new RailPoseExtra(
            0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
            DEFAULT_HALF_GAUGE,
            0.0D, 0.0D,
            0.0D, 0.0D
    );

    private static final String PREFIX = "v1";

    /** v2 前缀：在 v1 尾部追加逐轨道超高四元组（见类注释的格式说明）。 */
    private static final String PREFIX_V2 = "v2";

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

    /**
     * 「逐轨道超高」起点控制点（度，位置 0），或 {@code null} 表示<b>该轨道没有编辑过逐轨道超高</b>。
     * <p>
     * 用 {@code null} 而不是 0 当哨兵，是为了把「未编辑」与「作者显式编辑成 0」区分开：
     * 未编辑的轨道必须原样回退到节点派生的滚转（见 {@code FangSuRailMath} 的剖面选择），
     * 而显式编辑成 0 的轨道要保持「不倾斜」。
     */
    public final Double railTiltStartDegrees;
    /** 逐轨道超高中间控制点（度，位置见 {@link #railTiltMiddleFraction}），{@code null} = 未编辑。 */
    public final Double railTiltMiddleDegrees;
    /** 逐轨道超高终点控制点（度，位置 1），{@code null} = 未编辑。 */
    public final Double railTiltEndDegrees;
    /**
     * 逐轨道超高中间控制点的归一化位置（{@code (0,1)}），只在三个角度已编辑时有意义。
     * <p>
     * 由服务端在应用编辑时按当时几何算好（曲线轨 = 两半径接缝 {@code count1 / length}）
     * 并随姿态一起持久化，因此渲染端拿到的是「编辑当时的接缝位置」，不受后续几何微调影响。
     */
    public final double railTiltMiddleFraction;

    public RailPoseExtra(
            double offsetX1, double offsetY1, double offsetZ1,
            double offsetX2, double offsetY2, double offsetZ2,
            double halfGauge,
            double pitch1Degrees, double pitch2Degrees,
            double roll1Degrees, double roll2Degrees
    ) {
        this(offsetX1, offsetY1, offsetZ1, offsetX2, offsetY2, offsetZ2,
                halfGauge, pitch1Degrees, pitch2Degrees, roll1Degrees, roll2Degrees,
                null, null, null, DEFAULT_RAIL_TILT_MIDDLE_FRACTION);
    }

    /**
     * v2 构造：在 v1 字段之外带「逐轨道超高」三点。三个角度必须<b>同时</b>给或同时不给
     * （{@code null} = 未编辑）；只给一部分按「未编辑」处理，避免出现半个剖面。
     */
    public RailPoseExtra(
            double offsetX1, double offsetY1, double offsetZ1,
            double offsetX2, double offsetY2, double offsetZ2,
            double halfGauge,
            double pitch1Degrees, double pitch2Degrees,
            double roll1Degrees, double roll2Degrees,
            Double railTiltStartDegrees, Double railTiltMiddleDegrees, Double railTiltEndDegrees,
            double railTiltMiddleFraction
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
        final boolean hasAllTilt = railTiltStartDegrees != null && railTiltMiddleDegrees != null && railTiltEndDegrees != null;
        this.railTiltStartDegrees = hasAllTilt ? railTiltStartDegrees : null;
        this.railTiltMiddleDegrees = hasAllTilt ? railTiltMiddleDegrees : null;
        this.railTiltEndDegrees = hasAllTilt ? railTiltEndDegrees : null;
        this.railTiltMiddleFraction = hasAllTilt ? railTiltMiddleFraction : DEFAULT_RAIL_TILT_MIDDLE_FRACTION;
    }

    /**
     * 复制本姿态并写入「逐轨道超高」三点（{@code null} 表示清除，回到节点派生滚转）。
     * <p>
     * <b>这是服务端应用编辑的入口</b>：只覆盖逐轨道倾斜与半轨距，其余字段（两端平移、俯仰、
     * 节点派生滚转）原样保留，因此不会把客户端可能过期的节点几何写回服务端。
     *
     * @param startDegrees  起点控制点（度），{@code null} = 清除逐轨道超高
     * @param middleDegrees 中间控制点（度）
     * @param endDegrees    终点控制点（度）
     * @param middleFraction 中间控制点的归一化位置
     * @param halfGauge      半轨距（米）；非有限值或非正值时沿用当前值
     */
    public RailPoseExtra withRailTilt(Double startDegrees, Double middleDegrees, Double endDegrees, double middleFraction, double halfGauge) {
        if (startDegrees == null || middleDegrees == null || endDegrees == null) {
            return new RailPoseExtra(
                    offsetX1, offsetY1, offsetZ1, offsetX2, offsetY2, offsetZ2,
                    this.halfGauge, pitch1Degrees, pitch2Degrees, roll1Degrees, roll2Degrees,
                    null, null, null, DEFAULT_RAIL_TILT_MIDDLE_FRACTION
            );
        }
        return new RailPoseExtra(
                offsetX1, offsetY1, offsetZ1, offsetX2, offsetY2, offsetZ2,
                Double.isFinite(halfGauge) && halfGauge > 0.0D ? halfGauge : this.halfGauge,
                pitch1Degrees, pitch2Degrees, roll1Degrees, roll2Degrees,
                startDegrees, middleDegrees, endDegrees,
                Double.isFinite(middleFraction) ? middleFraction : DEFAULT_RAIL_TILT_MIDDLE_FRACTION
        );
    }

    /** 是否有「逐轨道超高」三点。为 {@code false} 时渲染端回退到节点派生滚转。 */
    public boolean hasRailTilt() {
        return railTiltStartDegrees != null && railTiltMiddleDegrees != null && railTiltEndDegrees != null;
    }

    /** 逐轨道超高是否处处为 0（作者显式编辑成 0：保持不倾斜，而不是回退到节点滚转）。 */
    public boolean isRailTiltZero() {
        return hasRailTilt()
                && railTiltStartDegrees == 0.0D && railTiltMiddleDegrees == 0.0D && railTiltEndDegrees == 0.0D;
    }

    /**
     * 是否等价于「无附加姿态」：平移、俯仰、节点派生滚转全为 0，且没有编辑过逐轨道超高。
     * <p>
     * 注意 {@code halfGauge} 有非零默认值，不参与判定；「只有半轨距不同」是否需要写盘由
     * {@link #shouldPersist()} 单独负责（见该类注释）。
     */
    public boolean isDefault() {
        return offsetX1 == 0.0D && offsetY1 == 0.0D && offsetZ1 == 0.0D
                && offsetX2 == 0.0D && offsetY2 == 0.0D && offsetZ2 == 0.0D
                && pitch1Degrees == 0.0D && pitch2Degrees == 0.0D
                && roll1Degrees == 0.0D && roll2Degrees == 0.0D
                && !hasRailTilt();
    }

    /**
     * <b>写盘门控</b>：是否必须把本姿态序列化进存档。
     * <p>
     * 比 {@link #isDefault()} 多一条「半轨距不是默认值」。这是必须的：{@code isDefault()} 不看
     * {@code halfGauge}（它是「轨距的一半」这种物理尺寸，默认值非 0），而半轨距会改变滚转抬升的
     * 幅度 —— 只改半轨距、角度全 0 的轨道原本<b>不会写盘</b>，重新载入后半轨距被静默重置成
     * 1435&nbsp;mm 的一半。写盘门控与默认判定必须是同一个判据，否则就会出现这种「改了但存不下来」。
     * <p>
     * 仍然保守：完全没有附加姿态、半轨距也是默认值时返回 {@code false}，
     * 原版轨道的存档字节与不装本模组时完全一致。
     */
    public boolean shouldPersist() {
        return !isDefault() || halfGauge != DEFAULT_HALF_GAUGE;
    }

    /** 是否需要替换几何（平移/俯仰/滚转/逐轨道超高任一非默认）。 */
    public boolean affectsGeometry() {
        return !isDefault();
    }

    /** 是否需要纵坡 Hermite 剖面（任一俯仰角非零）。 */
    public boolean hasPitch() {
        return pitch1Degrees != 0.0D || pitch2Degrees != 0.0D;
    }

    /**
     * 是否需要滚转（实际生效的滚转剖面非零）。
     * <p>
     * 逐轨道超高<b>优先于</b>节点派生滚转（见 {@link #toRollProfile()}），所以判据也要跟着分层：
     * 编辑过逐轨道超高时只看三点，否则才看节点派生的两端角度。否则会出现「渲染层以为有滚转、
     * 实际剖面处处为 0」的空转，或者反过来把真正要画的超高挡掉。
     */
    public boolean hasRoll() {
        if (hasRailTilt()) {
            return !isRailTiltZero();
        }
        return roll1Degrees != 0.0D || roll2Degrees != 0.0D;
    }

    /**
     * 滚转剖面（归一化位置为键）。
     * <p>
     * <b>逐轨道超高优先</b>：编辑过就用三点剖面（中间点落在 {@link #railTiltMiddleFraction}）；
     * 没编辑过则等价于引入本特性之前的行为 —— 节点派生的两点剖面，老轨道渲染结果逐位不变。
     */
    public RailRollProfile toRollProfile() {
        if (hasRailTilt()) {
            return RailRollProfile.threePointDegrees(
                    railTiltStartDegrees, railTiltMiddleDegrees, railTiltEndDegrees, railTiltMiddleFraction
            );
        }
        return RailRollProfile.twoPointDegrees(roll1Degrees, roll2Degrees);
    }

    /**
     * 序列化为单键字符串。
     * <p>
     * <b>格式选择由数据决定</b>：没有编辑过逐轨道超高时写 {@code v1}，字节与引入本特性之前
     * <b>完全相同</b>（因此「读出 v1 → 写回」不会改动任何老存档）；编辑过才写 {@code v2}
     * （在 v1 末尾追加 {@code ;start,middle,end,middleFraction}）。
     */
    public String encode() {
        final StringBuilder builder = new StringBuilder();
        builder.append(hasRailTilt() ? PREFIX_V2 : PREFIX)
                .append(";").append(offsetX1).append(",").append(offsetY1).append(",").append(offsetZ1)
                .append(",").append(offsetX2).append(",").append(offsetY2).append(",").append(offsetZ2)
                .append(";").append(halfGauge)
                .append(";").append(pitch1Degrees).append(",").append(pitch2Degrees)
                .append(";").append(roll1Degrees).append(",").append(roll2Degrees);
        if (hasRailTilt()) {
            builder.append(";").append(railTiltStartDegrees).append(",").append(railTiltMiddleDegrees)
                    .append(",").append(railTiltEndDegrees).append(",").append(railTiltMiddleFraction);
        }
        return builder.toString();
    }

    /**
     * 反序列化。任何格式问题（null/空/段数不符/数值不可解析/版本前缀不识别）都返回
     * {@link #DEFAULT}，保证旧存档与损坏数据不会让轨道加载失败。
     * <p>
     * <b>v1 与 v2 都能读</b>：v1 只读前 5 段（尾部多出来的段被忽略，因此未来再把 v2 往后扩展
     * 也不会让本版本读崩），解出的姿态逐位等同于引入 v2 之前；v2 额外读第 6 段四元组，
     * 该段列数不符时同样退回 {@link #DEFAULT}（而不是「静默丢掉倾斜」——那会让下一次写盘
     * 把已经有超高的轨道悄悄抹平）。
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
            // ";" 这种输入 split 出来是空数组（Java 的 split 会丢掉全部尾部空串），
            // 必须先挡住，否则 sections[0] 直接抛 ArrayIndexOutOfBoundsException
            // （旧实现只挡了 null/空串/段数 < 5，";" 一直是个真实崩溃点）。
            if (sections.length < 1) {
                return DEFAULT;
            }
            final String version = sections[0].trim();
            final boolean version2 = PREFIX_V2.equals(version);
            // v2 必须带第 6 段；段数不足时按损坏数据处理
            if ((!PREFIX.equals(version) && !version2) || sections.length - 1 < (version2 ? 5 : 4)) {
                return DEFAULT;
            }
            final double[] offsets = parseDoubles(sections[1], 6, "offsets");
            final double[] pitch = parseDoubles(sections[3], 2, "pitch");
            final double[] roll = parseDoubles(sections[4], 2, "roll");
            final double halfGauge = Double.parseDouble(sections[2].trim());
            if (!version2) {
                return new RailPoseExtra(
                        offsets[0], offsets[1], offsets[2], offsets[3], offsets[4], offsets[5],
                        halfGauge,
                        pitch[0], pitch[1],
                        roll[0], roll[1]
                );
            }
            final double[] tilt = parseDoubles(sections[5], 4, "railTilt");
            // 非有限值（NaN / ±Infinity）视为损坏数据整体降级：本类的容错策略是「格式有问题就回 DEFAULT」，
            // 而不是把垃圾值留在姿态里（中间位置越界本身无害，构造剖面的工厂会夹取）。
            for (final double value : tilt) {
                if (!Double.isFinite(value)) {
                    return DEFAULT;
                }
            }
            return new RailPoseExtra(
                    offsets[0], offsets[1], offsets[2], offsets[3], offsets[4], offsets[5],
                    halfGauge,
                    pitch[0], pitch[1],
                    roll[0], roll[1],
                    tilt[0], tilt[1], tilt[2], tilt[3]
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
