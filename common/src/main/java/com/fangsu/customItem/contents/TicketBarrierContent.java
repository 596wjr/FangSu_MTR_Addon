package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

public class TicketBarrierContent extends BaseContent {
    private final String model;
    private final boolean filpV;

    private TicketBarrierContent(JsonObject json) {
        super(json);
        model = json.get("model").getAsString();
        filpV = json.has("flipV") && json.get("flipV").getAsBoolean();

    }

    public String getModel() {
        return model;
    }

    public boolean getFilpV() {
        return filpV;
    }

    protected static class TicketBarrierLoader extends BaseLoader {
        @Override
        public void load(String type, String path, JsonObject content) {
            ContentManager cm = ContentManager.getInstance();
            for (Map.Entry<String, JsonElement> entry : content.entrySet()) {
                String entryKey = entry.getKey();
                JsonElement entryValue = entry.getValue();
                if (entryValue == null || !entryValue.isJsonArray()) {
                    Main.LOGGER.warn("Failed to load content {} of {}({}): JSON is null or empty", entryKey, type, path);
                    continue;
                }
                JsonArray entryArray = entryValue.getAsJsonArray();
                for (int i = 0; i < entryArray.size(); i++) {
                    JsonElement detailElement = entryArray.get(i);
                    if (detailElement == null || !detailElement.isJsonObject()) {
                        Main.LOGGER.warn("Failed to load content index {} in {} of {}({}): JSON is null or empty", i, entryKey, type, path);
                        continue;
                    }
                    JsonObject detailObject = detailElement.getAsJsonObject();

                }

            }
        }
    }


}
