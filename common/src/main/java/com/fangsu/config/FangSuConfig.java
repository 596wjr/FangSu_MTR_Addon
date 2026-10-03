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
 * </ul>
 * 文件缺失或缺少键时会在 config 目录下自动补全。
 */
public final class FangSuConfig {

    public static final String FILE_NAME = "fangsu.properties";

    public static final String KEY_HIGH_QUALITY_SHAPE = "highQualityShape";
    public static final String KEY_SHAPE_STEP = "shapeStep";

    public static final boolean DEFAULT_HIGH_QUALITY_SHAPE = true;
    public static final double DEFAULT_SHAPE_STEP = 0.1d;

    /** 步长允许范围：过小会触发 RotatableShapeHelper 的子盒上限保护 */
    private static final double MIN_SHAPE_STEP = 0.01d;
    private static final double MAX_SHAPE_STEP = 1.0d;

    private static volatile boolean initialized = false;
    private static volatile boolean highQualityShape = DEFAULT_HIGH_QUALITY_SHAPE;
    private static volatile double shapeStep = DEFAULT_SHAPE_STEP;

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

        if (needSave) save(properties);

        Main.LOGGER.info("FangSu config: {}={}, {}={}", KEY_HIGH_QUALITY_SHAPE, highQualityShape, KEY_SHAPE_STEP, shapeStep);
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

        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                properties.store(writer, "FangSu config. highQualityShape: rotate collision shapes (true/false); "
                        + "shapeStep: sub-box step used when rotating (" + MIN_SHAPE_STEP + "~" + MAX_SHAPE_STEP + ")");
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
}
