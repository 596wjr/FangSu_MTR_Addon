package com.fangsu.utils.nte;

import java.util.Map;
import java.util.HashMap;

public class DoubleFloatMapSerializer {

    public static Map<Double, Float> deserialize(String str) {
        if (str == null || str.isEmpty()) {
            return new HashMap<>();
        }
        String[] pairs = str.split(",");
        Map<Double, Float> map = new HashMap<>();
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    double key = Double.parseDouble(kv[0]);
                    float value = Float.parseFloat(kv[1]);
                    map.put(key, value);
                } catch (NumberFormatException e) {
                    // ignore invalid pairs
                }
            }
        }
        return map;
    }

    public static String serializeToString(Map<Double, Float> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Double, Float> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append(",");
        }
        return sb.substring(0, sb.length() - 1);
    }
}