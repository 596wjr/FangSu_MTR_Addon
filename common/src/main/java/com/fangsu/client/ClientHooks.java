package com.fangsu.client;

import com.fangsu.Main;
import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.signItems.SignItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
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
    public static TicketMachineConsumer OPEN_TICKET_MACHINE_SCREEN
            = ((title, pos) -> {
        Main.LOGGER.error("打开方法没有被替换!");
    });
    public static PlatformSelectConsumer OPEN_PLATFORM_SELECT_SCREEN
            = (component, defaultValue, setter, pos, maxSelect) -> {
        Main.LOGGER.error("打开方法没有被替换!");
    };

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

    public static void openTicketMachineScreen(Component title, BlockPos pos) {
        OPEN_TICKET_MACHINE_SCREEN.accept(title, pos);
    }

    public static void openPlatformSelectScreen(Component component, List<Long> defaultValue, Consumer<List<Long>> setter, BlockPos pos, int maxSelect) {
        OPEN_PLATFORM_SELECT_SCREEN.accept(component, defaultValue, setter, pos, maxSelect);
    }

    @FunctionalInterface
    public interface SignScreenConsumer {
        void accept(int faces, List<Map<String, List<SignItem>>> items, Consumer<List<Map<String, List<SignItem>>>> setter);
    }

    @FunctionalInterface
    public interface TicketMachineConsumer {
        void accept(Component title, BlockPos pos);
    }

    @FunctionalInterface
    public interface PlatformSelectConsumer {
        void accept(Component component, List<Long> defaultValue, Consumer<List<Long>> setter, BlockPos pos, int maxSelect);
    }
}
