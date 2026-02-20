package com.fangsu.client;

import com.fangsu.Main;
import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.signItems.SignItem;
import dev.architectury.injectables.annotations.ExpectPlatform;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ClientHooks {
    public static Consumer<BaseObjBlockEntity> OPEN_OBJ_BLOCK_CONFIG_SCREEN
            = blockEntity -> {
        Main.LOGGER.error("打开方法没有被替换!");
    };
    public static SignScreenConsumer OPEN_OBJ_SIGN_SCREEN
            = ((faces, items, onSave) -> {
        Main.LOGGER.error("打开方法没有被替换!");
    });


    private ClientHooks() {
    }

    public static void openObjBlockConfigScreen(BaseObjBlockEntity blockEntity) {
        OPEN_OBJ_BLOCK_CONFIG_SCREEN.accept(blockEntity);
    }

    public static void openSignConfigScreen(
            int faces, List<Map<String, List<SignItem>>> items, Consumer<List<Map<String, List<SignItem>>>> setter
    ) {
        OPEN_OBJ_SIGN_SCREEN.accept(faces, items, setter);
    }

    @FunctionalInterface
    public interface SignScreenConsumer {
        void accept(int faces, List<Map<String, List<SignItem>>> items, Consumer<List<Map<String, List<SignItem>>>> setter);
    }
}
