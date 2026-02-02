package com.fangsu.extraConfig;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class StringConfig extends Config {
    Component title;
    Consumer<String> saveConsumer;
    String defaultValue;

    public StringConfig(Component title, Consumer<String> saveConsumer, String defaultValue) {
        this.title = title;
        this.saveConsumer = saveConsumer;
        this.defaultValue = defaultValue;
    }

    @Override
    public StringListEntry getEntry(ConfigEntryBuilder builder) {
        return builder.startStrField(title, "")
                .setDefaultValue(defaultValue)
                .setSaveConsumer(saveConsumer)
                .build();
    }
}
