package com.fangsu.customItem.contents;

import java.util.List;
import java.util.Map;

public final class SignContent {
    private SignContent() {
    }

    public record SignDisplayInfo(
            String model,
            boolean flipV,
            int unit,
            List<?> tex,
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
            List<?> tex = map.get("tex") instanceof List<?> l ? l : List.of();
            Map<?, ?> main = map.get("main") instanceof Map<?, ?> m ? m : null;
            Map<?, ?> side = map.get("side") instanceof Map<?, ?> m ? m : null;
            Map<?, ?> pole = map.get("pole") instanceof Map<?, ?> m ? m : null;
            boolean isMtrTheme = map.get("isMtrTheme") instanceof Boolean b && b;
            double mtrPoleOffset = map.get("mtrPoleOffset") instanceof Number n ? n.doubleValue() : 0;
            int defaultBgColor = map.get("defaultBgColor") instanceof Number n ? n.intValue() : -1;
            return new SignDisplayInfo(model, flipV, unit, tex, main, side, pole, isMtrTheme, mtrPoleOffset, defaultBgColor);
        }
    }
}
