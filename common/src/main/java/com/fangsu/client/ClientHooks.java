package com.fangsu.client;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.signItems.SignItem;
import dev.architectury.injectables.annotations.ExpectPlatform;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class ClientHooks {
    private ClientHooks() {
    }

    @ExpectPlatform
    public static void openObjBlockConfigScreen(BaseObjBlockEntity blockEntity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void openSignConfigScreen(
            Map<String, List<SignItem>> itemsFront,
            Map<String, List<SignItem>> itemsBack,
            BiConsumer<Map<String, List<SignItem>>, Map<String, List<SignItem>>> onSave
    ) {
        throw new AssertionError();
    }
}
