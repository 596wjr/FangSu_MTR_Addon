package com.fangsu.customItem;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SubModelDispInfo {
    private final Component name;
    private final List<ModelSelectInfo> infos;
    private final Function<BaseObjBlockEntity, String> initialGetter;
    private final BiConsumer<BaseObjBlockEntity, String> setter;

    public SubModelDispInfo(Component name, List<ModelSelectInfo> infos,
                            Function<BaseObjBlockEntity, String> initialGetter,
                            BiConsumer<BaseObjBlockEntity, String> setter) {
        this.name = name;
        this.infos = infos;
        this.initialGetter = initialGetter;
        this.setter = setter;
    }

    public Component name() {
        return name;
    }

    public List<ModelSelectInfo> infos() {
        return infos;
    }

    public Function<BaseObjBlockEntity, String> initialGetter() {
        return initialGetter;
    }

    public BiConsumer<BaseObjBlockEntity, String> setter() {
        return setter;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SubModelDispInfo) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.infos, that.infos) &&
                Objects.equals(this.initialGetter, that.initialGetter) &&
                Objects.equals(this.setter, that.setter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, infos, initialGetter, setter);
    }

    @Override
    public String toString() {
        return "SubModelDispInfo[" +
                "name=" + name + ", " +
                "infos=" + infos + ", " +
                "initialGetter=" + initialGetter + ", " +
                "setter=" + setter + ']';
    }

}
