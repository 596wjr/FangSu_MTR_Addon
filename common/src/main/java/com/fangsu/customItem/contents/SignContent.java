package com.fangsu.customItem.contents;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SignContent {
    private SignContent() {
    }

    private static final String FACE_LANG_PREFIX = "ui.fangsu.sign.";

    /**
     * 解析面名。允许直接写面名（front/back/faceN），也允许写语言键（如 ui.fangsu.sign.front），
     * 语言键会去掉前缀，存储时按面名（front/back）存储。
     */
    public static String resolveFaceName(Object key) {
        String s = String.valueOf(key);
        return s.startsWith(FACE_LANG_PREFIX) ? s.substring(FACE_LANG_PREFIX.length()) : s;
    }

    /**
     * 解析 tex：新版为 Map（面名 -> [y,z]），旧版为数组（第一个 front，第二个 back）。
     */
    private static Map<String, List<?>> parseTex(Object tex) {
        Map<String, List<?>> map = new LinkedHashMap<>();
        if (tex instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getValue() instanceof List<?> list) {
                    map.put(resolveFaceName(e.getKey()), list);
                }
            }
        } else if (tex instanceof List<?> l) {
            // 旧版数组形式：第一个 front，第二个 back
            if (!l.isEmpty() && l.get(0) instanceof List<?> f) map.put("front", f);
            if (l.size() > 1 && l.get(1) instanceof List<?> b) map.put("back", b);
        }
        return map;
    }

    public record SignDisplayInfo(
            String model,
            boolean flipV,
            int unit,
            Map<String, List<?>> tex, // 面名(front/back) -> [y,z]
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

        /** 取某个面的 tex 点（[y,z]），缺省返回 [0,0]。 */
        public List<?> texPoint(String face) {
            List<?> p = tex.get(face);
            return p != null ? p : List.of(0d, 0d);
        }
    }
}
