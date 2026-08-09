package com.fangsu.customItem;

import com.fangsu.mappings.ComponentHelper;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * {@link ModelSelectInfo} 的基本实现，存储文本 / 内容 / 描述 / 默认值四类信息，
 * 并将其余变量逻辑（equals / hashCode / toString）统一在此实现。
 */
public class BasicModelSelectInfo implements ModelSelectInfo {
    private final String text;
    private final String content;
    private final String contentText;
    private final JsonObject defaultItem;

    public BasicModelSelectInfo(@Nullable String text, @Nullable String content, @Nullable String contentText, @Nullable JsonObject defaultItem) {
        this.text = text == null ? "[MISSING TEXT]" : text;
        this.content = content == null ? "Unknown" : content;
        this.contentText = contentText == null ? ComponentHelper.translatable("ui.fangsu.block.no_detail").getString() : contentText;
        this.defaultItem = defaultItem;
    }

    public BasicModelSelectInfo(String text, String content) {
        this(text, content, ComponentHelper.translatable("ui.fangsu.block.no_detail").getString(), null);
    }

    public BasicModelSelectInfo(String text, String content, String contentText) {
        this(text, content, contentText, null);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (obj instanceof ModelSelectInfo m) {
            return this.content.equals(m.getContent());
        }
        return false;
    }

    @Override
    public String toString() {
        return "BasicModelSelectInfo {\"text\":" + text + ", \"content\":" + content + ", \"contentText\":" + contentText + "}";
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public String getContentText() {
        return contentText;
    }

    @Override
    @Nullable
    public JsonObject getDefault() {
        return defaultItem;
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, content, contentText, defaultItem);
    }

}
