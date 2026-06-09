package com.fangsu.customItem;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomMtrLifts {
    private static CustomMtrLifts instance = new CustomMtrLifts();
    private static final ResourceLocation MTR_CUSTOM_RESOURCES = new ResourceLocation("mtr", "mtr_custom_resources.json");


    private final Map<String, LiftSelectInfo> builtInInfos;
    private final Map<String, LiftSelectInfo> infos;
    private final TexturedLiftSelectInfo defaultInfo;

    public static CustomMtrLifts getInstance() {
        return instance;
    }

    private CustomMtrLifts() {
        builtInInfos = new HashMap<>();
        infos = new HashMap<>();

        JsonObject defaultLift = new JsonObject();
        defaultLift.addProperty("id", "default");
        defaultLift.addProperty("texture", "mtr:textures/entity/lift_1.png");
        defaultLift.addProperty("name", ComponentHelper.translatable("mtr.fangsu.lift.vanilla").toString());
        defaultLift.addProperty("description", ComponentHelper.translatable("mtr.fangsu.lift.vanilla.description").toString());
        defaultInfo = new TexturedLiftSelectInfo(defaultLift);
    }

    public void load() {
        infos.clear();

        JsonElement mtrCustomResources = ResourceUtil.loadAsJSON(MTR_CUSTOM_RESOURCES);
        if (mtrCustomResources == null || !mtrCustomResources.isJsonObject()) {
            return;
        }
        JsonObject customResourcesObject = mtrCustomResources.getAsJsonObject();
        if (customResourcesObject.has("lifts")) {
            JsonArray liftsArray = customResourcesObject.getAsJsonArray("lifts");
            for (int i = 0; i < liftsArray.size(); i++) {
                JsonElement liftElement = liftsArray.get(i);
                if (liftElement == null || !liftElement.isJsonObject()) {
                    continue;
                }
                JsonObject liftObject = liftElement.getAsJsonObject();
                LiftSelectInfo current;
                if (liftObject.has("model")) {
                    current = (new ModeledLiftSelectInfo(liftObject));
                } else {
                    current = (new TexturedLiftSelectInfo(liftObject));
                }
                infos.put(current.getContent(), current);
            }
        }
    }

    public void injectBuiltInTexturedLifts(JsonObject object) {
        TexturedLiftSelectInfo info = new TexturedLiftSelectInfo(object);
        builtInInfos.put(info.getContent(), info);
    }

    public void injectBuiltInModelLifts(JsonObject object) {
        ModeledLiftSelectInfo info = new ModeledLiftSelectInfo(object);
        infos.put(info.getContent(), info);
    }

    public List<LiftSelectInfo> getInfoList() {
        List<LiftSelectInfo> liftInfos = new ArrayList<>();
        liftInfos.addAll(builtInInfos.values());
        liftInfos.addAll(infos.values());
        return liftInfos;
    }

    public LiftSelectInfo getInfo(String key) {
        if (builtInInfos.containsKey(key)) {
            return builtInInfos.get(key);
        }
        if (infos.containsKey(key)) {
            return infos.get(key);
        }
        return defaultInfo;
    }

    public TexturedLiftSelectInfo getTexturedLiftSelectInfo(String key) {
        if (builtInInfos.containsKey(key)) {
            LiftSelectInfo info = builtInInfos.get(key);
            if (info instanceof TexturedLiftSelectInfo t) {
                return t;
            }
        }
        return defaultInfo;
    }

    public static abstract class LiftSelectInfo extends ModelSelectInfo {
        private LiftSelectInfo(@Nullable String text, @Nullable String content, @Nullable String contentText, @Nullable JsonObject defaultItem) {
            super(text, content, contentText, defaultItem);
        }
    }

    public static class ModeledLiftSelectInfo extends LiftSelectInfo {
        private final ResourceLocation texture;
        private final ResourceLocation model;

        private ModeledLiftSelectInfo(JsonObject json) {
            super(json.getAsJsonPrimitive("name").getAsString(), json.getAsJsonPrimitive("id").getAsString(), json.getAsJsonPrimitive("description").getAsString(), null);
            if (!json.has("texture")) {
                texture = ResourceUtil.ERROR_IMAGE;
            } else {
                String texturePath = json.getAsJsonPrimitive("texture").getAsString();
                texture = new ResourceLocation(texturePath);
            }
            model = new ResourceLocation(json.getAsJsonPrimitive("model").getAsString());
        }

        public ResourceLocation getTexture() {
            return texture;
        }

        public ResourceLocation getModel() {
            return model;
        }
    }

    public static class TexturedLiftSelectInfo extends LiftSelectInfo {
        private final ResourceLocation texture;

        private TexturedLiftSelectInfo(JsonObject json) {
            super(json.getAsJsonPrimitive("name").getAsString(), json.getAsJsonPrimitive("id").getAsString(), json.getAsJsonPrimitive("description").getAsString(), null);
            if (!json.has("texture")) {
                texture = ResourceUtil.ERROR_IMAGE;
            } else {
                String texturePath = json.getAsJsonPrimitive("texture").getAsString();
                texture = new ResourceLocation(texturePath);
            }
        }

        public ResourceLocation getTexture() {
            return texture;
        }
    }
}
