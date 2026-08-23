package com.fangsu.customItem.contents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SignOnWallContent {
    private SignOnWallContent() {
    }

    /**
     * 解析面名。键既可以是纯面名，也可以是语言键，统一取最后一个「.」之后的部分。
     */
    private static String resolveFaceName(Object key) {
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
     * 旧版数组 [[y1,z1],[y2,z2]]：表示单个正面（墙牌）的两个角点，自动归一为 front。
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
            }
        }
        return map;
    }

    public record SignOnWallDisplayInfo(
            String model,
            boolean flipV,
            int unit,
            Map<String, List<?>> tex, // 面名 -> 两个角点 [[y1,z1],[y2,z2]]
            Map<?, ?> main,
            Map<?, ?> side
    ) {
        public static SignOnWallDisplayInfo fromMap(Map<String, Object> current) {
            if (current == null || !(current.get("model") instanceof String model)) return null;
            boolean flipV = current.get("flipV") instanceof Boolean b && b;
            int unit = current.get("unit") instanceof Number n ? n.intValue() : 8;
            Map<String, List<?>> tex = parseTex(current.get("tex"));
            Map<?, ?> main = current.get("main") instanceof Map<?, ?> m ? m : null;
            Map<?, ?> side = current.get("side") instanceof Map<?, ?> m ? m : null;
            return new SignOnWallDisplayInfo(model, flipV, unit, tex, main, side);
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
