package com.fangsu.extraConfig;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class ConfigEntry<T> {

    public final Component title;
    protected final ConfigSpec spec;

    protected final Supplier<T> getter;
    protected final Consumer<T> setter;

    protected T value;

    protected Function<T, Boolean> showCondition;
    private boolean saveOnChange = false;
    private Consumer<ConfigEntry<?>> changeListener;

    protected ConfigEntry(
            Component title,
            ConfigSpec spec,
            Supplier<T> getter,
            Consumer<T> setter
    ) {
        this.title = title;
        this.spec = spec;
        this.getter = getter;
        this.setter = setter;

        this.value = getter.get();
    }

    public ConfigEntry<T> setShowCondition(Function<T, Boolean> showCondition) {
        this.showCondition = showCondition;
        return this;
    }

    public ConfigEntry<T> setSaveOnChange(boolean saveOnChange) {
        this.saveOnChange = saveOnChange;
        return this;
    }

    public boolean isSaveOnChange() {
        return saveOnChange;
    }

    public void setChangeListener(Consumer<ConfigEntry<?>> changeListener) {
        this.changeListener = changeListener;
    }

    protected void notifyValueChanged() {
        if (changeListener != null) {
            changeListener.accept(this);
        }
    }

//    public abstract ConfigEntry<?> fromJson(JsonObject json,
//                                            Consumer<Object> onChanged);

    public void load(Object be) {
        value = getter.get();
    }

    public void save(Object be) {
        setter.accept(value);
    }

    public abstract ConfigWidget createWidget(
            int x, int y, int labelWidth, int fieldWidth
    );

    public boolean isVisible() {
        if (showCondition == null) return true;
        return showCondition.apply(value);
    }
}
