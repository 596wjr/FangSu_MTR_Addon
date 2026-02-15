package com.fangsu.scripting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RouteNameUtil {
    /**
     * 提取中文线路名称
     *
     * @param lineStr 线路字符串
     * @return 提取的线路名称部分
     */
    public static String getCJKLineName(String lineStr) {
        // 正则表达式：^(\d+|[\u4e00-\u9fa5]+)线$
        Pattern pattern = Pattern.compile("^(\\d+|[\u4e00-\u9fa5]+)线$");
        Matcher matcher = pattern.matcher(lineStr);

        if (matcher.find() && matcher.group(1) != null) {
            return matcher.group(1); // 返回提取的部分
        } else {
            if (lineStr.endsWith("号线")) {
                String numberPart = lineStr.replace("号线", "");
                // 尝试解析为整数，如果失败则返回原字符串
                try {
                    return String.valueOf(Integer.parseInt(numberPart));
                } catch (NumberFormatException e) {
                    return numberPart;
                }
            } else {
                return null;
            }
        }
    }

    /**
     * 提取非中文线路名称
     *
     * @param lineStr 线路字符串
     * @return 提取的线路名称部分
     */
    public static String getNonCJKLineName(String lineStr) {
        // 正则表达式：^Line\s+(\d+|[A-Za-z]+)$
        Pattern pattern1 = Pattern.compile("^Line\\s+(\\d+|[A-Za-z]+)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = pattern1.matcher(lineStr);

        if (matcher1.find() && matcher1.group(1) != null) {
            return matcher1.group(1); // 返回提取的部分
        } else {
            // 正则表达式：([A-Za-z\s]+)\sLine
            Pattern pattern2 = Pattern.compile("([A-Za-z\\s]+)\\sLine", Pattern.CASE_INSENSITIVE);
            Matcher matcher2 = pattern2.matcher(lineStr);

            if (matcher2.find() && matcher2.group(1) != null) {
                return matcher2.group(1).trim(); // 返回提取的部分并去除首尾空格
            } else {
                return lineStr;
            }
        }
    }

    /**
     * 判断是否为数字线路
     *
     * @param lineStr 线路字符串
     * @return 如果提取的中文和非中文部分都为数字且相等，返回true，否则返回false
     */
    public static boolean isNumLine(String lineStr) {
        // 简单实现：直接调用上面的方法
        String cjkPart = getCJKLineName(TextUtil.getCjkParts(lineStr));
        String nonCjkPart = getNonCJKLineName(TextUtil.getNonCjkParts(lineStr));

        // 如果两部分都是null，返回false
        if (cjkPart == null || nonCjkPart == null) {
            return false;
        }

        return cjkPart.equals(nonCjkPart);
    }
}
