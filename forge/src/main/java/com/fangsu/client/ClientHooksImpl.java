package com.fangsu.client;

import com.fangsu.Main;
import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.signItems.SignItem;
import com.fangsu.ui.ObjBlockConfigScreen;
import com.fangsu.ui.PlatformSelectionScreen;
import com.fangsu.ui.SignConfigUI;
import com.fangsu.ui.ticketMachine.TicketMachineMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

//@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientHooksImpl {
    private ClientHooksImpl() {
    }

//    static {
//        ClientHooks.OPEN_OBJ_BLOCK_CONFIG_SCREEN = ClientHooksImpl::openObjBlockConfigScreen;
//        ClientHooks.OPEN_OBJ_SIGN_SCREEN = ClientHooksImpl::openSignConfigScreen;
//    }

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
}
