package com.fangsu.client;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityScreendoorCentralControl;
import com.fangsu.drawing.sign.SignItem;
import com.fangsu.ui.*;
import com.fangsu.ui.ticketMachine.TicketMachineMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ClientHooksImpl {

    private ClientHooksImpl() {
    }

    public static void openObjBlockConfigScreen(BaseObjBlockEntity blockEntity) {
        Minecraft.getInstance().setScreen(new ObjBlockConfigScreen(blockEntity));
    }

    public static void openSignConfigScreen(
            int faces, List<Map<String, List<SignItem>>> items, Consumer<List<Map<String, List<SignItem>>>> setter
    ) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new SignConfigUI(faces, items, setter)));
    }

    public static void openTicketMachineScreen(Component title, BlockPos pos) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new TicketMachineMainScreen(title, pos)));
    }

    public static void openPlatformSelectScreen(Component component, List<Long> defaultValue, Consumer<List<Long>> setter, BlockPos pos, int maxSelect) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new PlatformSelectionScreen(
                    component, defaultValue, setter, pos, maxSelect, Minecraft.getInstance().screen
            ));
        });
    }

    public static void openRouteSelectScreen(Component component, List<Long> defaultValue, Consumer<List<RouteSelectInfo>> setter, BlockPos pos, int maxSelect) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new RouteSelectionScreen(
                    component, defaultValue, setter, pos, maxSelect, Minecraft.getInstance().screen
            ));
        });
    }

    public static void openStationSelectScreen(Component component, List<Long> defaultValue, Consumer<List<Long>> setter, BlockPos pos, int maxSelect) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new StationSelectionScreen(
                    component, defaultValue, setter, pos, maxSelect, Minecraft.getInstance().screen
            ));
        });
    }

    public static void openScreendoorCentralControlScreen(BlockEntityScreendoorCentralControl ctrl) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new ScreendoorCentralControlScreen(ctrl));
        });
    }
}
