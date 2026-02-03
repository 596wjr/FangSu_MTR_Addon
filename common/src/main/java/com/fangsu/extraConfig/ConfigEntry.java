package com.fangsu.extraConfig;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class ConfigEntry<T> {

    protected final Component title;
    protected final ConfigSpec spec;

    protected final Function<Object, T> getter;
    protected final BiConsumer<Object, T> setter;

    protected T value;

    protected Function<T, Boolean> showCondition;

    protected ConfigEntry(
            Component title,
            ConfigSpec spec,
            Function<Object, T> getter,
            BiConsumer<Object, T> setter
    ) {
        this.title = title;
        this.spec = spec;
        this.getter = getter;
        this.setter = setter;
    }

    public ConfigEntry<T> setShowCondition(Function<T, Boolean> showCondition) {
        this.showCondition = showCondition;
        return this;
    }

//    public abstract ConfigEntry<?> fromJson(JsonObject json,
//                                            Consumer<Object> onChanged);

    public void load(Object be) {
        value = getter.apply(be);
    }

    public void save(Object be) {
        setter.accept(be, value);
    }

    public abstract ConfigWidget createWidget(
            int x, int y, int labelWidth, int fieldWidth
    );

    public boolean isVisible(Object be) {
        if (showCondition == null) return true;
        return showCondition.apply(value);
    }
}
