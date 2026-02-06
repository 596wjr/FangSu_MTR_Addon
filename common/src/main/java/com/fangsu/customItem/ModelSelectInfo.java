package com.fangsu.customItem;

import net.minecraft.network.chat.Component;

public record ModelSelectInfo(String text, String content, String contentText) {
    public ModelSelectInfo(String text, String content) {
        this(text, content, Component.translatable("ui.fangsu.block.no_detail").getString());
    }
}
