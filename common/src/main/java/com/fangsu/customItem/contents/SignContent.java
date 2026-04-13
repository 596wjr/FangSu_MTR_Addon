package com.fangsu.customItem.contents;

import com.fangsu.customItem.ModelSelectInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public final class SignContent {
    private SignContent() {
    }

    public static SignDisplayInfo loadDisplayInfo(String mainModel, String subModel) {
        Map<String, Map<String, Object>> loaded = ContentResourceLoader.loadMapByPath(new ResourceLocation(mainModel), "common");
        if (loaded == null || !loaded.containsKey(subModel)) return null;
        return SignDisplayInfo.fromMap(loaded.get(subModel));
    }

    public static List<ModelSelectInfo> loadModelSelectInfos(String mainModel) {
        return ContentResourceLoader.loadModelSelectInfos(new ResourceLocation(mainModel), "common");
    }

    public record SignDisplayInfo(
            String model,
            boolean flipV,
            int unit,
            List<?> tex,
            Map<?, ?> main,
            Map<?, ?> side,
            Map<?, ?> pole
    ) {
        public static SignDisplayInfo fromMap(Map<String, Object> current) {
            if (current == null || !(current.get("model") instanceof String model)) return null;
            boolean flipV = current.get("flipV") instanceof Boolean b && b;
            int unit = current.get("unit") instanceof Number n ? n.intValue() : 8;
            List<?> tex = current.get("tex") instanceof List<?> l ? l : List.of();
            Map<?, ?> main = current.get("main") instanceof Map<?, ?> m ? m : null;
            Map<?, ?> side = current.get("side") instanceof Map<?, ?> m ? m : null;
            Map<?, ?> pole = current.get("pole") instanceof Map<?, ?> m ? m : null;
            return new SignDisplayInfo(model, flipV, unit, tex, main, side, pole);
        }
    }
}
