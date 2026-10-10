package com.fangsu.config;

import com.fangsu.Main;
import dev.architectury.platform.Platform;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 游戏目录 config/fangsu.properties 全局配置。
 * <p>
 * 当前包含的项：
 * <ul>
 *     <li>{@code highQualityShape}：旋转碰撞箱是否按步长细分（默认 true）。
 *     关闭后改为整块旋转的粗粒度结果，可显著降低计算/内存开销；
 *     方块朝向（90° 整数倍）无论开关都照常生效且不会细分。</li>
 *     <li>{@code shapeStep}：细分时的切分步长（默认 0.1，单位：方块）。
 *     步长越小碰撞箱越贴合模型，但子盒数量与计算量随之增大。</li>
 *     <li>{@code cameraTiltEnabled}：乘车经过外轨超高（翻滚角）的曲线时，镜头 / 地平线是否随车体滚转
 *     （P5-7，默认 true）。关闭后世界观感与原生逐位一致。</li>
 *     <li>{@code cameraTiltStrength}：上述镜头滚转的强度倍率（P5-7，默认 1.0，允许 0~2）。
 *     1.0 = 与车体同角度；2.0 = 两倍（刻意过量，用于确认特性真的生效）；0.0 等价于关闭。</li>
 * </ul>
 * 文件缺失或缺少键时会在 config 目录下自动补全。
 * <p>
 * <b>键名与 MTR4 版逐字相同</b>（{@code fangsu_for_mtr_4/.../config/FangSuConfig.java}）：
 * 两个项目共用同一套 {@code fangsu.properties} 键，因此绝不能改名。
 */
public final class FangSuConfig {

    public static final String FILE_NAME = "fangsu.properties";

    public static final String KEY_HIGH_QUALITY_SHAPE = "highQualityShape";
    public static final String KEY_SHAPE_STEP = "shapeStep";
    public static final String KEY_CAMERA_TILT_ENABLED = "cameraTiltEnabled";
    public static final String KEY_CAMERA_TILT_STRENGTH = "cameraTiltStrength";

    public static final boolean DEFAULT_HIGH_QUALITY_SHAPE = true;
    public static final double DEFAULT_SHAPE_STEP = 0.1d;
    public static final boolean DEFAULT_CAMERA_TILT_ENABLED = true;
    public static final double DEFAULT_CAMERA_TILT_STRENGTH = 1.0d;

    /** 步长允许范围：过小会触发 RotatableShapeHelper 的子盒上限保护 */
    private static final double MIN_SHAPE_STEP = 0.01d;
    private static final double MAX_SHAPE_STEP = 1.0d;

    /** 镜头滚转强度允许范围：0 = 关闭效果，2 = 两倍过量（与 MTR4 相同的钳制区间）。 */
    private static final double MIN_CAMERA_TILT_STRENGTH = 0.0d;
    private static final double MAX_CAMERA_TILT_STRENGTH = 2.0d;

    private static volatile boolean initialized = false;
    private static volatile boolean highQualityShape = DEFAULT_HIGH_QUALITY_SHAPE;
    private static volatile double shapeStep = DEFAULT_SHAPE_STEP;
    private static volatile boolean cameraTiltEnabled = DEFAULT_CAMERA_TILT_ENABLED;
    private static volatile double cameraTiltStrength = DEFAULT_CAMERA_TILT_STRENGTH;

    private FangSuConfig() {
    }

    /**
     * 读取配置文件；文件不存在或缺少键时会写出补全后的配置。
     * 多次调用只有第一次生效。
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        Properties properties = new Properties();
        Path path = configPath();
        if (path != null && Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (Exception e) {
                Main.LOGGER.warn("Failed to read config {}: {}", FILE_NAME, e.toString());
            }
        }

        boolean needSave = false;

        String quality = properties.getProperty(KEY_HIGH_QUALITY_SHAPE);
        if (quality == null || quality.trim().isEmpty()) {
            needSave = true;
        } else {
            highQualityShape = Boolean.parseBoolean(quality.trim());
        }

        String step = properties.getProperty(KEY_SHAPE_STEP);
        if (step == null || step.trim().isEmpty()) {
            needSave = true;
        } else {
            try {
                double parsed = Double.parseDouble(step.trim());
                double clamped = clampStep(parsed);
                if (clamped != parsed) {
                    Main.LOGGER.warn("{} out of range [{}~{}], using {}", KEY_SHAPE_STEP, MIN_SHAPE_STEP, MAX_SHAPE_STEP, clamped);
                    needSave = true;
                }
                shapeStep = clamped;
            } catch (NumberFormatException e) {
                Main.LOGGER.warn("Invalid {} value \"{}\", using {}", KEY_SHAPE_STEP, step, DEFAULT_SHAPE_STEP);
                needSave = true;
            }
        }

        String tiltEnabled = properties.getProperty(KEY_CAMERA_TILT_ENABLED);
        if (tiltEnabled == null || tiltEnabled.trim().isEmpty()) {
            needSave = true;
        } else {
            cameraTiltEnabled = Boolean.parseBoolean(tiltEnabled.trim());
        }

        String tiltStrength = properties.getProperty(KEY_CAMERA_TILT_STRENGTH);
        if (tiltStrength == null || tiltStrength.trim().isEmpty()) {
            needSave = true;
        } else {
            try {
                double parsed = Double.parseDouble(tiltStrength.trim());
                double clamped = clampCameraTiltStrength(parsed);
                if (clamped != parsed) {
                    Main.LOGGER.warn("{} out of range [{}~{}], using {}", KEY_CAMERA_TILT_STRENGTH,
                            MIN_CAMERA_TILT_STRENGTH, MAX_CAMERA_TILT_STRENGTH, clamped);
                    needSave = true;
                }
                cameraTiltStrength = clamped;
            } catch (NumberFormatException e) {
                Main.LOGGER.warn("Invalid {} value \"{}\", using {}", KEY_CAMERA_TILT_STRENGTH, tiltStrength,
                        DEFAULT_CAMERA_TILT_STRENGTH);
                needSave = true;
            }
        }

        if (needSave) save(properties);

        Main.LOGGER.info("FangSu config: {}={}, {}={}, {}={}, {}={}",
                KEY_HIGH_QUALITY_SHAPE, highQualityShape, KEY_SHAPE_STEP, shapeStep,
                KEY_CAMERA_TILT_ENABLED, cameraTiltEnabled, KEY_CAMERA_TILT_STRENGTH, cameraTiltStrength);
    }

    /** 旋转碰撞箱是否细分；false 时整块旋转（朝向仍然生效，只是不做细分）。 */
    public static boolean highQualityShape() {
        init();
        return highQualityShape;
    }

    /** 细分时的切分步长（方块单位，已保证落在合法范围内）。 */
    public static double shapeStep() {
        init();
        return shapeStep;
    }

    /**
     * 乘车经过外轨超高段时，镜头 / 地平线是否随车体滚转（P5-7，默认 {@code true}）。
     * <p>
     * 只在「玩家确实坐在一辆带滚转的车厢里」时才有意义：不满足时相机与原生逐位一致。
     */
    public static boolean cameraTiltEnabled() {
        init();
        return cameraTiltEnabled;
    }

    /**
     * 镜头滚转的强度倍率（P5-7，默认 {@code 1.0}，已钳制到 {@code [0, 2]}）。
     * <p>
     * {@code 1.0} = 与车体同一个角度（物理上正确的「乘客脑袋焊在车厢上」）；
     * {@code 2.0} = 两倍，用来在游戏里一眼确认特性确实生效；{@code 0.0} 等价于关闭。
     */
    public static double cameraTiltStrength() {
        init();
        return cameraTiltStrength;
    }

    private static Path configPath() {
        try {
            Path folder = Platform.getConfigFolder();
            if (folder == null) return null;
            return folder.resolve(FILE_NAME);
        } catch (Throwable t) {
            Main.LOGGER.warn("Failed to locate config folder: {}", t.toString());
            return null;
        }
    }

    private static void save(Properties properties) {
        Path path = configPath();
        if (path == null) return;

        properties.setProperty(KEY_HIGH_QUALITY_SHAPE, Boolean.toString(highQualityShape));
        properties.setProperty(KEY_SHAPE_STEP, Double.toString(shapeStep));
        properties.setProperty(KEY_CAMERA_TILT_ENABLED, Boolean.toString(cameraTiltEnabled));
        properties.setProperty(KEY_CAMERA_TILT_STRENGTH, Double.toString(cameraTiltStrength));

        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                properties.store(writer, "FangSu config. highQualityShape: rotate collision shapes (true/false); "
                        + "shapeStep: sub-box step used when rotating (" + MIN_SHAPE_STEP + "~" + MAX_SHAPE_STEP + "); "
                        + "cameraTiltEnabled: roll camera/horizon while riding a banked train (true/false); "
                        + "cameraTiltStrength: camera roll multiplier ("
                        + MIN_CAMERA_TILT_STRENGTH + "~" + MAX_CAMERA_TILT_STRENGTH + ")");
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to write config {}: {}", FILE_NAME, e.toString());
        }
    }

    private static double clampStep(double value) {
        if (Double.isNaN(value)) return DEFAULT_SHAPE_STEP;
        if (value < MIN_SHAPE_STEP) return MIN_SHAPE_STEP;
        if (value > MAX_SHAPE_STEP) return MAX_SHAPE_STEP;
        return value;
    }

    private static double clampCameraTiltStrength(double value) {
        if (Double.isNaN(value)) return DEFAULT_CAMERA_TILT_STRENGTH;
        if (value < MIN_CAMERA_TILT_STRENGTH) return MIN_CAMERA_TILT_STRENGTH;
        if (value > MAX_CAMERA_TILT_STRENGTH) return MAX_CAMERA_TILT_STRENGTH;
        return value;
    }
}
