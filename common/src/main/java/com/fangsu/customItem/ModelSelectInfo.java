package com.fangsu.customItem;

import com.fangsu.mappings.ComponentHelper;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ModelSelectInfo {
    private final String text;
    private final String content;
    private final String contentText;
    private final JsonObject defaultItem;

    public ModelSelectInfo(@Nullable String text, @Nullable String content, @Nullable String contentText, @Nullable JsonObject defaultItem) {
        this.text = text == null ? "[MISSING TEXT]" : text;
        this.content = content == null ? "Unknown" : content;
        this.contentText = contentText == null ? ComponentHelper.translatable("ui.fangsu.block.no_detail").getString() : contentText;
        this.defaultItem = defaultItem;
    }

    public ModelSelectInfo(String text, String content) {
        this(text, content, ComponentHelper.translatable("ui.fangsu.block.no_detail").getString(), null);
    }

    public ModelSelectInfo(String text, String content, String contentText) {
        this(text, content, contentText, null);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (obj instanceof ModelSelectInfo m) {
            return m.content.equals(this.content);
        }
        return false;
    }

    @Override
    public String toString() {
        return "ModelSelectInfo {\"text\":" + text + ", \"content\":" + contentText + ", \"contentText\":" + contentText + "}";
    }

    public String getText() {
        return text;
    }

    public String getContent() {
        return content;
    }

    public String getContentText() {
        return contentText;
    }

    public JsonObject getDefault() {
        return defaultItem;
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, content, contentText, defaultItem);
    }

}
