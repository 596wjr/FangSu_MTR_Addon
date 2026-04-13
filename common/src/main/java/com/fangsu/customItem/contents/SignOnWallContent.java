package com.fangsu.customItem.contents;

import java.util.List;
import java.util.Map;

public final class SignOnWallContent {
    private SignOnWallContent() {
    }

    public record SignOnWallDisplayInfo(
            String model,
            boolean flipV,
            int unit,
            List<?> tex,
            Map<?, ?> main,
            Map<?, ?> side
    ) {
        public static SignOnWallDisplayInfo fromMap(Map<String, Object> current) {
            if (current == null || !(current.get("model") instanceof String model)) return null;
            boolean flipV = current.get("flipV") instanceof Boolean b && b;
            int unit = current.get("unit") instanceof Number n ? n.intValue() : 8;
            List<?> tex = current.get("tex") instanceof List<?> l ? l : List.of();
            Map<?, ?> main = current.get("main") instanceof Map<?, ?> m ? m : null;
            Map<?, ?> side = current.get("side") instanceof Map<?, ?> m ? m : null;
            return new SignOnWallDisplayInfo(model, flipV, unit, tex, main, side);
        }
    }
}
