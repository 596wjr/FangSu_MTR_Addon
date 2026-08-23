package com.fangsu.customItem.contents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SignContent {
    private SignContent() {
    }

    /**
     * 解析面名。键既可以是纯面名（front/back/front_upper），也可以是语言键
     * （如 ui.fangsu.sign.front 或 ui.fangsu.beijing.sign.front_upper）。
     * 统一取最后一个「.」之后的部分作为存储用的面名。
     */
    public static String resolveFaceName(Object key) {
        String s = String.valueOf(key);
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private static double num(Object o) {
        return ((Number) o).doubleValue();
    }

    /**
     * 解析 tex。
     * 新版：Map（面名 -> 两个角点 [[y1,z1],[y2,z2]]）；
     * 旧版数组 [[y1,z1],[y2,z2]]：表示正/反两面共用这两个角点，自动拆成 front 与 back（back 的 z 取反）。
     */
    private static Map<String, List<?>> parseTex(Object tex) {
        Map<String, List<?>> map = new LinkedHashMap<>();
        if (tex instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getValue() instanceof List<?> corners) {
                    map.put(resolveFaceName(e.getKey()), corners);
                }
            }
        } else if (tex instanceof List<?> l) {
            if (l.size() >= 2 && l.get(0) instanceof List<?> c1 && l.get(1) instanceof List<?> c2) {
                double y1 = num(c1.get(0)), z1 = num(c1.get(1));
                double y2 = num(c2.get(0)), z2 = num(c2.get(1));
                map.put("front", List.of(List.of(y1, z1), List.of(y2, z2)));
                map.put("back", List.of(List.of(y1, -z1), List.of(y2, -z2)));
            }
        }
        return map;
    }

    public record SignDisplayInfo(
            String model,
            boolean flipV,
            int unit,
            Map<String, List<?>> tex, // 面名 -> 两个角点 [[y1,z1],[y2,z2]]
            Map<?, ?> main,
            Map<?, ?> side,
            Map<?, ?> pole,
            boolean isMtrTheme,
            double mtrPoleOffset,
            int defaultBgColor
    ) {
        public static SignDisplayInfo fromMap(Map<String, Object> map) {
            if (map == null || !(map.get("model") instanceof String model)) return null;
            boolean flipV = map.get("flipV") instanceof Boolean b && b;
            int unit = map.get("unit") instanceof Number n ? n.intValue() : 8;
            Map<String, List<?>> tex = parseTex(map.get("tex"));
            Map<?, ?> main = map.get("main") instanceof Map<?, ?> m ? m : null;
            Map<?, ?> side = map.get("side") instanceof Map<?, ?> m ? m : null;
            Map<?, ?> pole = map.get("pole") instanceof Map<?, ?> m ? m : null;
            boolean isMtrTheme = map.get("isMtrTheme") instanceof Boolean b && b;
            double mtrPoleOffset = map.get("mtrPoleOffset") instanceof Number n ? n.doubleValue() : 0;
            int defaultBgColor = map.get("defaultBgColor") instanceof Number n ? n.intValue() : -1;
            return new SignDisplayInfo(model, flipV, unit, tex, main, side, pole, isMtrTheme, mtrPoleOffset, defaultBgColor);
        }

        /** 按顺序返回所有面名。 */
        public List<String> texFaces() {
            return new ArrayList<>(tex.keySet());
        }

        /** 返回某个面的两个角点 [y1,z1,y2,z2]，缺省返回 null。 */
        public double[] texCorners(String face) {
            Object v = tex.get(face);
            if (v instanceof List<?> list && list.size() >= 2
                    && list.get(0) instanceof List<?> c1 && list.get(1) instanceof List<?> c2) {
                return new double[]{num(c1.get(0)), num(c1.get(1)), num(c2.get(0)), num(c2.get(1))};
            }
            return null;
        }
    }
}
