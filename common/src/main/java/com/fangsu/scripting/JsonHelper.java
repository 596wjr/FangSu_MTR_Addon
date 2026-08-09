package com.fangsu.scripting;

import com.google.gson.*;

import java.util.function.Function;

public class JsonHelper {
    public static <T> T getOrDefault(JsonObject json, String key, T def, Function<JsonElement, T> parser) {
        if (json.has(key)) {
            return parser.apply(json.get(key));
        }
        return def;
    }

    public static int getOrDefaultInteger(JsonObject json, String key, int def) {
        if (json.has(key)) {
            return json.get(key).getAsInt();
        }
        return def;
    }

    public static float getOrDefaultFloat(JsonObject json, String key, float def) {
        if (json.has(key)) {
            return json.get(key).getAsFloat();
        }
        return def;
    }

    public static double getOrDefaultDouble(JsonObject json, String key, double def) {
        if (json.has(key)) {
            return json.get(key).getAsDouble();
        }
        return def;
    }

    public static boolean getOrDefaultBoolean(JsonObject json, String key, boolean def) {
        if (json.has(key)) {
            return json.get(key).getAsBoolean();
        }
        return def;
    }

    public static String getOrDefaultString(JsonObject json, String key, String def) {
        if (json.has(key)) {
            return json.get(key).getAsString();
        }
        return def;
    }

}
