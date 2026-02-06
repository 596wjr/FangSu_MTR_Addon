package com.fangsu.customItem;

import net.minecraft.network.chat.Component;

public record ModelSelectInfo(String text, String content, String contentText) {
    public ModelSelectInfo(String text, String content) {
        this(text, content, Component.translatable("ui.fangsu.block.no_detail").getString());
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
}
