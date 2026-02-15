package com.fangsu.client;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.signItems.SignItem;
import com.fangsu.ui.ObjBlockConfigScreen;
import com.fangsu.ui.SignConfigUI;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class ClientHooksImpl {
    private ClientHooksImpl() {
    }

    public static void openObjBlockConfigScreen(BaseObjBlockEntity blockEntity) {
        Minecraft.getInstance().setScreen(new ObjBlockConfigScreen(blockEntity));
    }

    public static void openSignConfigScreen(
            Map<String, List<SignItem>> itemsFront,
            Map<String, List<SignItem>> itemsBack,
            BiConsumer<Map<String, List<SignItem>>, Map<String, List<SignItem>>> onSave
    ) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new SignConfigUI(2, List.of(itemsFront, itemsBack), list -> onSave.accept(list.get(0), list.get(1)))));
    }
}
