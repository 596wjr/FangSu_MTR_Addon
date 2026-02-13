package com.fangsu.customItem;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SubModelMethodInfo extends SubModelDispInfo {
    private Runnable action;

    public SubModelMethodInfo(Component name, Runnable action) {
        super(name, null, (a) -> null, (a, b) -> {
        });
        this.action = action;
    }

    public Runnable getAction() {
        return action;
    }
}
