package com.fangsu.extraConfig;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Deprecated()//不会写
public final class JsonConfigParser {
//
//    public static <T> ConfigEntry<T> parse(
//            JsonObject json,
//            @NotNull T defaultValue,
//            Consumer<T> onChanged
//    ) {
//        String type = json.get("type").getAsString();
//        String title = json.has("title") ? json.get("title").getAsString() : type + json.hashCode();
//        Class<T> clazz = (Class<T>) defaultValue.getClass();
//
//        if (Float.class.isAssignableFrom(clazz)) {
//            return new NumberInputConfig(Component.translatable(title), ConfigSpec.fromJson(json), () -> (Float) defaultValue, onChanged);
//        }
//    }
}
