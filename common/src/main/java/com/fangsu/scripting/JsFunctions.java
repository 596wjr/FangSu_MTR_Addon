package com.fangsu.scripting;

import com.fangsu.Main;
import com.fangsu.utils.ColorUtil;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.awt.*;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JsFunctions {
    public static Object loadResource(String type, String path) throws Exception {
        ResourceLocation rl = new ResourceLocation(path);
        return switch (type) {
            case "string", "str" -> ResourceUtil.loadString(rl);
            case "image", "img" -> ResourceUtil.loadImage(rl);
            case "font" -> ResourceUtil.loadFont(rl);
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
        Date date = new Date();
        int year = date.getYear();
        int month = date.getMonth() + 1;
        int day = date.getDate();

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
}
