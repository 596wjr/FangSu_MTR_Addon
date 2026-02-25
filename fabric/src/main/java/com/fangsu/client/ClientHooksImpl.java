package com.fangsu.client;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.signItems.SignItem;
import com.fangsu.ui.ObjBlockConfigScreen;
import com.fangsu.ui.SignConfigUI;
import com.fangsu.ui.ticketMachine.TicketMachineMainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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
}
