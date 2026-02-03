package com.fangsu.extraConfig;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class JsonConfigParser {

//    public static ConfigEntry<?> parse(
//            JsonObject json,
//            Consumer<Object> onChanged
//    ) {
//        String type = json.get("type").getAsString();
//
//        return switch (type) {
//            case "number" -> NumberConfig.fromJson(json, onChanged);
//            case "bool" -> BoolConfig.fromJson(json, onChanged);
//            case "string" -> StringConfig.fromJson(json, onChanged);
//            case "list" -> EnumConfig.fromJson(json, onChanged);
//            default -> throw new IllegalArgumentException("Unknown config type: " + type);
//        };
//    }
}
