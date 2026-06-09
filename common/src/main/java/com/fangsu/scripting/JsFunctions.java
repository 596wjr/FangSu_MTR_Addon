package com.fangsu.scripting;

import com.fangsu.Main;
import com.fangsu.mtr.DrawableRoute;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.utils.ColorUtil;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsFunctions {
    public static Object loadResource(String type, String path) throws Exception {
        if ("systemFont".equals(type)) {
            return (new Font(path, Font.PLAIN, 12));

        }
        if ("gif".equals(type)) {
            ResourceLocation rl = new ResourceLocation(path);
            String id = rl.toString();
            GifHelper gifHelper = GifHelper.getInstance();
            if (!gifHelper.hasGif(id)) {
                gifHelper.bindGif(id, rl);
            }
            return gifHelper.getCurrentFrame(id);
        }
        ResourceLocation rl = new ResourceLocation(path);
        return switch (type) {
            case "string", "str" -> ResourceUtil.loadString(rl);
            case "image", "img" -> ResourceUtil.loadImage(rl);
            case "font" -> (ResourceUtil.loadFont(rl));
            case "model" -> ResourceUtil.loadModel(rl, false);
            case "partedModel" -> ResourceUtil.loadPartedModel(rl, true);
            case "json", "JSON" -> ResourceUtil.loadAsJSON(rl);
            default -> "";
        };
    }

    public static String addPrefix(String src, String prefix, boolean addSpace) {
        return TextUtil.addPrefix(src, TextUtil.getCjkParts(prefix), TextUtil.getNonCjkParts(prefix), addSpace);
    }

    public static String addSuffix(String src, String suffix) {
        return src;
        //TODO
    }

    public static void setDebugInfo(String msg) {
        if (Main.debug)
            Main.LOGGER.info("JavaScript Debug Info: {}", msg);
    }

    public static void setWarnInfo(String msg) {
        Main.LOGGER.warn("JavaScript Warn Info: {}", msg);
    }

    public static void setErrorInfo(String msg) {
        Main.LOGGER.error("JavaScript Error Info: {}", msg);
    }

    public static ProxyObject getCurrentDate() {
        Calendar cal = Calendar.getInstance();
        Map<String, Object> map = new HashMap<>();
        map.put("y", cal.get(Calendar.YEAR));
        map.put("m", cal.get(Calendar.MONTH) + 1);
        map.put("d", cal.get(Calendar.DAY_OF_MONTH));
        map.put("h", cal.get(Calendar.HOUR_OF_DAY));
        map.put("min", cal.get(Calendar.MINUTE));
        map.put("s", cal.get(Calendar.SECOND));
        return ProxyObject.fromMap(map);
    }

    public static int getCurrentWeekday() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.DAY_OF_WEEK) - 1;
    }

    public static String formatDate(boolean isCjk) {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        if (isCjk) {
            // 返回 "YYYY年MM月DD日" 格式
            return year + "年" + padZero(month) + "月" + padZero(day) + "日";
        } else {
            // 返回 "MM, DDth YYYY" 格式
            return padZero(month) + ", " + getOrdinalSuffix(day) + " " + year;
        }
    }

    public static String formatWeekday(boolean isCjk) {
        var date = new Date();
        var day = date.getDay();
        var weekdaysCN = new String[]{"日", "一", "二", "三", "四", "五", "六"};
        var weekdaysEN = new String[]{"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

        return isCjk ? "星期" + weekdaysCN[day] : weekdaysEN[day];
    }

    public static String padZero(int num) {
        return num < 10 ? "0" + num : num + "";
    }

    public static String getOrdinalSuffix(int day) {
        if (day > 3 && day < 21) return day + "th";
        return switch (day % 10) {
            case 1 -> day + "st";
            case 2 -> day + "nd";
            case 3 -> day + "rd";
            default -> day + "th";
        };
    }

    public static Color rgbToColor(int r, int g, int b) {
        return new Color(r, g, b);
    }

    public static Color rgbaToColor(int r, int g, int b, int a) {
        return new Color(r, g, b, a);
    }

    public static Color intToColor(int c) {
        return Color.decode(String.valueOf(c));
    }

    public static boolean isLightColor(Color c) {
        return ColorUtil.isLightColor(c);
    }

    public static int jsDrawStrDl(Graphics2D g, Font cjkFont, Font nonCjkFont, String str, double x, double y, double h, int bd, int d) {
        String drawStr = str == null ? "" : str;
        int width = G2dTextHelper.getMultiLinesWidth(g, cjkFont, nonCjkFont, (float) h, drawStr.split("\\|"));
        return G2dTextHelper.drawStrMultiLines(g, cjkFont, nonCjkFont, (int) x - (bd == 1 ? width / 2 : bd == 2 ? width : 0), (int) y - (int) h, (int) h, d, drawStr.split("\\|"));
    }

    public static int jsGetDLStringWidth(Graphics2D g, Font cjkFont, Font nonCjkFont, String str, double h) {
        String drawStr = str == null ? "" : str;
        return G2dTextHelper.getMultiLinesWidth(g, cjkFont, nonCjkFont, (float) h, drawStr.split("\\|"));
    }

    public static int jsDrawStrUnified(Graphics2D g, Font font, String str, double x, double y, double h, int align) {
        return G2dTextHelper.drawStrUnified(g, font, str, x, y, h, align);
    }

    public static int jsGetUnifiedStringWidth(Graphics2D g, Font font, String str, double h) {
        return G2dTextHelper.getUnifiedStringWidth(g, font, str, (float) h);
    }

    private static final Pattern CJK_PATTERN = Pattern.compile("^(\\d+|[\u4e00-\u9fa5]+)线$");
    private static final Pattern LINE_PATTERN = Pattern.compile("^Line\\s+(\\d+|[A-Za-z]+)$");
    private static final Pattern LINE_SUFFIX_PATTERN = Pattern.compile("([A-Za-z\\s]+)\\sLine");

    public static String parseLineName(String lineStr) {
        if (lineStr == null || lineStr.isEmpty()) {
            return "|"; // 或者返回 null，根据业务需求
        }

        String cjkName = getCJKLineName(TextUtil.getCjkParts(lineStr));
        String nonCjkName = getNonCJKLineName(TextUtil.getNonCjkParts(lineStr));

        return (cjkName != null ? cjkName : "") + "|" + (nonCjkName != null ? nonCjkName : "");
    }

    public static String getCJKLineName(String lineStr) {
        if (lineStr == null || lineStr.isEmpty()) {
            return null;
        }

        Matcher matcher = CJK_PATTERN.matcher(lineStr);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        // 处理"号线"后缀
        if (lineStr.endsWith("号线")) {
            String numberPart = lineStr.substring(0, lineStr.length() - 2);
            if (numberPart.matches("\\d+")) {
                return numberPart;
            }
        }

        return null;
    }

    public static String getNonCJKLineName(String lineStr) {
        if (lineStr == null || lineStr.isEmpty()) {
            return null;
        }

        // 匹配 "Line X" 格式
        Matcher matcher = LINE_PATTERN.matcher(lineStr);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        // 匹配 "XXX Line" 格式
        matcher = LINE_SUFFIX_PATTERN.matcher(lineStr);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }

        return lineStr;
    }

    public static boolean isNumLine(String lineStr) {
        if (lineStr == null || lineStr.isEmpty()) {
            return false;
        }

        String cjkPart = getCJKLineName(TextUtil.getCjkParts(lineStr));
        String nonCjkPart = getNonCJKLineName(TextUtil.getNonCjkParts(lineStr));

        if (cjkPart == null || nonCjkPart == null) {
            return false;
        }

        return cjkPart.equals(nonCjkPart);
    }

    public static BufferedImage changeImageColor(Image originalImage, Color newColor) {
        // 参数校验
        if (originalImage == null || newColor == null) {
            throw new IllegalArgumentException("原始图像和新颜色不能为 null");
        }

        // 获取图像尺寸
        int width = originalImage.getWidth(null);
        int height = originalImage.getHeight(null);

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("图像尺寸无效: " + width + "x" + height);
        }

        // 创建 BufferedImage（使用更高效的图像类型）
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // 绘制原始图像
        Graphics2D g2d = bufferedImage.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(originalImage, 0, 0, null);
        } finally {
            g2d.dispose();
        }

        // 提前获取颜色分量（使用位运算优化）
        int newRed = newColor.getRed();
        int newGreen = newColor.getGreen();
        int newBlue = newColor.getBlue();
        int newRGBWithoutAlpha = (newRed << 16) | (newGreen << 8) | newBlue;

        return changeColorByPixel(bufferedImage, newRGBWithoutAlpha);
    }

    private static BufferedImage changeColorByPixel(BufferedImage image, int newRGBWithoutAlpha) {
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;

                // 跳过完全透明的像素
                if (alpha == 0) {
                    continue;
                }

                // 组合新颜色（保持原始 alpha）
                int newRGB = (alpha << 24) | newRGBWithoutAlpha;
                image.setRGB(x, y, newRGB);
            }
        }

        return image;
    }

    public static DrawableRoute jsRouteToObj(LocalRoute localRoute) {
        return DrawableRoute.getDrawableRoute(localRoute);
    }
}
