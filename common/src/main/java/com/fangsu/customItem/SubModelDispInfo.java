package com.fangsu.customItem;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public record SubModelDispInfo(Component name, List<ModelSelectInfo> infos,
                               Function<BaseObjBlockEntity, String> initialGetter,
                               BiConsumer<BaseObjBlockEntity, String> setter) {
}
